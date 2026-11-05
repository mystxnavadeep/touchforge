package com.touchforge.toolkit.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_profiles")
data class ProfileEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    /** Serialized MappingFileV1 JSON (our own schema). */
    val mappingJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Serialized MacroDef JSON. */
    val macroJson: String,
    val createdAt: Long,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM game_profiles ORDER BY displayName")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM game_profiles WHERE packageName = :pkg")
    suspend fun get(pkg: String): ProfileEntity?

    @Query("SELECT * FROM game_profiles WHERE enabled = 1")
    suspend fun getEnabled(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM game_profiles WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY name")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun get(id: String): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ProfileEntity::class, MacroEntity::class], version = 1, exportSchema = false)
abstract class ToolkitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun macroDao(): MacroDao
}
