package com.touchforge.toolkit.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.capability.Availability
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.core.gate.TierGate
import com.touchforge.toolkit.core.shell.CommandPolicy
import com.touchforge.toolkit.data.CrosshairSettings
import com.touchforge.toolkit.data.HudSettings
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.service.OverlayHostService
import com.touchforge.toolkit.session.ProfileApplier
import com.touchforge.toolkit.session.SessionManager
import com.touchforge.toolkit.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val session: SessionManager,
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    val applier: ProfileApplier,
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state
    val features = session.features()
    val tier: StateFlow<Tier> = settings.tier.stateIn(viewModelScope, SharingStarted.Eagerly, Tier.FREE)
    val sensitivity: StateFlow<Float> = settings.globalSensitivity.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val hud: StateFlow<HudSettings?> = settings.hud.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val crosshair: StateFlow<CrosshairSettings?> = settings.crosshair.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pointerLocation: StateFlow<Boolean> = settings.pointerLocationEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val accidentalTouch: StateFlow<Boolean> = settings.accidentalTouchGuard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val recentProfiles = profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activePackage = applier.activePackage
    val activeProfile = applier.active

    fun setSensitivity(v: Float) = viewModelScope.launch {
        val decision = TierGate.sensitivity(tier.value, v)
        settings.setGlobalSensitivity(when (decision) {
            is TierGate.SensitivityDecision.Allowed -> decision.value
            is TierGate.SensitivityDecision.Clamped -> decision.value
        })
    }

    fun applyShowTouches(on: Boolean) = viewModelScope.launch {
        session.applySystemSetting("show_touches", if (on) "1" else "0")
    }

    fun applyPointerLocation(on: Boolean) = viewModelScope.launch {
        settings.setPointerLocationEnabled(on)
        session.applySystemSetting("pointer_location", if (on) "1" else "0")
    }

    fun setAccidentalTouchGuard(on: Boolean) = viewModelScope.launch {
        settings.setAccidentalTouchGuard(on)
    }

    fun toggleHud(current: HudSettings?, ctx: Context) = viewModelScope.launch {
        val next = (current ?: HudSettings(true, 1000, true, true, true, true, 0.8f, 0)).let { it.copy(enabled = !it.enabled) }
        settings.setHud(next)
        syncOverlayService(ctx, next.enabled || (crosshair.value?.enabled ?: false))
    }

    fun toggleCrosshair(current: CrosshairSettings?, ctx: Context) = viewModelScope.launch {
        val next = (current ?: CrosshairSettings(true, 0.5f, 0.5f, 0.06f, 0xFF00E5FF, 0.9f, 0)).let { it.copy(enabled = !it.enabled) }
        settings.setCrosshair(next)
        syncOverlayService(ctx, next.enabled || (hud.value?.enabled ?: false))
    }

    private fun syncOverlayService(ctx: Context, anyOverlay: Boolean) {
        if (anyOverlay && Settings.canDrawOverlays(ctx)) OverlayHostService.start(ctx)
        else if (!anyOverlay) OverlayHostService.stop(ctx)
    }

    fun memoryTrim(onResult: (String) -> Unit) = viewModelScope.launch {
        val res = session.exec("am send-trim-memory --all complete") ?: "Trim command executed"
        onResult(res)
    }

    fun applyResolution(w: Int, h: Int) = viewModelScope.launch {
        session.startResolution(w, h, 0)
    }

    fun restoreAll() = viewModelScope.launch { session.restoreAll() }

    fun stopEverything(ctx: Context) {
        session.disconnect()
        OverlayHostService.stop(ctx)
    }
}

