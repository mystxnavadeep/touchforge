package com.touchforge.toolkit.session

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.monitor.GameDetector
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the currently applied per-game mapping. */
@Singleton
class ProfileApplier @Inject constructor() {
    private val _active = MutableStateFlow<MappingFileV1?>(null)
    val active: StateFlow<MappingFileV1?> = _active

    private val _activePackage = MutableStateFlow<String?>(null)
    val activePackage: StateFlow<String?> = _activePackage

    fun apply(pkg: String, mapping: MappingFileV1) {
        _activePackage.value = pkg
        _active.value = mapping
    }

    fun clear() {
        _activePackage.value = null
        _active.value = null
    }
}

/**
 * Foreground-app detection via public UsageStatsManager polling (with the
 * AppOps special access), debounced into open/close events, auto-applying
 * matching profiles. Shell-based process observation is a future enhancement.
 */
@Singleton
class GameMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val applier: ProfileApplier,
    private val externalScope: CoroutineScope,
) {
    sealed interface MonitorState {
        data object Stopped : MonitorState
        data class Running(val intervalMs: Long) : MonitorState
        data class NeedsPermission(val mode: String) : MonitorState
    }

    private val _state = MutableStateFlow<MonitorState>(MonitorState.Stopped)
    val state: StateFlow<MonitorState> = _state

    private val _events = MutableSharedFlow<GameDetector.Event>(extraBufferCapacity = 16)
    val events: SharedFlow<GameDetector.Event> = _events

    private val detector = GameDetector()
    private var job: Job? = null

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName,
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun start(intervalMs: Long = 2_000) {
        if (job?.isActive == true) return
        if (!hasUsageAccess()) {
            _state.value = MonitorState.NeedsPermission("usage access")
            return
        }
        _state.value = MonitorState.Running(intervalMs)
        job = externalScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            while (isActive) {
                val now = System.currentTimeMillis()
                val events = UsageEvents().also { usm.queryEvents(now - intervalMs * 2, now, it) }
                var foreground: String? = null
                val e = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(e)
                    if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        foreground = e.packageName
                    }
                }
                for (ev in detector.onObservation(foreground, now)) {
                    _events.emit(ev)
                    when (ev) {
                        is GameDetector.Event.Opened -> autoApply(ev.packageName)
                        is GameDetector.Event.Closed -> if (applier.activePackage.value == ev.packageName) applier.clear()
                    }
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = MonitorState.Stopped
    }

    private suspend fun autoApply(pkg: String) {
        val match = profiles.enabledProfiles().firstOrNull { it.first.packageName == pkg } ?: return
        applier.apply(pkg, match.second)
    }
}
