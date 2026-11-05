package com.touchforge.toolkit.core.capability

/**
 * Capability detection → feature availability with graceful fallback.
 * Pure logic; the Android layer feeds a CapabilityReport from live probes.
 */
data class CapabilityReport(
    val hasShellSession: Boolean = false,
    val canExec: Boolean = false,
    val canInject: Boolean = false,
    val canGrabRawInput: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasWriteSettings: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,
    val apiLevel: Int = 0,
)

enum class Availability { AVAILABLE, FALLBACK, UNAVAILABLE }

data class FeatureStatus(val availability: Availability, val mode: String, val reason: String)

enum class Feature {
    GLOBAL_SENSITIVITY,
    ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH,
    ZONE_SENSITIVITY_ON_INJECTED_TOUCH,
    TRIGGERS_AUTOTAP,
    MACROS,
    KEY_MAPPING,
    CROSSHAIR,
    PERFORMANCE_HUD,
    GAME_AUTO_APPLY,
    SHOW_TOUCHES,
    ORIENTATION_LOCK,
    RESOLUTION_SCALE,
    MEMORY_TRIM,
    APP_STOP,
}

object CapabilityPolicy {

    fun evaluate(c: CapabilityReport): Map<Feature, FeatureStatus> {
        val m = HashMap<Feature, FeatureStatus>()

        fun avail(f: Feature, mode: String) = m.put(f, FeatureStatus(Availability.AVAILABLE, mode, ""))
        fun fallback(f: Feature, mode: String, reason: String) = m.put(f, FeatureStatus(Availability.FALLBACK, mode, reason))
        fun off(f: Feature, mode: String, reason: String) = m.put(f, FeatureStatus(Availability.UNAVAILABLE, mode, reason))

        val shell = c.hasShellSession && c.canExec

        if (shell) {
            avail(Feature.GLOBAL_SENSITIVITY, "engine")
            avail(Feature.SHOW_TOUCHES, "shell: settings put system show_touches")
            avail(Feature.MEMORY_TRIM, "shell: am send-trim-memory")
            avail(Feature.APP_STOP, "shell: am force-stop")
        } else {
            off(Feature.GLOBAL_SENSITIVITY, "off", "no privileged session")
            off(Feature.SHOW_TOUCHES, "off", "requires shell session")
            off(Feature.MEMORY_TRIM, "off", "requires shell session")
            off(Feature.APP_STOP, "off", "requires shell session")
        }

        // Physical-touch sensitivity needs raw grab + injection (OEM dependent).
        if (shell && c.canInject && c.canGrabRawInput) {
            avail(Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH, "native grab + reinject")
        } else if (shell && c.canInject) {
            fallback(
                Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH, "injection-only",
                "raw touchscreen grab unavailable on this OEM; sensitivity applies to injected touches only",
            )
        } else {
            off(Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH, "off", "no injection capability")
        }

        if (shell && c.canInject) {
            avail(Feature.ZONE_SENSITIVITY_ON_INJECTED_TOUCH, "engine")
            avail(Feature.TRIGGERS_AUTOTAP, "injected taps")
            if (m[Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH]?.availability == Availability.FALLBACK)
                fallback(Feature.TRIGGERS_AUTOTAP, "injected taps", "injection-only mode")
            avail(Feature.MACROS, if (c.canGrabRawInput) "record+playback" else "playback (injected)")
            avail(Feature.KEY_MAPPING, "overlay buttons + injected taps")
        } else {
            off(Feature.TRIGGERS_AUTOTAP, "off", "no injection capability")
            off(Feature.MACROS, "off", "no injection capability")
            off(Feature.KEY_MAPPING, "off", "no injection capability")
        }

        if (c.hasOverlayPermission) {
            avail(Feature.CROSSHAIR, "TYPE_APPLICATION_OVERLAY")
            avail(Feature.PERFORMANCE_HUD, "TYPE_APPLICATION_OVERLAY")
        } else {
            off(Feature.CROSSHAIR, "off", "overlay permission missing")
            off(Feature.PERFORMANCE_HUD, "off", "overlay permission missing")
        }

        when {
            c.hasShellSession && c.hasUsageStatsAccess ->
                avail(Feature.GAME_AUTO_APPLY, "shell process events + usage stats")
            c.hasUsageStatsAccess ->
                fallback(Feature.GAME_AUTO_APPLY, "usage stats polling", "no shell session; slower detection")
            else ->
                off(Feature.GAME_AUTO_APPLY, "manual", "grant Usage Access to enable auto-apply")
        }

        if (c.hasWriteSettings) avail(Feature.ORIENTATION_LOCK, "Settings.System USER_ROTATION")
        else off(Feature.ORIENTATION_LOCK, "off", "Write Settings permission missing")

        if (shell && c.apiLevel >= 28) avail(Feature.RESOLUTION_SCALE, "wm size/density")
        else if (shell) fallback(Feature.RESOLUTION_SCALE, "wm size only", "wm density unreliable before API 28")
        else off(Feature.RESOLUTION_SCALE, "off", "requires shell session")

        return m
    }

    /** True when any active feature could strand the user if the session dies. */
    fun needsWatchdog(features: Collection<Feature>): Boolean =
        features.any { it in WATCHDOG_FEATURES }

    val WATCHDOG_FEATURES = setOf(
        Feature.RESOLUTION_SCALE, Feature.SHOW_TOUCHES,
        Feature.ZONE_SENSITIVITY_ON_PHYSICAL_TOUCH, Feature.ZONE_SENSITIVITY_ON_INJECTED_TOUCH,
    )
}
