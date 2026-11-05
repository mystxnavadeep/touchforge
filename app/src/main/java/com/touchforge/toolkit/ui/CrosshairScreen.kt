package com.touchforge.toolkit.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FilterCenterFocus
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.CrosshairSettings
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.service.OverlayHostService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrosshairViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    val crosshair: StateFlow<CrosshairSettings?> =
        settings.crosshair.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateCrosshair(next: CrosshairSettings, ctx: Context) = viewModelScope.launch {
        settings.setCrosshair(next)
        if (next.enabled && Settings.canDrawOverlays(ctx)) {
            OverlayHostService.start(ctx)
        }
    }

    fun toggleEnabled(ctx: Context) = viewModelScope.launch {
        val current = crosshair.value ?: CrosshairSettings(
            enabled = true, x = 0.5f, y = 0.5f, size = 0.06f, color = 0xFF00E5FF, opacity = 0.9f, style = 0,
        )
        val next = current.copy(enabled = !current.enabled)
        updateCrosshair(next, ctx)
    }

    fun setStyle(style: Int, ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(style = style), ctx)
    }

    fun setSize(size: Float, ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(size = size), ctx)
    }

    fun setColor(color: Long, ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(color = color), ctx)
    }

    fun setOpacity(opacity: Float, ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(opacity = opacity), ctx)
    }

    fun nudge(dx: Float, dy: Float, ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(
            x = (current.x + dx).coerceIn(0.1f, 0.9f),
            y = (current.y + dy).coerceIn(0.1f, 0.9f),
        ), ctx)
    }

    fun recenter(ctx: Context) {
        val current = crosshair.value ?: return
        updateCrosshair(current.copy(x = 0.5f, y = 0.5f), ctx)
    }
}

private val colorPalette = listOf(
    0xFF00E5FF, // Cyan
    0xFF00E676, // Toxic Green
    0xFFFF9100, // Amber
    0xFFFF3D71, // Red
    0xFF7C4DFF, // Violet
    0xFFFFFFFF, // Pure White
)

