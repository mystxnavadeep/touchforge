package com.touchforge.toolkit.data

import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.MacroDef
import com.touchforge.toolkit.core.schema.MappingSchema
import com.touchforge.toolkit.data.db.MacroDao
import com.touchforge.toolkit.data.db.MacroEntity
import com.touchforge.toolkit.data.db.ProfileDao
import com.touchforge.toolkit.data.db.ProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Local-first store for game profiles and macros. No cloud. */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val macroDao: MacroDao,
    private val externalScope: CoroutineScope,
) {
    fun observeProfiles(): Flow<List<ProfileEntity>> = profileDao.observeAll()
    fun observeMacros(): Flow<List<MacroEntity>> = macroDao.observeAll()

    /** First stored profile's package (for export fallback), or null when empty. */
    suspend fun firstProfilePackage(): String? =
        observeProfiles().first().firstOrNull()?.packageName

    sealed class SaveResult {
        data object Ok : SaveResult()
        data class Invalid(val errors: List<MappingSchema.Error>) : SaveResult()
    }

    suspend fun saveProfile(pkg: String, displayName: String, mapping: MappingFileV1): SaveResult {
        val errors = MappingSchema.validate(mapping)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val now = System.currentTimeMillis()
        val existing = profileDao.get(pkg)
        profileDao.upsert(
            ProfileEntity(
                packageName = pkg,
                displayName = displayName,
                enabled = existing?.enabled ?: true,
                mappingJson = MappingSchema.serialize(mapping.copy(packageName = pkg)),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return SaveResult.Ok
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) {
        val p = profileDao.get(pkg) ?: return
        profileDao.upsert(p.copy(enabled = enabled))
    }

    suspend fun get(pkg: String): Pair<ProfileEntity, MappingFileV1>? {
        val e = profileDao.get(pkg) ?: return null
        val m = when (val r = MappingSchema.load(e.mappingJson)) {
            is MappingSchema.LoadResult.Ok -> r.file
            is MappingSchema.LoadResult.Failed -> MappingFileV1()
        }
        return e to m
    }

    suspend fun enabledProfiles(): List<Pair<ProfileEntity, MappingFileV1>> =
        profileDao.getEnabled().mapNotNull { e ->
            when (val r = MappingSchema.load(e.mappingJson)) {
                is MappingSchema.LoadResult.Ok -> e to r.file
                else -> null
            }
        }

    suspend fun delete(pkg: String) = profileDao.delete(pkg)

    suspend fun importMapping(text: String, pkg: String, displayName: String): SaveResult =
        when (val r = MappingSchema.load(text)) {
            is MappingSchema.LoadResult.Ok -> saveProfile(pkg, displayName, r.file.copy(packageName = pkg))
            is MappingSchema.LoadResult.Failed -> SaveResult.Invalid(r.errors)
        }

    suspend fun exportProfile(pkg: String): String? = get(pkg)?.let { MappingSchema.serialize(it.second) }

    fun saveMacro(macro: MacroDef) {
        externalScope.launch {
            macroDao.upsert(MacroEntity(macro.id, macro.name, json.encodeToString(macro), System.currentTimeMillis()))
        }
    }

    suspend fun deleteMacro(id: String) = macroDao.delete(id)
}