@Composable
fun HomeScreen(
    onOpenConnect: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenAimCurve: (String) -> Unit,
    onOpenTriggers: (String) -> Unit,
    onOpenKeyMap: (String) -> Unit,
    onOpenMacros: () -> Unit,
    onOpenCrosshair: () -> Unit,
    onOpenHud: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val tier by vm.tier.collectAsState()
    val sens by vm.sensitivity.collectAsState()
    val hud by vm.hud.collectAsState()
    val cross by vm.crosshair.collectAsState()
    val pointerLoc by vm.pointerLocation.collectAsState()
    val accidentalTouch by vm.accidentalTouch.collectAsState()
    val recentProfiles by vm.recentProfiles.collectAsState()
    val activePkg by vm.activePackage.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()

    var showResDialog by remember { mutableStateOf(false) }
    var trimStatus by remember { mutableStateOf<String?>(null) }

    val defaultTargetPkg = recentProfiles.firstOrNull()?.packageName ?: "com.default.game"

    Column(
        Modifier
            .fillMaxSize()
            .background(CyberColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status Hero Header Card
        CyberCard(
            title = when (val s = state) {
                is SessionState.Connected -> "System Connected (UID ${s.shellUid})"
                SessionState.Connecting -> "Establishing Connection..."
                SessionState.Idle -> "Service Standby"
                is SessionState.Failed -> "Connection Failed"
            },
            subtitle = if (activePkg != null) "Active Game: $activePkg" else "TouchForge Engine Active",
            icon = Icons.Default.Speed,
            badgeText = when (state) {
                is SessionState.Connected -> "ONLINE"
                SessionState.Connecting -> "CONNECTING"
                SessionState.Idle -> "STANDBY"
                is SessionState.Failed -> "ERROR"
            },
            badgeColor = when (state) {
                is SessionState.Connected -> CyberColors.Green
                SessionState.Connecting -> CyberColors.Cyan
                SessionState.Idle -> CyberColors.Amber
                is SessionState.Failed -> CyberColors.Red
            },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeonButton(
                    text = if (state is SessionState.Connected) "Diagnostics" else stringResource(R.string.open_connect),
                    onClick = onOpenConnect,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Build,
                )
                CyberOutlinedButton(
                    text = stringResource(R.string.stop_all),
                    onClick = { vm.stopEverything(ctx) },
                    modifier = Modifier.weight(1f),
                    color = CyberColors.Amber,
                )
            }
        }

        // Global Sensitivity Card with Tier Cap & Quick Presets
        CyberCard(
            title = stringResource(R.string.global_sensitivity),
            subtitle = "Tier Limit: %.1fx · Mode: %s".format(TierGate.limits(tier).maxSensitivity, tier.name),
            badgeText = "%.2fx".format(sens),
            badgeColor = CyberColors.Cyan,
        ) {
            CyberSlider(
                label = "Sensitivity Multiplier",
                value = sens.coerceIn(0.1f, 5f),
                valueRange = 0.1f..TierGate.limits(tier).maxSensitivity,
                onValueChange = { vm.setSensitivity(it) },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { presetVal ->
                    if (presetVal <= TierGate.limits(tier).maxSensitivity) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { vm.setSensitivity(presetVal) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (sens == presetVal) CyberColors.Cyan.copy(alpha = 0.2f) else CyberColors.SurfaceVariant,
                            border = BorderStroke(1.dp, if (sens == presetVal) CyberColors.Cyan else CyberColors.Border),
                        ) {
                            Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text("%.1fx".format(presetVal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sens == presetVal) CyberColors.Cyan else CyberColors.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Quick Toggles Card
        CyberCard(title = stringResource(R.string.quick_toggles)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CyberSwitchRow(
                    label = stringResource(R.string.show_touches),
                    checked = false,
                    onCheckedChange = { vm.applyShowTouches(it) },
                    subtitle = "System pointer dots via Settings.System / shell",
                )
                CyberSwitchRow(
                    label = stringResource(R.string.crosshair),
                    checked = cross?.enabled ?: false,
                    onCheckedChange = {
                        if (!Settings.canDrawOverlays(ctx)) {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        } else {
                            vm.toggleCrosshair(cross, ctx)
                        }
                    },
                    subtitle = "Floating reticle overlay (Tap Crosshair Studio to customize)",
                )
                CyberSwitchRow(
                    label = stringResource(R.string.hud),
                    checked = hud?.enabled ?: false,
                    onCheckedChange = {
                        if (!Settings.canDrawOverlays(ctx)) {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        } else {
                            vm.toggleHud(hud, ctx)
                        }
                    },
                    subtitle = "Real-time CPU, RAM, FPS & battery floating indicator",
                )
                CyberSwitchRow(
                    label = stringResource(R.string.tweak_accidental_touch),
                    checked = accidentalTouch,
                    onCheckedChange = { vm.setAccidentalTouchGuard(it) },
                    subtitle = "Edge palm suppression filter",
                )
            }
        }

        // Toolkit Hub — 8-tile Quick Navigation Matrix (Nebula Touch inspired)
        CyberCard(
            title = stringResource(R.string.quick_tools),
            subtitle = "Dedicated touch configuration and esports studios",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolGridTile(
                        title = stringResource(R.string.tool_zones),
                        desc = "Touch Zones Canvas",
                        icon = Icons.Default.TouchApp,
                        iconTint = CyberColors.Cyan,
                        onClick = { onOpenEditor(defaultTargetPkg) },
                        modifier = Modifier.weight(1f),
                    )
                    ToolGridTile(
                        title = stringResource(R.string.tool_aim_curve),
                        desc = "Bezier Response Curve",
                        icon = Icons.Default.Timeline,
                        iconTint = CyberColors.Teal,
                        onClick = { onOpenAimCurve(defaultTargetPkg) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolGridTile(
                        title = stringResource(R.string.tool_triggers),
                        desc = "Rapid Auto-Tap",
                        icon = Icons.Default.Bolt,
                        iconTint = CyberColors.Amber,
                        onClick = { onOpenTriggers(defaultTargetPkg) },
                        modifier = Modifier.weight(1f),
                    )
                    ToolGridTile(
                        title = stringResource(R.string.tool_keymap),
                        desc = "Hardware Keys",
                        icon = Icons.Default.Keyboard,
                        iconTint = CyberColors.Violet,
                        onClick = { onOpenKeyMap(defaultTargetPkg) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolGridTile(
                        title = stringResource(R.string.tool_macros),
                        desc = "Combo Recorder",
                        icon = Icons.Default.Refresh,
                        iconTint = CyberColors.Red,
                        onClick = onOpenMacros,
                        modifier = Modifier.weight(1f),
                    )
                    ToolGridTile(
                        title = stringResource(R.string.tool_crosshair),
                        desc = "Reticle Studio",
                        icon = Icons.Default.FilterCenterFocus,
                        iconTint = CyberColors.Cyan,
                        onClick = onOpenCrosshair,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolGridTile(
                        title = stringResource(R.string.tool_hud),
                        desc = "Performance HUD",
                        icon = Icons.Default.Speed,
                        iconTint = CyberColors.Green,
                        onClick = onOpenHud,
                        modifier = Modifier.weight(1f),
                    )
                    ToolGridTile(
                        title = stringResource(R.string.tool_cleaner),
                        desc = "RAM Boost",
                        icon = Icons.Default.CleaningServices,
                        iconTint = CyberColors.Amber,
                        onClick = {
                            vm.memoryTrim { res ->
                                trimStatus = res
                                Toast.makeText(ctx, "Memory Trimmed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Recent Games Card
        CyberCard(
            title = stringResource(R.string.recent_games),
            badgeText = "GAME LIBRARY",
            badgeColor = CyberColors.Cyan,
            onClick = onOpenGames,
        ) {
            if (recentProfiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_games),
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentProfiles.take(3).forEach { p ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberColors.SurfaceVariant,
                            border = BorderStroke(1.dp, CyberColors.Border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.displayName, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                                    Text(p.packageName, fontSize = 11.sp, color = CyberColors.TextSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CyberOutlinedButton(
                                        text = "Edit",
                                        onClick = { onOpenEditor(p.packageName) },
                                        modifier = Modifier.height(36.dp),
                                    )
                                    NeonButton(
                                        text = "Launch",
                                        onClick = {
                                            val i = ctx.packageManager.getLaunchIntentForPackage(p.packageName)
                                            if (i != null) ctx.startActivity(i)
                                            else Toast.makeText(ctx, "App not found", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.height(36.dp),
                                        icon = Icons.Default.PlayArrow,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device Capabilities Matrix Card
        CyberCard(title = stringResource(R.string.capabilities)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.features.forEach { (feat, status) ->
                    val color = when (status.availability) {
                        Availability.AVAILABLE -> CyberColors.Green
                        Availability.FALLBACK -> CyberColors.Amber
                        Availability.UNAVAILABLE -> CyberColors.Red
                    }
                    val badge = when (status.availability) {
                        Availability.AVAILABLE -> "AVAILABLE"
                        Availability.FALLBACK -> "FALLBACK"
                        Availability.UNAVAILABLE -> "UNSUPPORTED"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberColors.SurfaceVariant,
                        border = BorderStroke(1.dp, CyberColors.Border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(feat.name, fontWeight = FontWeight.SemiBold, color = CyberColors.TextPrimary, fontSize = 13.sp)
                                Text(status.mode.ifBlank { status.reason }, fontSize = 11.sp, color = CyberColors.TextSecondary)
                            }
                            StatusBadge(text = badge, color = color)
                        }
                    }
                }
            }
        }

        // Bottom Actions: Resolution Scale & Restore
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton(
                text = stringResource(R.string.resolution_scale),
                onClick = { showResDialog = true },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
            )
            CyberOutlinedButton(
                text = stringResource(R.string.restore_changes),
                onClick = { vm.restoreAll() },
                modifier = Modifier.weight(1f),
                color = CyberColors.Amber,
            )
        }
    }

    if (showResDialog) {
        ResolutionDialog(vm = vm, onDismiss = { showResDialog = false })
    }
}

@Composable
private fun ToolGridTile(
    title: String,
    desc: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CyberColors.SurfaceVariant,
        border = BorderStroke(1.dp, CyberColors.Border),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary, fontSize = 13.sp)
            Text(desc, fontSize = 10.sp, color = CyberColors.TextSecondary)
        }
    }
}

@Composable
private fun ResolutionDialog(vm: HomeViewModel, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(0.85f) }
    var current by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        current = vm.session.exec("wm size")?.let { CommandPolicy.parseWmSize(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resolution_scale)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.resolution_current, current?.first ?: 0, current?.second ?: 0))
                Text(stringResource(R.string.resolution_warning), style = MaterialTheme.typography.bodySmall, color = CyberColors.TextSecondary)
                CyberSlider(
                    label = "Scale: ${(scale * 100).toInt()}%",
                    value = scale,
                    valueRange = 0.5f..1f,
                    onValueChange = { scale = it },
                )
                val (w, h) = current ?: (0 to 0)
                Text(stringResource(R.string.resolution_target, (w * scale).toInt(), (h * scale).toInt()), fontWeight = FontWeight.Bold, color = CyberColors.Cyan)
            }
        },
        confirmButton = {
            NeonButton(
                text = stringResource(R.string.apply),
                onClick = {
                    val (w, h) = current ?: return@NeonButton
                    vm.applyResolution((w * scale).toInt(), (h * scale).toInt())
                    onDismiss()
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = CyberColors.Surface,
        titleContentColor = CyberColors.TextPrimary,
        textContentColor = CyberColors.TextPrimary,
    )
}
