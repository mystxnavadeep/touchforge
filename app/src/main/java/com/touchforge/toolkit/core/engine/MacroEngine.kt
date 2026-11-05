package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.MacroDef
import com.touchforge.toolkit.core.model.MacroStep
import com.touchforge.toolkit.core.model.MacroStepType

/** Records normalized touch events with timestamps into a [MacroDef]. */
class MacroRecorder {
    private val steps = ArrayList<MacroStep>()
    private var t0 = -1L

    fun begin(nowMs: Long) {
        steps.clear()
        t0 = nowMs
    }

    fun add(type: MacroStepType, x: Float, y: Float, nowMs: Long) {
        if (t0 < 0) return
        val delay = (nowMs - t0).coerceAtLeast(0)
        t0 = nowMs
        if (steps.isNotEmpty() && steps.last().type == MacroStepType.SLEEP) {
            steps[steps.size - 1] = steps.last().copy(delayMs = steps.last().delayMs + delay)
        } else if (delay > 0 && steps.isNotEmpty()) {
            steps += MacroStep(MacroStepType.SLEEP, delayMs = delay)
        }
        steps += MacroStep(type, x, y)
    }

    fun finish(name: String, id: String, loop: Boolean = false): MacroDef =
        MacroDef(id = id, name = name, steps = steps.toList(), loop = loop)
}

/**
 * Plays a macro back as a timeline of actions relative to startMs.
 * Pure logic: the caller schedules execution.
 */
class MacroPlayer(private val macro: MacroDef) {
    private data class Timed(val atMs: Long, val step: MacroStep)

    private val timeline: List<Timed> = buildTimeline(macro)

    fun actionsBetween(fromMs: Long, toMs: Long): List<MacroStep> =
        timeline.filter { it.atMs in fromMs until toMs }.map { it.step }

    val durationMs: Long = timeline.maxOfOrNull { it.atMs } ?: 0L

    companion object {
        fun buildTimeline(macro: MacroDef): List<Timed> {
            val out = ArrayList<Timed>()
            var t = 0L
            val passes = if (macro.loop) 1 else 1 // loops handled by caller restarting the player
            repeat(passes) {
                for (s in macro.steps) {
                    t += s.delayMs
                    out += Timed(t, s)
                }
            }
            return out
        }
    }
}
