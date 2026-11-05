package com.touchforge.toolkit.core.schema

import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.SensitivityZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingSchemaTest {

    private val valid = MappingFileV1(
        schema = 1, name = "test", packageName = "com.example.game",
        sensitivityZones = listOf(SensitivityZone("z", 0f, 0f, 0.5f, 0.5f, 1.5f, 1.5f)),
    )

    @Test
    fun `round trip preserves data`() {
        val text = MappingSchema.serialize(valid)
        val loaded = MappingSchema.load(text)
        assertTrue(loaded is MappingSchema.LoadResult.Ok)
        assertEquals(valid, (loaded as MappingSchema.LoadResult.Ok).file)
    }

    @Test
    fun `rejects future schema`() {
        val text = MappingSchema.serialize(valid.copy(schema = 99))
        val r = MappingSchema.load(text)
        assertTrue(r is MappingSchema.LoadResult.Failed)
        assertTrue((r as MappingSchema.LoadResult.Failed).errors.any { it.path == "schema" })
    }

    @Test
    fun `rejects garbage json`() {
        val r = MappingSchema.load("{{{not json")
        assertTrue(r is MappingSchema.LoadResult.Failed)
    }

    @Test
    fun `rejects out of range sensitivity`() {
        val bad = valid.copy(sensitivityZones = listOf(SensitivityZone("z", 0f, 0f, 0.5f, 0.5f, 99f, 1f)))
        val errors = MappingSchema.validate(bad)
        assertTrue(errors.any { it.path.contains("sx") })
    }

    @Test
    fun `rejects degenerate bounds and bad intervals`() {
        val bad = valid.copy(
            sensitivityZones = listOf(SensitivityZone("z", 0.5f, 0.5f, 0.5f, 0.5f)),
            triggers = listOf(com.touchforge.toolkit.core.model.TriggerZone("t", 0f, 0f, 1f, 1f, tapIntervalMs = 0, tapDurationMs = 0)),
        )
        val errors = MappingSchema.validate(bad)
        assertTrue(errors.any { it.path == "sensitivityZones[0]" })
        assertTrue(errors.any { it.path.contains("tapIntervalMs") })
    }

    @Test
    fun `unknown keys are ignored for forward tolerance`() {
        val text = MappingSchema.serialize(valid).replace("}", ",\"futureField\":1}")
        val r = MappingSchema.load(text)
        assertTrue(r is MappingSchema.LoadResult.Ok)
    }
}
