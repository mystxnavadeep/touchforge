package com.touchforge.toolkit.core.schema

import com.touchforge.toolkit.core.model.MappingFileV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Our own mapping file schema. Versioned, strictly validated, normalized coords.
 * JSON shape is original; nothing here mirrors any other product's format.
 */
object MappingSchema {
    const val SUPPORTED_SCHEMA = 1
    const val FILE_MIME = "application/json"
    const val FILE_EXTENSION = "tfmap"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    data class Error(val path: String, val message: String)

    sealed class LoadResult {
        data class Ok(val file: MappingFileV1) : LoadResult()
        data class Failed(val errors: List<Error>) : LoadResult()
    }

    fun serialize(file: MappingFileV1): String = json.encodeToString(file)

    fun load(text: String): LoadResult {
        val file = try {
            json.decodeFromString<MappingFileV1>(text)
        } catch (e: SerializationException) {
            return LoadResult.Failed(listOf(Error("$", "Not a valid TouchForge mapping: ${e.message ?: "parse error"}")))
        } catch (e: IllegalArgumentException) {
            return LoadResult.Failed(listOf(Error("$", "Not a valid TouchForge mapping: ${e.message ?: "parse error"}")))
        }
        val errors = validate(file)
        return if (errors.isEmpty()) LoadResult.Ok(file) else LoadResult.Failed(errors)
    }

    fun validate(f: MappingFileV1): List<Error> {
        val e = ArrayList<Error>()
        if (f.schema > SUPPORTED_SCHEMA)
            e += Error("schema", "Mapping schema ${f.schema} is newer than supported $SUPPORTED_SCHEMA")
        if (f.schema < 1) e += Error("schema", "schema must be >= 1")
        fun coord(path: String, v: Float) {
            if (v.isNaN() || v.isInfinite() || v < -0.01f || v > 1.01f)
                e += Error(path, "coordinate out of range [0,1]: $v")
        }
        fun sens(path: String, v: Float) {
            if (v.isNaN() || v.isInfinite() || v < 0.1f || v > 5f)
                e += Error(path, "sensitivity out of range [0.1,5]: $v")
        }
        f.sensitivityZones.forEachIndexed { i, z ->
            coord("sensitivityZones[$i].left", z.left); coord("sensitivityZones[$i].top", z.top)
            coord("sensitivityZones[$i].right", z.right); coord("sensitivityZones[$i].bottom", z.bottom)
            sens("sensitivityZones[$i].sx", z.sensitivityX); sens("sensitivityZones[$i].sy", z.sensitivityY)
            if (z.right <= z.left || z.bottom <= z.top) e += Error("sensitivityZones[$i]", "degenerate bounds")
        }
        f.targets.forEachIndexed { i, t ->
            coord("targets[$i].centerX", t.centerX); coord("targets[$i].centerY", t.centerY)
            if (t.radius.isNaN() || t.radius <= 0f || t.radius > 0.5f) e += Error("targets[$i].radius", "radius must be in (0,0.5]")
            sens("targets[$i].sx", t.sensitivityX); sens("targets[$i].sy", t.sensitivityY)
        }
        f.triggers.forEachIndexed { i, t ->
            coord("triggers[$i].left", t.left); coord("triggers[$i].top", t.top)
            coord("triggers[$i].right", t.right); coord("triggers[$i].bottom", t.bottom)
            if (t.tapIntervalMs < 1 || t.tapIntervalMs > 60_000) e += Error("triggers[$i].tapIntervalMs", "interval must be 1..60000 ms")
            if (t.tapDurationMs < 1 || t.tapDurationMs > t.tapIntervalMs) e += Error("triggers[$i].tapDurationMs", "duration must be 1..interval")
            if (t.repeatCount < 0) e += Error("triggers[$i].repeatCount", "repeatCount must be >= 0")
        }
        f.keyMaps.forEachIndexed { i, k ->
            coord("keyMaps[$i].x", k.x); coord("keyMaps[$i].y", k.y)
            if (k.keyCode !in 0..65535) e += Error("keyMaps[$i].keyCode", "keyCode out of range")
            if (k.opacity !in 0.05f..1f) e += Error("keyMaps[$i].opacity", "opacity must be 0.05..1")
        }
        f.macros.forEachIndexed { i, m ->
            if (m.name.isBlank()) e += Error("macros[$i].name", "name required")
            if (m.steps.size > 10_000) e += Error("macros[$i].steps", "too many steps (max 10000)")
            m.steps.forEachIndexed { j, s ->
                if (s.type != com.touchforge.toolkit.core.model.MacroStepType.SLEEP) {
                    coord("macros[$i].steps[$j].x", s.x); coord("macros[$i].steps[$j].y", s.y)
                }
                if (s.delayMs < 0 || s.delayMs > 600_000) e += Error("macros[$i].steps[$j].delayMs", "delay must be 0..600000")
            }
        }
        return e
    }
}
