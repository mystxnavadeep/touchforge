package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.MacroStep
import com.touchforge.toolkit.core.model.MacroStepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEngineTest {

    @Test
    fun `recorder collapses gaps into sleep steps`() {
        val r = MacroRecorder()
        r.begin(1000)
        r.add(MacroStepType.DOWN, 0.1f, 0.1f, 1000)
        r.add(MacroStepType.UP, 0.1f, 0.1f, 1080) // 80ms gap
        r.add(MacroStepType.DOWN, 0.2f, 0.2f, 1140) // 60ms gap
        val macro = r.finish("m", "id1")
        assertEquals(
            listOf(MacroStepType.DOWN, MacroStepType.UP, MacroStepType.SLEEP, MacroStepType.DOWN),
            macro.steps.map { it.type },
        )
        assertEquals(60L, macro.steps[2].delayMs)
    }

    @Test
    fun `player timeline accumulates delays`() {
        val macro = com.touchforge.toolkit.core.model.MacroDef(
            id = "x", name = "x",
            steps = listOf(
                MacroStep(MacroStepType.DOWN, 0f, 0f, 0),
                MacroStep(MacroStepType.SLEEP, delayMs = 50),
                MacroStep(MacroStepType.UP),
            ),
        )
        val p = MacroPlayer(macro)
        assertEquals(3, p.actionsBetween(0, 1000).size)
        assertTrue(p.actionsBetween(51, 1000).isEmpty())
        assertEquals(50L, p.durationMs)
    }
}
