package com.touchforge.toolkit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.touchforge.toolkit.core.gate.CreditState
import com.touchforge.toolkit.core.gate.Tier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "touchforge_settings")

/** All user settings, local-only by design (no cloud). */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val tier = stringPreferencesKey("tier") // FREE/PRO/ULTRA (PRO/ULTRA via purchase; FREE default)
        val credits = intPreferencesKey("credits_balance")
        val creditsLastClaim = longPreferencesKey("credits_last_claim")
        val globalSensitivity = floatPreferencesKey("global_sensitivity")
        val hudEnabled = booleanPreferencesKey("hud_enabled")
        val hudIntervalMs = intPreferencesKey("hud_interval_ms")
        val hudShowCpu = booleanPreferencesKey("hud_show_cpu")
        val hudShowRam = booleanPreferencesKey("hud_show_ram")
        val hudShowFps = booleanPreferencesKey("hud_show_fps")
        val hudShowBattery = booleanPreferencesKey("hud_show_battery")
        val hudOpacity = floatPreferencesKey("hud_opacity")
        val crosshairEnabled = booleanPreferencesKey("crosshair_enabled")
        val crosshairX = floatPreferencesKey("crosshair_x")
        val crosshairY = floatPreferencesKey("crosshair_y")
        val crosshairSize = floatPreferencesKey("crosshair_size")
        val crosshairColor = longPreferencesKey("crosshair_color")
        val crosshairOpacity = floatPreferencesKey("crosshair_opacity")
        val crosshairStyle = intPreferencesKey("crosshair_style")
        val hudPosition = intPreferencesKey("hud_position")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val pointerLocation = booleanPreferencesKey("pointer_location")
        val accidentalTouch = booleanPreferencesKey("accidental_touch")
        val showTouchesRestore = stringPreferencesKey("restore_show_touches")
        val pointerLocRestore = stringPreferencesKey("restore_pointer_location")
        val resolutionRestore = stringPreferencesKey("restore_resolution")
        val updateCheckUrl = stringPreferencesKey("update_check_url")
        val adsEnabled = booleanPreferencesKey("ads_enabled")
    }

    val tier: Flow<Tier> = context.dataStore.data.map { p ->
        runCatching { Tier.valueOf(p[K.tier] ?: Tier.FREE.name) }.getOrDefault(Tier.FREE)
    }

    val credits: Flow<CreditState> = context.dataStore.data.map { p ->
        CreditState(balance = p[K.credits] ?: CreditState.START_CREDITS, lastClaimMs = p[K.creditsLastClaim] ?: 0)
    }

    val globalSensitivity: Flow<Float> = context.dataStore.data.map { it[K.globalSensitivity] ?: 1f }

    val hud = context.dataStore.data.map { p ->
        HudSettings(
            enabled = p[K.hudEnabled] ?: false,
            intervalMs = p[K.hudIntervalMs] ?: 1000,
            showCpu = p[K.hudShowCpu] ?: true,
            showRam = p[K.hudShowRam] ?: true,
            showFps = p[K.hudShowFps] ?: true,
            showBattery = p[K.hudShowBattery] ?: true,
            opacity = p[K.hudOpacity] ?: 0.8f,
            position = p[K.hudPosition] ?: 0,
        )
    }

    val crosshair = context.dataStore.data.map { p ->
        CrosshairSettings(
            enabled = p[K.crosshairEnabled] ?: false,
            x = p[K.crosshairX] ?: 0.5f,
            y = p[K.crosshairY] ?: 0.5f,
            size = p[K.crosshairSize] ?: 0.06f,
            color = p[K.crosshairColor] ?: 0xFF00E5FF,
            opacity = p[K.crosshairOpacity] ?: 0.9f,
            style = p[K.crosshairStyle] ?: 0,
        )
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[K.onboardingDone] ?: false }
    val pointerLocationEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.pointerLocation] ?: false }
    val accidentalTouchGuard: Flow<Boolean> = context.dataStore.data.map { it[K.accidentalTouch] ?: false }
    val adsEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.adsEnabled] ?: false }
    val updateCheckUrl: Flow<String> = context.dataStore.data.map { it[K.updateCheckUrl] ?: "" }

    suspend fun setTier(t: Tier) = context.dataStore.edit { it[K.tier] = t.name }
    suspend fun setCredits(s: CreditState) = context.dataStore.edit {
        it[K.credits] = s.balance; it[K.creditsLastClaim] = s.lastClaimMs
    }
    suspend fun setGlobalSensitivity(v: Float) = context.dataStore.edit { it[K.globalSensitivity] = v }
    suspend fun setHud(s: HudSettings) = context.dataStore.edit {
        it[K.hudEnabled] = s.enabled; it[K.hudIntervalMs] = s.intervalMs
        it[K.hudShowCpu] = s.showCpu; it[K.hudShowRam] = s.showRam
        it[K.hudShowFps] = s.showFps; it[K.hudShowBattery] = s.showBattery; it[K.hudOpacity] = s.opacity
        it[K.hudPosition] = s.position
    }
    suspend fun setCrosshair(s: CrosshairSettings) = context.dataStore.edit {
        it[K.crosshairEnabled] = s.enabled; it[K.crosshairX] = s.x; it[K.crosshairY] = s.y
        it[K.crosshairSize] = s.size; it[K.crosshairColor] = s.color; it[K.crosshairOpacity] = s.opacity
        it[K.crosshairStyle] = s.style
    }
    suspend fun setOnboardingCompleted(v: Boolean) = context.dataStore.edit { it[K.onboardingDone] = v }
    suspend fun setPointerLocationEnabled(v: Boolean) = context.dataStore.edit { it[K.pointerLocation] = v }
    suspend fun setAccidentalTouchGuard(v: Boolean) = context.dataStore.edit { it[K.accidentalTouch] = v }
    suspend fun setAdsEnabled(v: Boolean) = context.dataStore.edit { it[K.adsEnabled] = v }
    suspend fun setUpdateCheckUrl(v: String) = context.dataStore.edit { it[K.updateCheckUrl] = v }

    // ---- restore points for dangerous device-wide changes ----
    suspend fun saveRestorePoint(key: String, original: String) = context.dataStore.edit {
        it[stringPreferencesKey("restore_$key")] = original
    }
    suspend fun restorePoint(key: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("restore_$key")]
    suspend fun clearRestorePoint(key: String) = context.dataStore.edit { it.remove(stringPreferencesKey("restore_$key")) }
}

data class HudSettings(
    val enabled: Boolean,
    val intervalMs: Int,
    val showCpu: Boolean,
    val showRam: Boolean,
    val showFps: Boolean,
    val showBattery: Boolean,
    val opacity: Float,
    val position: Int = 0,
)

data class CrosshairSettings(
    val enabled: Boolean,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Long,
    val opacity: Float,
    val style: Int = 0,
)
