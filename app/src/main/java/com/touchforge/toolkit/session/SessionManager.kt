package com.touchforge.toolkit.session

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.touchforge.toolkit.aidl.IToolkitService
import com.touchforge.toolkit.aidl.TouchSpec
import com.touchforge.toolkit.core.capability.CapabilityPolicy
import com.touchforge.toolkit.core.capability.CapabilityReport
import com.touchforge.toolkit.di.ApplicationScope
import com.touchforge.toolkit.helper.ToolkitUserService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Idle : SessionState
    data object Connecting : SessionState
    data class Connected(val protocol: Int, val shellUid: Int, val caps: CapabilityReport) : SessionState
    data class Failed(val reason: String, val restored: Boolean) : SessionState
}

/**
 * Client side of the privileged helper over Shizuku. Owns connection state,
 * capability probing, heartbeat, and the emergency-restore path.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state

    @Volatile var service: IToolkitService? = null
        private set

    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    init {
        runCatching {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST) {
                    if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) connect()
                    else _state.value = SessionState.Failed("Shizuku permission denied", restored = false)
                }
            }
        }
    }

    fun isShizukuInstalled(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isShizukuAuthorized(): Boolean =
        runCatching { Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    fun requestPermission() {
        if (isShizukuAuthorized()) connect()
        else runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
            .onFailure { _state.value = SessionState.Failed("Shizuku not reachable: ${it.message}", false) }
    }

    fun connect() {
        if (!isShizukuInstalled()) {
            _state.value = SessionState.Failed("Shizuku app not installed or not running", false)
            return
        }
        if (!isShizukuAuthorized()) {
            requestPermission()
            return
        }
        _state.value = SessionState.Connecting
        val args = Shizuku.UserServiceArgs(ComponentName(context, ToolkitUserService::class.java))
            .processNameSuffix("helper")
            .version(ToolkitUserService.VERSION)
            .daemon(false)
        userServiceArgs = args
        runCatching { Shizuku.bindUserService(args, connection) }
            .onFailure { _state.value = SessionState.Failed("bind failed: ${it.message}", false) }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = binder?.let { IToolkitService.Stub.asInterface(it) }
            if (svc == null) {
                _state.value = SessionState.Failed("helper binder null (version mismatch?)", false)
                return
            }
            service = svc
            externalScope.launch { probe(svc) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Helper died: restore device state immediately, then surface failure.
            emergencyRestore()
            service = null
            _state.value = SessionState.Failed("helper connection lost; guarded changes restored", true)
        }
    }

    private suspend fun probe(svc: IToolkitService) {
        val uid = runCatching { svc.shellUid }.getOrDefault(-1)
        val protocol = runCatching { svc.protocolVersion }.getOrDefault(-1)
        val canExec = runCatching {
            svc.isCommandAllowed("cat /proc/stat") && svc.execCommand("cat /proc/stat").isNotBlank()
        }.getOrDefault(false)
        val canInject = runCatching { svc.canInjectProbe() }.getOrDefault(false)
        val canGrab = runCatching { svc.canGrabProbe() }.getOrDefault(false)
        val caps = CapabilityReport(
            hasShellSession = true,
            canExec = canExec,
            canInject = canInject,
            canGrabRawInput = canGrab,
            hasOverlayPermission = Settings.canDrawOverlays(context),
            hasWriteSettings = Settings.System.canWrite(context),
            hasUsageStatsAccess = hasUsageStatsAccess(),
            apiLevel = Build.VERSION.SDK_INT,
        )
        _state.value = SessionState.Connected(protocol, uid, caps)
        heartbeat(svc)
    }

    private suspend fun heartbeat(svc: IToolkitService) {
        while (externalScope.isActive && service === svc) {
            delay(5_000)
            val ok = runCatching { svc.ping() }.isSuccess
            if (!ok) {
                emergencyRestore()
                if (service === svc) {
                    service = null
                    _state.value = SessionState.Failed("heartbeat lost; guarded changes restored", true)
                }
                return
            }
        }
    }

    fun disconnect() {
        emergencyRestore()
        runCatching { service?.shutdown() }
        runCatching {
            userServiceArgs?.let { Shizuku.unbindUserService(it, connection, true) }
        }
        service = null
        _state.value = SessionState.Idle
    }

    /** Restore every guarded change; safe to call repeatedly. */
    fun emergencyRestore() {
        runCatching { service?.restoreGuardedSettings() }
    }

    // ---- typed ops (all best-effort, return false on any failure) ----

    suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        val s = service ?: return@withContext null
        if (!com.touchforge.toolkit.core.shell.CommandPolicy.isAllowed(command)) return@withContext null
        runCatching { s.execCommand(command) }.getOrNull()
    }

    suspend fun applySystemSetting(key: String, value: String): Boolean =
        runCatching { service?.applySystemSetting(key, value) }.getOrNull() == true

    suspend fun startResolution(w: Int, h: Int, dpi: Int): Boolean =
        runCatching { service?.startResolutionScale(w, h, dpi) }.getOrNull() == true

    suspend fun restoreAll(): Boolean =
        runCatching { service?.restoreGuardedSettings() }.getOrNull() == true

    suspend fun startGrab(path: String = ""): Boolean =
        runCatching { service?.startGrab(path) }.getOrNull() == true

    fun stopGrab() {
        runCatching { service?.stopGrab() }
    }

    suspend fun inject(spec: TouchSpec): Boolean =
        runCatching { service?.injectTouch(spec) }.getOrNull() == true

    fun capabilities(): CapabilityReport =
        (_state.value as? SessionState.Connected)?.caps ?: CapabilityReport(apiLevel = Build.VERSION.SDK_INT)

    fun features() = CapabilityPolicy.evaluate(capabilities())

    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), context.packageName,
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private companion object {
        const val PERMISSION_REQUEST = 4101
    }
}
