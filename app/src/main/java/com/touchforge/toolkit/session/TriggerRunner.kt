package com.touchforge.toolkit.session

import android.content.Context
import android.view.MotionEvent
import com.touchforge.toolkit.aidl.TouchSpec
import com.touchforge.toolkit.core.engine.TriggerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pumps the [TriggerEngine] and executes scheduled taps through the helper's
 * injector. Works in injection-only mode too (taps are ours, not the game's).
 */
@Singleton
class TriggerRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
    private val applier: ProfileApplier,
    private val externalScope: CoroutineScope,
) {
    private val engine = TriggerEngine()
    private var job: Job? = null

    fun onPointerDown(id: Int, nx: Float, ny: Float) {
        engine.onDown(id, nx, ny, applier.active.value?.triggers ?: emptyList())
    }

    fun onPointerUp(id: Int) = engine.onUp(id)

    fun start() {
        if (job?.isActive == true) return
        job = externalScope.launch {
            val dm = context.resources.displayMetrics
            while (isActive) {
                val taps = engine.due()
                if (taps.isNotEmpty()) {
                    val now = android.os.SystemClock.uptimeMillis()
                    for (tap in taps) {
                        session.inject(
                            TouchSpec(
                                x = tap.x * dm.widthPixels,
                                y = tap.y * dm.heightPixels,
                                action = if (tap.down) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_UP,
                                pointerId = 0,
                                downTimeMs = if (tap.down) tap.atMs else now,
                                eventTimeMs = now,
                            ),
                        )
                    }
                }
                delay(10)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        engine.clear()
    }
}
