package com.touchforge.toolkit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.engine.AimCurveMath
import com.touchforge.toolkit.core.model.AimCurve
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class AimCurveViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    var curve by mutableStateOf(AimCurve())
        private set
    var displayName by mutableStateOf("")
        private set
    var saved by mutableStateOf(false)
        private set

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        displayName = pair.first.displayName
        curve = pair.second.aimCurve
    }

    fun updateCurve(newCurve: AimCurve) {
        curve = newCurve
        saved = false
    }

    fun applyPreset(type: String) {
        curve = when (type) {
            "linear" -> AimCurve(enabled = true, x1 = 0.25f, y1 = 0.25f, x2 = 0.75f, y2 = 0.75f)
            "precision" -> AimCurve(enabled = true, x1 = 0.45f, y1 = 0.15f, x2 = 0.85f, y2 = 0.55f)
            "snap" -> AimCurve(enabled = true, x1 = 0.10f, y1 = 0.60f, x2 = 0.35f, y2 = 0.95f)
            "scurve" -> AimCurve(enabled = true, x1 = 0.25f, y1 = 0.10f, x2 = 0.75f, y2 = 0.90f)
            else -> AimCurve(enabled = false, x1 = 0.25f, y1 = 0.10f, x2 = 0.75f, y2 = 0.90f)
        }
        saved = false
    }

    fun save(pkg: String, onDone: () -> Unit) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        profiles.saveProfile(pkg, displayName, pair.second.copy(aimCurve = curve))
        saved = true
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AimCurveScreen(
    pkg: String,
    onBack: () -> Unit,
    vm: AimCurveViewModel = hiltViewModel(),
) {
    LaunchedEffect(pkg) { vm.load(pkg) }
    val curve = vm.curve

    // Test pad live drag vector magnitude (0f..1f)
    var testInputMag by remember { mutableFloatStateOf(0f) }
    val testOutputMag = if (curve.enabled) AimCurveMath.shape(testInputMag, curve) else testInputMag

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.aim_curve_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(vm.displayName.ifBlank { pkg }, fontSize = 12.sp, color = CyberColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberColors.Cyan)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save(pkg, onBack) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = CyberColors.Cyan)
                    }
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
            // Master toggle card
            CyberCard(
                title = stringResource(R.string.curve_enabled),
                subtitle = stringResource(R.string.curve_desc),
                icon = Icons.Default.Timeline,
                iconTint = CyberColors.Cyan,
                badgeText = if (curve.enabled) "ACTIVE" else "BYPASS",
                badgeColor = if (curve.enabled) CyberColors.Green else CyberColors.Amber,
            ) {
                CyberSwitchRow(
                    label = "Curve Acceleration",
                    checked = curve.enabled,
                    onCheckedChange = { vm.updateCurve(curve.copy(enabled = it)) },
                    subtitle = if (curve.enabled) "Dynamic piecewise-cubic transform applied" else "Direct 1:1 linear touch response",
                )
            }

            // Interactive Curve Canvas
            CyberCard(
                title = "Interactive Curve Graph",
                subtitle = "Drag control points P1 (Cyan) and P2 (Violet) directly to shape response",
            ) {
                CurveGraph(
                    curve = curve,
                    onPointsChanged = { x1, y1, x2, y2 ->
                        vm.updateCurve(curve.copy(x1 = x1, y1 = y1, x2 = x2, y2 = y2))
                    },
                )
            }

            // Presets Row
            CyberCard(title = "Response Presets") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CyberOutlinedButton(
                        text = stringResource(R.string.curve_linear),
                        onClick = { vm.applyPreset("linear") },
                        modifier = Modifier.weight(1f),
                    )
                    CyberOutlinedButton(
                        text = stringResource(R.string.curve_precision),
                        onClick = { vm.applyPreset("precision") },
                        modifier = Modifier.weight(1f),
                        color = CyberColors.Teal,
                    )
                    CyberOutlinedButton(
                        text = stringResource(R.string.curve_snap),
                        onClick = { vm.applyPreset("snap") },
                        modifier = Modifier.weight(1f),
                        color = CyberColors.Amber,
                    )
                    CyberOutlinedButton(
                        text = stringResource(R.string.curve_scurve),
                        onClick = { vm.applyPreset("scurve") },
                        modifier = Modifier.weight(1f),
                        color = CyberColors.Violet,
                    )
                }
            }

            // Numeric Fine-Tuning Sliders
            CyberCard(title = "Control Point Coordinates") {
                CyberSlider(
                    label = "Point 1 X (Speed Threshold)",
                    value = curve.x1,
                    valueRange = 0.05f..0.95f,
                    onValueChange = { vm.updateCurve(curve.copy(x1 = it)) },
                    accentColor = CyberColors.Cyan,
                )
                CyberSlider(
                    label = "Point 1 Y (Initial Sensitivity)",
                    value = curve.y1,
                    valueRange = 0.05f..0.95f,
                    onValueChange = { vm.updateCurve(curve.copy(y1 = it)) },
                    accentColor = CyberColors.Cyan,
                )
                Spacer(Modifier.height(8.dp))
                CyberSlider(
                    label = "Point 2 X (Acceleration Point)",
                    value = curve.x2,
                    valueRange = 0.05f..0.95f,
                    onValueChange = { vm.updateCurve(curve.copy(x2 = it)) },
                    accentColor = CyberColors.Violet,
                )
                CyberSlider(
                    label = "Point 2 Y (Peak Acceleration)",
                    value = curve.y2,
                    valueRange = 0.05f..0.95f,
                    onValueChange = { vm.updateCurve(curve.copy(y2 = it)) },
                    accentColor = CyberColors.Violet,
                )
            }

            // Interactive Live Curve Test Pad
            CyberCard(
                title = stringResource(R.string.curve_test_pad),
                subtitle = stringResource(R.string.curve_test_hint),
                icon = Icons.Default.Refresh,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Raw Input Drag",
                        value = "${(testInputMag * 100).toInt()}%",
                        accentColor = CyberColors.Amber,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Transformed Output",
                        value = "${(testOutputMag * 100).toInt()}%",
                        accentColor = CyberColors.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberColors.SurfaceVariant)
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { off ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val dx = (off.x - center.x) / (size.width / 2f)
                                    val dy = (off.y - center.y) / (size.height / 2f)
                                    testInputMag = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                                },
                                onDragEnd = { testInputMag = 0f },
                                onDragCancel = { testInputMag = 0f },
                            ) { change, _ ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = (change.position.x - center.x) / (size.width / 2f)
                                val dy = (change.position.y - center.y) / (size.height / 2f)
                                testInputMag = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (testInputMag > 0.01f) "Input: ${(testInputMag * 100).toInt()}%  ➔  Output: ${(testOutputMag * 100).toInt()}%"
                        else "Touch & drag here to test response curve",
                        color = if (testInputMag > 0.01f) CyberColors.Cyan else CyberColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NeonButton(
                    text = stringResource(R.string.save),
                    onClick = { vm.save(pkg, onBack) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Check,
                )
                CyberOutlinedButton(
                    text = stringResource(R.string.cancel),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CurveGraph(
    curve: AimCurve,
    onPointsChanged: (x1: Float, y1: Float, x2: Float, y2: Float) -> Unit,
) {
    var activeHandle by remember { mutableStateOf<Int?>(null) } // 1 = P1, 2 = P2

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070B12))
            .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
            .pointerInput(curve) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val p1Pos = Offset(curve.x1 * size.width, (1f - curve.y1) * size.height)
                        val p2Pos = Offset(curve.x2 * size.width, (1f - curve.y2) * size.height)
                        val d1 = (pos - p1Pos).getDistance()
                        val d2 = (pos - p2Pos).getDistance()
                        activeHandle = when {
                            d1 < 45f && d1 <= d2 -> 1
                            d2 < 45f -> 2
                            else -> null
                        }
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                ) { change, _ ->
                    val normX = (change.position.x / size.width).coerceIn(0.01f, 0.99f)
                    val normY = (1f - (change.position.y / size.height)).coerceIn(0.01f, 0.99f)
                    if (activeHandle == 1) {
                        onPointsChanged(normX, normY, curve.x2, curve.y2)
                    } else if (activeHandle == 2) {
                        onPointsChanged(curve.x1, curve.y1, normX, normY)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height

        // 1. Grid Lines (10x10)
        for (i in 1..9) {
            val gx = (w / 10f) * i
            val gy = (h / 10f) * i
            drawLine(
                color = Color(0xFF141F30),
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color(0xFF141F30),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
            )
        }

        // 2. Identity line (dashed 1:1 diagonal)
        drawLine(
            color = Color(0xFF334155),
            start = Offset(0f, h),
            end = Offset(w, 0f),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
        )

        // 3. Handle lines: (0,h) to P1, and (w,0) to P2
        val p1Offset = Offset(curve.x1 * w, (1f - curve.y1) * h)
        val p2Offset = Offset(curve.x2 * w, (1f - curve.y2) * h)

        drawLine(
            color = CyberColors.Cyan.copy(alpha = 0.5f),
            start = Offset(0f, h),
            end = p1Offset,
            strokeWidth = 2f,
        )
        drawLine(
            color = CyberColors.Violet.copy(alpha = 0.5f),
            start = Offset(w, 0f),
            end = p2Offset,
            strokeWidth = 2f,
        )

        // 4. Bezier response curve
        val path = Path()
        path.moveTo(0f, h)
        val steps = 80
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val out = AimCurveMath.shape(t, curve)
            path.lineTo(t * w, (1f - out) * h)
        }
        drawPath(
            path = path,
            color = if (curve.enabled) CyberColors.Cyan else Color(0xFF64748B),
            style = Stroke(width = 4f),
        )

        // 5. Draw Control Handle Points P1 and P2
        drawCircle(
            color = CyberColors.Cyan,
            radius = 12f,
            center = p1Offset,
        )
        drawCircle(
            color = Color.White,
            radius = 5f,
            center = p1Offset,
        )

        drawCircle(
            color = CyberColors.Violet,
            radius = 12f,
            center = p2Offset,
        )
        drawCircle(
            color = Color.White,
            radius = 5f,
            center = p2Offset,
        )
    }
}
