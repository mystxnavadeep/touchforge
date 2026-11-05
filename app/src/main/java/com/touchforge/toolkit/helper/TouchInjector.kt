package com.touchforge.toolkit.helper

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.touchforge.toolkit.aidl.TouchSpec

/**
 * Injects synthetic touches through the framework InputManager using reflection.
 * Requires the caller process to hold INJECT_EVENTS (shell uid under Shizuku).
 * Purely reflective on hidden-but-stable framework API; degrades to unavailable.
 */
internal object TouchInjector {

    private var injectMethod: java.lang.reflect.Method? = null
    private var instance: Any? = null

    @Synchronized
    fun available(): Boolean = prepare()

    private fun prepare(): Boolean {
        if (injectMethod != null) return true
        return runCatching {
            val im = Class.forName("android.hardware.input.InputManager")
            instance = im.getMethod("getInstance").invoke(null)
            injectMethod = instance?.javaClass?.getMethod(
                "injectInputEvent", MotionEvent::class.java, Int::class.javaPrimitiveType,
            )
            injectMethod != null
        }.getOrDefault(false)
    }

    @Synchronized
    fun inject(spec: TouchSpec): Boolean {
        if (!prepare()) return false
        val now = SystemClock.uptimeMillis()
        val downTime = if (spec.downTimeMs > 0) spec.downTimeMs else now
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = spec.pointerId.coerceIn(0, 9)
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = spec.x; y = spec.y
            pressure = 1f; size = 1f
        })
        val event = MotionEvent.obtain(
            downTime, spec.eventTimeMs.takeIf { it > 0 } ?: now, spec.action, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        return try {
            // INJECT_INPUT_EVENT_MODE_ASYNC == 0
            injectMethod!!.invoke(instance, event, 0) as Boolean
        } catch (t: Throwable) {
            false
        } finally {
            event.recycle()
        }
    }
}
