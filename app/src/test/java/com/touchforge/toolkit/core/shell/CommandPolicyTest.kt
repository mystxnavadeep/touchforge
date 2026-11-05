package com.touchforge.toolkit.core.shell

import com.touchforge.toolkit.core.capability.CapabilityPolicy
import com.touchforge.toolkit.core.capability.CapabilityReport
import com.touchforge.toolkit.core.capability.Feature
import com.touchforge.toolkit.core.monitor.CpuStatsParser
import com.touchforge.toolkit.core.monitor.FpsEstimator
import com.touchforge.toolkit.core.monitor.GameDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPolicyTest {

    @Test
    fun `whitelisted commands pass`() {
        assertTrue(CommandPolicy.isAllowed("settings put system show_touches 1"))
        assertTrue(CommandPolicy.isAllowed("settings get secure default_input_method"))
        assertTrue(CommandPolicy.isAllowed("wm size 1080x2400"))
        assertTrue(CommandPolicy.isAllowed("wm density 480"))
        assertTrue(CommandPolicy.isAllowed("am send-trim-memory com.x complete"))
        assertTrue(CommandPolicy.isAllowed("am force-stop com.x"))
        assertTrue(CommandPolicy.isAllowed("cat /proc/stat"))
    }

    @Test
    fun `metacharacters and unknown commands rejected`() {
        assertFalse(CommandPolicy.isAllowed("settings put system show_touches 1; rm -rf /"))
        assertFalse(CommandPolicy.isAllowed("wm size \$(boom)"))
        assertFalse(CommandPolicy.isAllowed("rm -rf /"))
        assertFalse(CommandPolicy.isAllowed("pm grant com.x foo"))
        assertFalse(CommandPolicy.isAllowed(""))
        assertFalse(CommandPolicy.isAllowed("settings put global adb_enabled 0"))
    }

    @Test
    fun `wm size and density parse`() {
        val out = "Physical size: 1080x2400\nOverride size: 900x2000\nPhysical density: 440\nOverride density: 420"
        assertEquals(1080 to 2400, CommandPolicy.parseWmSize(out))
        assertEquals(440, CommandPolicy.parseWmDensity(out))
        assertNull(CommandPolicy.parseWmSize("error: no device"))
    }
}

class CapabilityPolicyTest {

    @Test
    fun `full capability matrix`() {
        val m = CapabilityPolicy.evaluate(
            CapabilityReport(hasShellSession = true, canExec = true, canInject = true, canGrabRawInput = true,
                hasOverlayPermission = true, hasWriteSettings = true, hasUsageStatsAccess = true, apiLevel = 34),
        )
        assertEquals(com.touchforge.toolkit.core.capability.Availability.AVAILABLE, m[Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH]!!.availability)
        assertEquals(com.touchforge.toolkit.core.capability.Availability.AVAILABLE, m[Feature.RESOLUTION_SCALE]!!.availability)
        assertTrue(CapabilityPolicy.needsWatchdog(m.filterValues { it.availability != com.touchforge.toolkit.core.capability.Availability.UNAVAILABLE }.keys))
    }

    @Test
    fun `no session disables shell features`() {
        val m = CapabilityPolicy.evaluate(CapabilityReport())
        assertEquals(com.touchforge.toolkit.core.capability.Availability.UNAVAILABLE, m[Feature.GLOBAL_SENSITIVITY]!!.availability)
        assertEquals(com.touchforge.toolkit.core.capability.Availability.UNAVAILABLE, m[Feature.CROSSHAIR]!!.availability)
        assertFalse(CapabilityPolicy.needsWatchdog(m.filterValues { it.availability != com.touchforge.toolkit.core.capability.Availability.UNAVAILABLE }.keys))
    }

    @Test
    fun `injection without grab is a fallback, not available`() {
        val m = CapabilityPolicy.evaluate(
            CapabilityReport(hasShellSession = true, canExec = true, canInject = true, canGrabRawInput = false),
        )
        assertEquals(com.touchforge.toolkit.core.capability.Availability.FALLBACK, m[Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH]!!.availability)
        assertEquals(com.touchforge.toolkit.core.capability.Availability.AVAILABLE, m[Feature.TRIGGERS_AUTOTAP]!!.availability)
    }
}

class MonitorsTest {

    @Test
    fun `cpu busy fraction between snapshots`() {
        val a = CpuStatsParser.parse("cpu  100 0 100 700 0 0 0 0 0 0")!!
        val b = CpuStatsParser.parse("cpu  200 0 200 900 0 0 0 0 0 0")!!
        val f = CpuStatsParser.busyFraction(a, b)
        assertEquals(0.5f, f!!, 1e-4f)
        assertNull(CpuStatsParser.parse("garbage line"))
    }

    @Test
    fun `fps estimator converges to 60`() {
        val e = FpsEstimator()
        var t = 0L
        repeat(200) {
            t += 16_666_667L
            e.onFrame(t)
        }
        assertEquals(60f, e.fps()!!, 2f)
    }

    @Test
    fun `game detector debounces and emits open-close`() {
        val d = GameDetector(debounceMs = 1000)
        assertTrue(d.onObservation("com.game", 0).isEmpty())
        assertTrue(d.onObservation("com.launcher", 100).isEmpty()) // pending
        val ev1 = d.onObservation("com.launcher", 1200)
        assertTrue(ev1.any { it is GameDetector.Event.Opened && it.packageName == "com.launcher" })
        assertTrue(d.onObservation("com.launcher", 2000).isEmpty())
    }
}
