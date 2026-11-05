package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.TriggerRepeat
import com.touchforge.toolkit.core.model.TriggerZone

/**
 * Pure state machine that converts a held touch inside a trigger zone into a
 * schedule of synthetic taps (interval/duration). The caller executes the
 * returned actions through the privileged injector.
 */
class TriggerEngine(private val clock: () -> Long = System::currentTimeMillis) {

    data class Tap(val x: Float, val y: Float, val down: Boolean, val atMs: Long)

    private data class Active(val zone: TriggerZone, val x: Float, val y: Float, val startMs: Long, var fired: Int, var phase: Boolean)

    private val active = HashMap<Int, Active>() // pointer id -> active trigger

    fun onDown(pointerId: Int, nx: Float, ny: Float, zones: List<TriggerZone>) {
        val z = zones.lastOrNull { it.enabled && nx >= it.left && nx <= it.right && ny >= it.top && ny <= it.bottom }
            ?: return
        active[pointerId] = Active(z, (z.left + z.right) / 2f, (z.top + z.bottom) / 2f, clock(), 0, false)
    }

    fun onUp(pointerId: Int) {
        active.remove(pointerId)
    }

    fun clear() = active.clear()

    /** Actions that should be executed now (called from a periodic pump, ~1ms..interval). */
    fun due(nowMs: Long = clock()): List<Tap> {
        val out = ArrayList<Tap>()
        val finished = ArrayList<Int>()
        for ((pid, a) in active) {
            val maxFires = when (a.zone.repeat) {
                TriggerRepeat.FIXED_COUNT -> a.zone.repeatCount
                TriggerRepeat.WHILE_HELD -> Int.MAX_VALUE
            }
            if (a.fired >= maxFires) {
                finished += pid
                continue
            }
            val elapsed = nowMs - a.startMs
            val period = a.zone.tapIntervalMs.coerceAtLeast(1)
            val expected = (elapsed / period).toInt()
            val target = minOf(expected, maxFires)
            while (a.fired < target) {
                a.fired++
                val downAt = a.startMs + a.fired * period
                out += Tap(a.x, a.y, down = true, atMs = downAt)
                out += Tap(a.x, a.y, down = false, atMs = downAt + a.zone.tapDurationMs.coerceIn(1, period))
            }
            if (a.fired >= maxFires) {
                finished += pid
            }
        }
        finished.forEach { active.remove(it) }
        return out.sortedBy { it.atMs }
    }
}
