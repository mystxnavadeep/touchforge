package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.TriggerRepeat
import com.touchforge.toolkit.core.model.TriggerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerEngineTest {

    private var now = 10_000L
    private val clock = { now }
    private val zone = TriggerZone(
        id = "tr1", left = 0f, top = 0f, right = 0.2f, bottom = 0.2f,
        tapIntervalMs = 100, tapDurationMs = 40, repeat = TriggerRepeat.WHILE_HELD,
    )

    @Test
    fun `held touch in zone fires periodic down-up pairs`() {
        val e = TriggerEngine(clock)
        e.onDown(1, 0.1f, 0.1f, listOf(zone))
        now += 350
        val taps = e.due()
        // periods completed at t=100,200,300 -> 3 taps = 6 actions
        assertEquals(6, taps.size)
        assertEquals(true, taps[0].down)
        assertEquals(false, taps[1].down)
        assertTrue(taps[1].atMs - taps[0].atMs == 40L)
    }

    @Test
    fun `up stops firing`() {
        val e = TriggerEngine(clock)
        e.onDown(1, 0.1f, 0.1f, listOf(zone))
        now += 250
        e.due()
        e.onUp(1)
        now += 500
        assertTrue(e.due().isEmpty())
    }

    @Test
    fun `fixed count stops after N taps`() {
        val z = zone.copy(repeat = TriggerRepeat.FIXED_COUNT, repeatCount = 2)
        val e = TriggerEngine(clock)
        e.onDown(1, 0.1f, 0.1f, listOf(z))
        now += 100_000
        val downs = e.due().count { it.down }
        assertEquals(2, downs)
        assertTrue(e.due().isEmpty())
    }

    @Test
    fun `touch outside zones never fires`() {
        val e = TriggerEngine(clock)
        e.onDown(1, 0.9f, 0.9f, listOf(zone))
        now += 1000
        assertTrue(e.due().isEmpty())
    }
}
