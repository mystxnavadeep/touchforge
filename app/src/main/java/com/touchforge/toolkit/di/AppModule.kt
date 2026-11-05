package com.touchforge.toolkit.di

import android.content.Context
import androidx.room.Room
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.db.MacroDao
import com.touchforge.toolkit.data.db.ProfileDao
import com.touchforge.toolkit.data.db.ToolkitDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun externalScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): ToolkitDatabase =
        Room.databaseBuilder(ctx, ToolkitDatabase::class.java, "touchforge.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun profileDao(db: ToolkitDatabase): ProfileDao = db.profileDao()

    @Provides
    fun macroDao(db: ToolkitDatabase): MacroDao = db.macroDao()

    @Provides
    @Singleton
    fun profileRepository(dao: ProfileDao, macroDao: MacroDao, @ApplicationScope scope: CoroutineScope): ProfileRepository =
        ProfileRepository(dao, macroDao, scope)
}
