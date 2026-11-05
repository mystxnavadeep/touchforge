package com.touchforge.toolkit.core.gate

/**
 * TouchForge's own freemium gate. Original tier structure and limits;
 * deliberately NOT a copy of any other product's tiers or numbers.
 */
enum class Tier { FREE, PRO, ULTRA }

data class Limits(
    val maxSensitivityZones: Int,
    val maxTargets: Int,
    val maxTriggers: Int,
    val maxSensitivity: Float,
    val minTapIntervalMs: Long,
    val macrosEnabled: Boolean,
    val hudMinIntervalMs: Long,
    val resolutionScaling: Boolean,
)

object TierGate {
    fun limits(tier: Tier): Limits = when (tier) {
        Tier.FREE -> Limits(maxSensitivityZones = 2, maxTargets = 2, maxTriggers = 1, maxSensitivity = 1.5f,
            minTapIntervalMs = 50, macrosEnabled = false, hudMinIntervalMs = 1000, resolutionScaling = false)
        Tier.PRO -> Limits(maxSensitivityZones = 8, maxTargets = 8, maxTriggers = 4, maxSensitivity = 3f,
            minTapIntervalMs = 10, macrosEnabled = true, hudMinIntervalMs = 500, resolutionScaling = true)
        Tier.ULTRA -> Limits(maxSensitivityZones = 32, maxTargets = 32, maxTriggers = 16, maxSensitivity = 5f,
            minTapIntervalMs = 1, macrosEnabled = true, hudMinIntervalMs = 250, resolutionScaling = true)
    }

    sealed class SensitivityDecision {
        data class Allowed(val value: Float) : SensitivityDecision()
        data class Clamped(val value: Float, val limit: Float) : SensitivityDecision()
    }

    fun sensitivity(tier: Tier, requested: Float): SensitivityDecision {
        val max = limits(tier).maxSensitivity
        val v = requested.coerceAtLeast(0.1f)
        return if (v <= max) SensitivityDecision.Allowed(v) else SensitivityDecision.Clamped(max, max)
    }

    sealed class CountDecision {
        object Allowed : CountDecision()
        data class Blocked(val limit: Int, val required: Tier) : CountDecision()
    }

    fun canAddZone(tier: Tier, current: Int): CountDecision = checkCount(current, limits(tier).maxSensitivityZones)
    fun canAddTarget(tier: Tier, current: Int): CountDecision = checkCount(current, limits(tier).maxTargets)
    fun canAddTrigger(tier: Tier, current: Int): CountDecision = checkCount(current, limits(tier).maxTriggers)

    private fun checkCount(current: Int, limit: Int): CountDecision =
        if (current < limit) CountDecision.Allowed
        else CountDecision.Blocked(limit, if (limit == 2) Tier.PRO else if (limit == 8) Tier.PRO else Tier.ULTRA)

    sealed class IntervalDecision {
        data class Allowed(val ms: Long) : IntervalDecision()
        data class Clamped(val ms: Long, val limit: Long) : IntervalDecision()
    }

    fun tapInterval(tier: Tier, requestedMs: Long): IntervalDecision {
        val min = limits(tier).minTapIntervalMs
        val v = requestedMs.coerceAtLeast(1)
        return if (v >= min) IntervalDecision.Allowed(v) else IntervalDecision.Clamped(min, min)
    }
}
