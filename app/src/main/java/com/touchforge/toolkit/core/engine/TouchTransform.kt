package com.touchforge.toolkit.core.engine

import com.touchforge.toolkit.core.model.AimCurve
import com.touchforge.toolkit.core.model.SensitivityZone
import com.touchforge.toolkit.core.model.TargetZone
import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure transform engine.
 *
 * Semantics (our own design, not derived from any other product):
 * A point inside a target or zone is displaced away from (or pulled toward) the
 * anchor by the X/Y sensitivity factors. Targets take precedence over zones.
 * When a curve is enabled, the displacement magnitude is additionally shaped by
 * a monotone cubic Bezier so small offsets stay precise while large offsets scale up.
 */
object TouchTransform {

    data class Result(val x: Float, val y: Float, val zoneId: String?)

    fun contains(z: SensitivityZone, nx: Float, ny: Float): Boolean =
        z.enabled && nx >= z.left && nx <= z.right && ny >= z.top && ny <= z.bottom

    fun contains(t: TargetZone, nx: Float, ny: Float): Boolean {
        if (!t.enabled) return false
        val dx = nx - t.centerX
        val dy = ny - t.centerY
        return dx * dx + dy * dy <= t.radius * t.radius
    }

    /** Returns the anchor + sensitivity to use for a normalized point, or null. */
    fun resolve(
        nx: Float,
        ny: Float,
        zones: List<SensitivityZone>,
        targets: List<TargetZone>,
    ): Triple<Float, Float, Pair<Float, Float>>? {
        for (t in targets.asReversed()) {
            if (contains(t, nx, ny)) return Triple(t.centerX, t.centerY, t.sensitivityX to t.sensitivityY)
        }
        for (z in zones.asReversed()) {
            if (contains(z, nx, ny)) {
                val cx = (z.left + z.right) / 2f
                val cy = (z.top + z.bottom) / 2f
                return Triple(cx, cy, z.sensitivityX to z.sensitivityY)
            }
        }
        return null
    }

    fun apply(
        px: Float,
        py: Float,
        screenW: Float,
        screenH: Float,
        zones: List<SensitivityZone>,
        targets: List<TargetZone>,
        curve: AimCurve = AimCurve(),
    ): Result {
        val nx = if (screenW > 0) px / screenW else 0f
        val ny = if (screenH > 0) py / screenH else 0f
        val hit = resolve(nx, ny, zones, targets) ?: return Result(px, py, null)
        val (ax, ay, s) = hit
        val shaped = if (curve.enabled) {
            val mag = (((px / screenW - ax) * s.first).pow(2) + ((py / screenH - ay) * s.second).pow(2)).pow(0.5f)
            AimCurveMath.shape(mag, curve) / (mag.coerceAtLeast(1e-6f))
        } else 1f
        val outX = (ax + (nx - ax) * s.first * shaped) * screenW
        val outY = (ay + (ny - ay) * s.second * shaped) * screenH
        val zoneId = targets.lastOrNull { contains(it, nx, ny) }?.id
            ?: zones.lastOrNull { contains(it, nx, ny) }?.id
        return Result(outX, outY, zoneId)
    }

    /** Clamp helper used by editors and the gate. */
    fun clampSensitivity(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)
}

/** Monotone cubic Bezier mapping t∈[0,1] -> [0,1], evaluated by bisection on x(s)=t. */
object AimCurveMath {
    fun shape(t: Float, c: AimCurve): Float {
        val x = t.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        var s = x
        for (i in 0 until 24) {
            s = (lo + hi) / 2f
            val bx = bezier(s, c.x1, c.x2)
            if (abs(bx - x) < 1e-5f) break
            if (bx < x) lo = s else hi = s
        }
        return bezier(s, c.y1, c.y2)
    }

    private fun bezier(s: Float, p1: Float, p2: Float): Float {
        val u = 1f - s
        return 3f * u * u * s * p1 + 3f * u * s * s * p2 + s * s * s
    }
}