private val styleNames = listOf(
    "Classic Cross",
    "Precision Dot",
    "Cross + Dot",
    "T-Bar Reticle",
    "Tactical Chevron",
    "Circle Reticle",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrosshairScreen(
    onBack: () -> Unit,
    vm: CrosshairViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val cfg = vm.crosshair.collectAsState().value ?: CrosshairSettings(
        enabled = false, x = 0.5f, y = 0.5f, size = 0.06f, color = 0xFF00E5FF, opacity = 0.9f, style = 0,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.crosshair_studio_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (cfg.enabled) "Overlay Active" else "Overlay Standby", fontSize = 12.sp, color = if (cfg.enabled) CyberColors.Green else CyberColors.TextSecondary)
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
            // Live Preview Canvas
            CyberCard(
                title = "Live Reticle Preview",
                subtitle = "Preview of custom overlay as it appears over game viewports",
                badgeText = if (cfg.enabled) "VISIBLE ON SCREEN" else "OFF",
                badgeColor = if (cfg.enabled) CyberColors.Green else CyberColors.Amber,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070B12))
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        // Draw subtle target background rings
                        drawCircle(Color(0xFF131D2E), radius = size.height * 0.35f, center = Offset(cx, cy), style = Stroke(width = 1f))
                        drawCircle(Color(0xFF131D2E), radius = size.height * 0.20f, center = Offset(cx, cy), style = Stroke(width = 1f))
                        drawLine(Color(0xFF131D2E), Offset(cx - size.width * 0.3f, cy), Offset(cx + size.width * 0.3f, cy), strokeWidth = 1f)
                        drawLine(Color(0xFF131D2E), Offset(cx, cy - size.height * 0.4f), Offset(cx, cy + size.height * 0.4f), strokeWidth = 1f)

                        // Draw selected crosshair
                        val reticleColor = Color(cfg.color).copy(alpha = cfg.opacity)
                        val reticleSizePx = cfg.size * size.width * 1.8f
                        val arm = reticleSizePx * 0.42f
                        val gap = reticleSizePx * 0.12f
                        val strokeW = (reticleSizePx * 0.05f).coerceAtLeast(2f)

                        when (cfg.style) {
                            1 -> { // Dot
                                drawCircle(reticleColor, radius = reticleSizePx * 0.14f, center = Offset(cx, cy))
                            }
                            2 -> { // Cross + Dot
                                drawLine(reticleColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW)
                                drawCircle(reticleColor, radius = reticleSizePx * 0.07f, center = Offset(cx, cy))
                            }
                            3 -> { // T-Bar
                                drawLine(reticleColor, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = strokeW)
                                drawCircle(reticleColor, radius = reticleSizePx * 0.06f, center = Offset(cx, cy))
                            }
                            4 -> { // Chevron
                                drawLine(reticleColor, Offset(cx - arm * 0.6f, cy + arm * 0.6f), Offset(cx, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy), Offset(cx + arm * 0.6f, cy + arm * 0.6f), strokeWidth = strokeW)
                                drawCircle(reticleColor, radius = reticleSizePx * 0.04f, center = Offset(cx, cy + arm * 0.3f))
                            }
                            5 -> { // Circle Reticle
                                drawCircle(reticleColor, radius = reticleSizePx * 0.32f, center = Offset(cx, cy), style = Stroke(width = strokeW))
                                drawCircle(reticleColor, radius = reticleSizePx * 0.06f, center = Offset(cx, cy))
                            }
                            else -> { // 0: Classic Cross
                                drawLine(reticleColor, Offset(cx - arm, cy), Offset(cx - gap * 1.3f, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx + gap * 1.3f, cy), Offset(cx + arm, cy), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy - arm), Offset(cx, cy - gap * 1.3f), strokeWidth = strokeW)
                                drawLine(reticleColor, Offset(cx, cy + gap * 1.3f), Offset(cx, cy + arm), strokeWidth = strokeW)
                                drawCircle(reticleColor, radius = reticleSizePx * 0.05f, center = Offset(cx, cy))
                            }
                        }
                    }
                }
            }

            // Reticle Style Selection
            CyberCard(title = stringResource(R.string.crosshair_style)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    styleNames.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { name ->
                                val idx = styleNames.indexOf(name)
                                val isSel = cfg.style == idx
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { vm.setStyle(idx, ctx) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) CyberColors.Cyan.copy(alpha = 0.15f) else CyberColors.SurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSel) CyberColors.Cyan else CyberColors.Border),
                                ) {
                                    Box(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                        Text(name, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) CyberColors.Cyan else CyberColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Color Swatches
            CyberCard(title = stringResource(R.string.crosshair_color)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    colorPalette.forEach { argb ->
                        val isSel = cfg.color == argb
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (isSel) 3.dp else 1.dp, if (isSel) Color.White else Color.Black, CircleShape)
                                .clickable { vm.setColor(argb, ctx) },
                        )
                    }
                }
            }

            // Size & Opacity Sliders
            CyberCard(title = "Reticle Dimensions") {
                CyberSlider(
                    label = "Size Scale",
                    value = cfg.size,
                    valueRange = 0.03f..0.15f,
                    onValueChange = { vm.setSize(it, ctx) },
                )
                Spacer(Modifier.height(8.dp))
                CyberSlider(
                    label = "Opacity",
                    value = cfg.opacity,
                    valueRange = 0.2f..1f,
                    onValueChange = { vm.setOpacity(it, ctx) },
                )
            }

            // Center Calibration Controls
            CyberCard(
                title = "Center Calibration",
                subtitle = "Align reticle perfectly with your game's weapon crosshair",
                badgeText = "RESET",
                onClick = { vm.recenter(ctx) },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Position: (%.1f%%, %.1f%%)".format(cfg.x * 100f, cfg.y * 100f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberColors.Cyan,
                    )

                    // D-Pad Nudge Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CyberOutlinedButton(text = "◀", onClick = { vm.nudge(-0.005f, 0f, ctx) }, modifier = Modifier.size(36.dp))
                        CyberOutlinedButton(text = "▲", onClick = { vm.nudge(0f, -0.005f, ctx) }, modifier = Modifier.size(36.dp))
                        CyberOutlinedButton(text = "▼", onClick = { vm.nudge(0f, 0.005f, ctx) }, modifier = Modifier.size(36.dp))
                        CyberOutlinedButton(text = "▶", onClick = { vm.nudge(0.005f, 0f, ctx) }, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}
