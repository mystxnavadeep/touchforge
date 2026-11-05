package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.AimCurve
import com.touchforge.toolkit.core.model.SensitivityZone
import com.touchforge.toolkit.core.model.TargetZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TouchTransformTest {

    private val zone = SensitivityZone(id = "z1", left = 0f, top = 0f, right = 0.5f, bottom = 1f, sensitivityX = 2f, sensitivityY = 2f)
    private val target = TargetZone(id = "t1", centerX = 0.8f, centerY = 0.8f, radius = 0.1f, sensitivityX = 3f, sensitivityY = 3f)

    @Test
    fun `point outside zones is untouched`() {
        val r = TouchTransform.apply(900f, 100f, 1000f, 2000f, listOf(zone), emptyList())
        assertEquals(900f, r.x, 1e-3f)
        assertEquals(100f, r.y, 1e-3f)
        assertNull(r.zoneId)
    }

    @Test
    fun `point in zone scales away from zone center`() {
        // center of zone = (0.25, 0.5); point at (0.25,0.75) -> dy=0.25*2 => 0.25+0.5=... y center 0.5 + (0.75-0.5)*2 = 1.0
        val r = TouchTransform.apply(250f, 1500f, 1000f, 2000f, listOf(zone), emptyList())
        assertEquals(250f, r.x, 1e-2f)
        assertEquals(2000f, r.y, 1e-2f)
        assertEquals("z1", r.zoneId)
    }

    @Test
    fun `zone center maps to itself`() {
        val r = TouchTransform.apply(250f, 1000f, 1000f, 2000f, listOf(zone), emptyList())
        assertEquals(250f, r.x, 1e-2f)
        assertEquals(1000f, r.y, 1e-2f)
    }

    @Test
    fun `target takes precedence over zone`() {
        val overlapping = SensitivityZone(id = "z2", left = 0f, top = 0f, right = 1f, bottom = 1f, sensitivityX = 1.2f, sensitivityY = 1.2f)
        val r = TouchTransform.apply(800f, 800f, 1000f, 1000f, listOf(overlapping), listOf(target))
        assertEquals("t1", r.zoneId)
        // anchor (0.8,0.8) == point -> unchanged
        assertEquals(800f, r.x, 1e-2f)
    }

    @Test
    fun `disabled zones ignored`() {
        val r = TouchTransform.apply(100f, 100f, 1000f, 2000f, listOf(zone.copy(enabled = false)), emptyList())
        assertEquals(100f, r.x, 1e-3f)
        assertNull(r.zoneId)
    }

    @Test
    fun `curve endpoints are fixed and interior is monotone`() {
        val c = AimCurve(enabled = true)
        assertEquals(0f, AimCurveMath.shape(0f, c), 1e-4f)
        assertEquals(1f, AimCurveMath.shape(1f, c), 1e-4f)
        var prev = 0f
        var t = 0.05f
        while (t < 1f) {
            val v = AimCurveMath.shape(t, c)
            assertTrue("curve not monotone at t=$t", v >= prev - 1e-5f)
            prev = v
            t += 0.05f
        }
    }

    @Test
    fun `curve compresses low end with our default control points`() {
        val c = AimCurve(enabled = true, x1 = 0.25f, y1 = 0.10f, x2 = 0.75f, y2 = 0.90f)
        assertTrue(AimCurveMath.shape(0.5f, c) < 0.5f)
        assertTrue(abs(AimCurveMath.shape(0.25f, c) - 0.25f) < 0.2f)
    }

    @Test
    fun `disabled curve is identity factor`() {
        val r = TouchTransform.apply(250f, 1500f, 1000f, 2000f, listOf(zone), emptyList(), AimCurve(enabled = false))
        assertEquals(2000f, r.y, 1e-2f)
    }
}
