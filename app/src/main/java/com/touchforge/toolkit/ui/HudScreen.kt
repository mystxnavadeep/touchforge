package com.touchforge.toolkit.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.HudSettings
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.service.OverlayHostService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HudViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val hud: StateFlow<HudSettings?> =
        settings.hud.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateHud(next: HudSettings, ctx: Context) = viewModelScope.launch {
        settings.setHud(next)
        if (next.enabled && Settings.canDrawOverlays(ctx)) {
            OverlayHostService.start(ctx)
        }
    }

    fun toggleEnabled(ctx: Context) = viewModelScope.launch {
        val current = hud.value ?: HudSettings(
            enabled = true, intervalMs = 1000, showCpu = true, showRam = true, showFps = true, showBattery = true, opacity = 0.8f, position = 0,
        )
        val next = current.copy(enabled = !current.enabled)
        updateHud(next, ctx)
    }

    fun setCpu(enabled: Boolean, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(showCpu = enabled), ctx)
    }

    fun setRam(enabled: Boolean, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(showRam = enabled), ctx)
    }

    fun setFps(enabled: Boolean, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(showFps = enabled), ctx)
    }

    fun setBattery(enabled: Boolean, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(showBattery = enabled), ctx)
    }

    fun setInterval(ms: Int, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(intervalMs = ms), ctx)
    }

    fun setOpacity(v: Float, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(opacity = v), ctx)
    }

    fun setPosition(pos: Int, ctx: Context) {
        val current = hud.value ?: return
        updateHud(current.copy(position = pos), ctx)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudScreen(
    onBack: () -> Unit,
    vm: HudViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val cfg = vm.hud.collectAsState().value ?: HudSettings(
        enabled = false, intervalMs = 1000, showCpu = true, showRam = true, showFps = true, showBattery = true, opacity = 0.8f, position = 0,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.hud_studio_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (cfg.enabled) "HUD Overlay Active" else "HUD Standby", fontSize = 12.sp, color = if (cfg.enabled) CyberColors.Green else CyberColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberColors.Cyan)
                    }
                },
                actions = {
                    CyberSwitchRow(
                        label = "",
                        checked = cfg.enabled,
                        onCheckedChange = { vm.toggleEnabled(ctx) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberColors.Background,
                    titleContentColor = CyberColors.TextPrimary,
                ),
            )
        },
        containerColor = CyberColors.Background,
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live Preview of the Floating HUD Bubble
            CyberCard(
                title = "HUD Bubble Live Preview",
                subtitle = "Appearance of the in-game overlay bubble",
                badgeText = if (cfg.enabled) "ACTIVE" else "OFF",
                badgeColor = if (cfg.enabled) CyberColors.Green else CyberColors.Amber,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070B12))
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = when (cfg.position) {
                        1 -> Alignment.TopEnd
                        2 -> Alignment.BottomStart
                        else -> Alignment.TopStart
                    },
                ) {
                    // Floating HUD preview widget
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = cfg.opacity),
                        border = BorderStroke(1.dp, CyberColors.BorderGlow),
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (cfg.showCpu) {
                                Text("CPU 34%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Cyan)
                            }
                            if (cfg.showRam) {
                                Text("RAM 68%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Teal)
                            }
                            if (cfg.showFps) {
                                Text("FPS~ 60 (est.)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Green)
                            }
                            if (cfg.showBattery) {
                                Text("BAT 85%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Amber)
                            }
                        }
                    }
                }
            }

            // Metric Toggles
            CyberCard(title = "Display Metrics") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CyberSwitchRow(
                        label = stringResource(R.string.hud_show_cpu),
                        checked = cfg.showCpu,
                        onCheckedChange = { vm.setCpu(it, ctx) },
                        accentColor = CyberColors.Cyan,
                    )
                    CyberSwitchRow(
                        label = stringResource(R.string.hud_show_ram),
                        checked = cfg.showRam,
                        onCheckedChange = { vm.setRam(it, ctx) },
                        accentColor = CyberColors.Teal,
                    )
                    CyberSwitchRow(
                        label = stringResource(R.string.hud_show_fps),
                        checked = cfg.showFps,
                        onCheckedChange = { vm.setFps(it, ctx) },
                        subtitle = "Choreographer-based frame timing estimate",
                        accentColor = CyberColors.Green,
                    )
                    CyberSwitchRow(
                        label = stringResource(R.string.hud_show_battery),
                        checked = cfg.showBattery,
                        onCheckedChange = { vm.setBattery(it, ctx) },
                        accentColor = CyberColors.Amber,
                    )
                }
            }

            // Position Selection
            CyberCard(title = "Overlay Default Anchor") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val posList = listOf(
                        "Top-Left" to 0,
                        "Top-Right" to 1,
                        "Bottom-Left" to 2,
                    )
                    posList.forEach { (name, posIdx) ->
                        val isSel = cfg.position == posIdx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { vm.setPosition(posIdx, ctx) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) CyberColors.Cyan.copy(alpha = 0.15f) else CyberColors.SurfaceVariant,
                            border = BorderStroke(1.dp, if (isSel) CyberColors.Cyan else CyberColors.Border),
                        ) {
                            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(name, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) CyberColors.Cyan else CyberColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            // Tuning Sliders
            CyberCard(title = "Refresh & Transparency") {
                CyberSlider(
                    label = stringResource(R.string.hud_interval),
                    value = cfg.intervalMs.toFloat(),
                    valueRange = 250f..3000f,
                    displayValue = "${cfg.intervalMs} ms",
                    onValueChange = { vm.setInterval(it.toInt(), ctx) },
                )
                Spacer(Modifier.height(8.dp))
                CyberSlider(
                    label = stringResource(R.string.hud_opacity),
                    value = cfg.opacity,
                    valueRange = 0.2f..1f,
                    onValueChange = { vm.setOpacity(it, ctx) },
                )
            }
        }
    }
}
