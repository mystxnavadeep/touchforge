package com.touchforge.toolkit.core.model

import kotlinx.serialization.Serializable

/**
 * All persisted geometry uses normalized coordinates (0f..1f of screen width/height)
 * so profiles are resolution-independent. Conversion to pixels happens in the engine.
 */

@Serializable
data class SensitivityZone(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sensitivityX: Float = 1f,
    val sensitivityY: Float = 1f,
    val enabled: Boolean = true,
)

@Serializable
data class TargetZone(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val sensitivityX: Float = 1f,
    val sensitivityY: Float = 1f,
    val enabled: Boolean = true,
)

enum class TriggerRepeat { WHILE_HELD, FIXED_COUNT }

@Serializable
data class TriggerZone(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val tapIntervalMs: Long = 100,
    val tapDurationMs: Long = 50,
    val repeat: TriggerRepeat = TriggerRepeat.WHILE_HELD,
    val repeatCount: Int = 0,
    val enabled: Boolean = true,
)

/** A keyboard key remapped to a screen point (injected when the key fires). */
@Serializable
data class KeyMapDef(
    val id: String,
    val keyCode: Int,
    val x: Float,
    val y: Float,
    val label: String = "",
    val size: Float = 0.08f,
    val opacity: Float = 0.6f,
    val colorArgb: Long = 0xFFFFFFFF,
)

@Serializable
enum class MacroStepType { DOWN, UP, MOVE, SLEEP }

@Serializable
data class MacroStep(
    val type: MacroStepType,
    val x: Float = 0f,
    val y: Float = 0f,
    val delayMs: Long = 0,
)

@Serializable
data class MacroDef(
    val id: String,
    val name: String,
    val steps: List<MacroStep> = emptyList(),
    val loop: Boolean = false,
)

/** Full per-game profile; serialized into the Room row as MappingFileV1 JSON. */
@Serializable
data class MappingFileV1(
    val schema: Int = 1,
    val name: String = "",
    val packageName: String = "",
    val createdAt: Long = 0,
    val sensitivityZones: List<SensitivityZone> = emptyList(),
    val targets: List<TargetZone> = emptyList(),
    val triggers: List<TriggerZone> = emptyList(),
    val keyMaps: List<KeyMapDef> = emptyList(),
    val macros: List<MacroDef> = emptyList(),
    val aimCurve: AimCurve = AimCurve(),
)

/**
 * Original "aim curve": a monotone piecewise-cubic response curve applied to the
 * offset magnitude from the active zone center. identity() disables it.
 */
@Serializable
data class AimCurve(
    val enabled: Boolean = false,
    val x1: Float = 0.25f,
    val y1: Float = 0.10f,
    val x2: Float = 0.75f,
    val y2: Float = 0.90f,
)
