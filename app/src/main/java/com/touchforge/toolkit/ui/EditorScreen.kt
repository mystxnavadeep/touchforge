package com.touchforge.toolkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.touchforge.toolkit.core.engine.TouchTransform
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.core.gate.TierGate
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.SensitivityZone
import com.touchforge.toolkit.core.model.TargetZone
import com.touchforge.toolkit.core.model.TriggerZone
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    var mapping by mutableStateOf(MappingFileV1())
        private set
    var displayName by mutableStateOf("")
        private set
    var selectedZoneId by mutableStateOf<String?>(null)
    var selectedTargetId by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
        private set

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        mapping = pair.second
        displayName = pair.first.displayName
    }

    fun addSensitivityZone(zone: SensitivityZone, tier: Tier) {
        val decision = TierGate.canAddZone(tier, mapping.sensitivityZones.size)
        if (decision is TierGate.CountDecision.Blocked) return
        val clampedSx = TierGate.sensitivity(tier, zone.sensitivityX)
        val clampedSy = TierGate.sensitivity(tier, zone.sensitivityY)
        fun value(d: TierGate.SensitivityDecision) = when (d) {
            is TierGate.SensitivityDecision.Allowed -> d.value
            is TierGate.SensitivityDecision.Clamped -> d.value
        }
        mapping = mapping.copy(
            sensitivityZones = mapping.sensitivityZones + zone.copy(
                sensitivityX = value(clampedSx),
                sensitivityY = value(clampedSy),
            ),
        )
        selectedZoneId = zone.id
        selectedTargetId = null
        dirty = true
    }

    fun addTargetZone(target: TargetZone) {
        mapping = mapping.copy(targets = mapping.targets + target)
        selectedTargetId = target.id
        selectedZoneId = null
        dirty = true
    }

    fun updateSelectedSensitivity(sx: Float, sy: Float, tier: Tier) {
        val id = selectedZoneId ?: return
        fun clamp(d: TierGate.SensitivityDecision) = when (d) {
            is TierGate.SensitivityDecision.Allowed -> d.value
            is TierGate.SensitivityDecision.Clamped -> d.value
        }
        mapping = mapping.copy(
            sensitivityZones = mapping.sensitivityZones.map {
                if (it.id == id) it.copy(
                    sensitivityX = clamp(TierGate.sensitivity(tier, sx)),
                    sensitivityY = clamp(TierGate.sensitivity(tier, sy)),
                ) else it
            },
        )
        dirty = true
    }

    fun updateSelectedTarget(radius: Float, sx: Float, sy: Float) {
        val id = selectedTargetId ?: return
        mapping = mapping.copy(
            targets = mapping.targets.map {
                if (it.id == id) it.copy(radius = radius, sensitivityX = sx, sensitivityY = sy) else it
            },
        )
        dirty = true
    }

    fun deleteSelected() {
        if (selectedZoneId != null) {
            mapping = mapping.copy(sensitivityZones = mapping.sensitivityZones.filterNot { it.id == selectedZoneId })
            selectedZoneId = null
            dirty = true
        } else if (selectedTargetId != null) {
            mapping = mapping.copy(targets = mapping.targets.filterNot { it.id == selectedTargetId })
            selectedTargetId = null
            dirty = true
        }
    }

    fun save(pkg: String, onSaved: (Boolean) -> Unit) = viewModelScope.launch {
        val result = profiles.saveProfile(pkg, displayName, mapping)
        dirty = result is ProfileRepository.SaveResult.Invalid
        onSaved(result is ProfileRepository.SaveResult.Ok)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    pkg: String,
    onDone: () -> Unit,
    vm: EditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(pkg) { vm.load(pkg) }
    val mapping = vm.mapping
    val selectedZone = mapping.sensitivityZones.firstOrNull { it.id == vm.selectedZoneId }
    val selectedTarget = mapping.targets.firstOrNull { it.id == vm.selectedTargetId }

    var editorMode by remember { mutableIntStateOf(0) } // 0 = Sensitivity Rects, 1 = Target Circles
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val tier = Tier.PRO

    // Live Test Pad state
    var testTouchPos by remember { mutableStateOf<Offset?>(null) }
    var testResult by remember { mutableStateOf<TouchTransform.Result?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Touch Zones: ${vm.displayName.ifBlank { pkg }}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${mapping.sensitivityZones.size} Rects · ${mapping.targets.size} Targets", fontSize = 11.sp, color = CyberColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberColors.Cyan)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save(pkg) { onDone() } }) {
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
            // Mode Segmented Bar
            SegmentedTabRow(
                tabs = listOf("Sensitivity Rects", "Circular Targets"),
                selectedIndex = editorMode,
                onTabSelected = { editorMode = it },
            )

            // Canvas Box
            CyberCard(
                title = "Touch Screen Canvas",
                subtitle = "Drag to create new zones. Tap existing zone to adjust sensitivity.",
            ) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090E18))
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
                        .pointerInput(mapping.sensitivityZones, mapping.targets, editorMode) {
                            detectDragGestures(
                                onDragStart = { off -> dragStart = off; dragEnd = off },
                                onDragEnd = {
                                    val s = dragStart
                                    val e = dragEnd
                                    if (s != null && e != null) {
                                        val l = min(s.x, e.x) / size.width
                                        val t = min(s.y, e.y) / size.height
                                        val r = max(s.x, e.x) / size.width
                                        val b = max(s.y, e.y) / size.height

                                        if (editorMode == 0 && (r - l > 0.04f) && (b - t > 0.04f)) {
                                            vm.addSensitivityZone(
                                                SensitivityZone(
                                                    id = "zone_" + UUID.randomUUID().toString().take(6),
                                                    left = l, top = t, right = r, bottom = b,
                                                    sensitivityX = 1.5f, sensitivityY = 1.5f,
                                                ),
                                                tier,
                                            )
                                        } else if (editorMode == 1) {
                                            val radius = (abs(e.x - s.x) / size.width).coerceAtLeast(0.06f)
                                            vm.addTargetZone(
                                                TargetZone(
                                                    id = "tgt_" + UUID.randomUUID().toString().take(6),
                                                    centerX = s.x / size.width,
                                                    centerY = s.y / size.height,
                                                    radius = radius,
                                                    sensitivityX = 1.6f, sensitivityY = 1.6f,
                                                ),
                                            )
                                        }
                                    }
                                    dragStart = null; dragEnd = null
                                },
                                onDragCancel = { dragStart = null; dragEnd = null },
                            ) { change, _ ->
                                dragEnd = change.position
                            }
                        }
                        .pointerInput(mapping) {
                            detectTapGestures { tapOff ->
                                val nx = tapOff.x / size.width
                                val ny = tapOff.y / size.height
                                val hitTarget = mapping.targets.lastOrNull { TouchTransform.contains(it, nx, ny) }
                                if (hitTarget != null) {
                                    vm.selectedTargetId = hitTarget.id
                                    vm.selectedZoneId = null
                                } else {
                                    val hitZone = mapping.sensitivityZones.lastOrNull { TouchTransform.contains(it, nx, ny) }
                                    vm.selectedZoneId = hitZone?.id
                                    vm.selectedTargetId = null
                                }
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height

                    // Grid lines
                    for (i in 1..4) {
                        drawLine(Color(0xFF131D2D), Offset((w / 5f) * i, 0f), Offset((w / 5f) * i, h), 1f)
                    }
                    for (i in 1..2) {
                        drawLine(Color(0xFF131D2D), Offset(0f, (h / 3f) * i), Offset(w, (h / 3f) * i), 1f)
                    }

                    // Draw Rect Sensitivity Zones
                    mapping.sensitivityZones.forEach { z ->
                        val isSel = z.id == vm.selectedZoneId
                        val rectColor = if (isSel) CyberColors.Cyan else CyberColors.Teal
                        drawRect(
                            color = rectColor.copy(alpha = 0.20f),
                            topLeft = Offset(z.left * w, z.top * h),
                            size = Size((z.right - z.left) * w, (z.bottom - z.top) * h),
                        )
                        drawRect(
                            color = rectColor,
                            topLeft = Offset(z.left * w, z.top * h),
                            size = Size((z.right - z.left) * w, (z.bottom - z.top) * h),
                            style = Stroke(width = if (isSel) 4f else 2f),
                        )
                    }

                    // Draw Circular Targets
                    mapping.targets.forEach { t ->
                        val isSel = t.id == vm.selectedTargetId
                        val tgtColor = if (isSel) CyberColors.Amber else CyberColors.Violet
                        val center = Offset(t.centerX * w, t.centerY * h)
                        val rPx = t.radius * w

                        drawCircle(tgtColor.copy(alpha = 0.25f), radius = rPx, center = center)
                        drawCircle(tgtColor, radius = rPx, center = center, style = Stroke(width = if (isSel) 4f else 2f))
                        drawCircle(tgtColor, radius = 4f, center = center)
                    }

                    // Draw active drag creation preview
                    val s = dragStart
                    val e = dragEnd
                    if (s != null && e != null) {
                        if (editorMode == 0) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.4f),
                                topLeft = Offset(min(s.x, e.x), min(s.y, e.y)),
                                size = Size(abs(e.x - s.x), abs(e.y - s.y)),
                                style = Stroke(width = 2f),
                            )
                        } else {
                            val r = abs(e.x - s.x)
                            drawCircle(Color.White.copy(alpha = 0.4f), radius = r, center = s, style = Stroke(width = 2f))
                        }
                    }
                }
            }

            // Selected Item Property Inspector
            if (selectedZone != null) {
                CyberCard(
                    title = "Selected Zone: ${selectedZone.id}",
                    badgeText = "SENSITIVITY RECT",
                    badgeColor = CyberColors.Cyan,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberSlider(
                            label = "X-Axis Sensitivity",
                            value = selectedZone.sensitivityX,
                            valueRange = 0.2f..4.0f,
                            onValueChange = { vm.updateSelectedSensitivity(it, selectedZone.sensitivityY, tier) },
                            accentColor = CyberColors.Cyan,
                        )
                        CyberSlider(
                            label = "Y-Axis Sensitivity",
                            value = selectedZone.sensitivityY,
                            valueRange = 0.2f..4.0f,
                            onValueChange = { vm.updateSelectedSensitivity(selectedZone.sensitivityX, it, tier) },
                            accentColor = CyberColors.Teal,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            CyberOutlinedButton(
                                text = "Delete Zone",
                                onClick = { vm.deleteSelected() },
                                color = CyberColors.Red,
                                icon = Icons.Default.Delete,
                            )
                        }
                    }
                }
            } else if (selectedTarget != null) {
                CyberCard(
                    title = "Selected Target: ${selectedTarget.id}",
                    badgeText = "CIRCULAR TARGET",
                    badgeColor = CyberColors.Amber,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CyberSlider(
                            label = "Target Radius",
                            value = selectedTarget.radius,
                            valueRange = 0.05f..0.40f,
                            onValueChange = { vm.updateSelectedTarget(it, selectedTarget.sensitivityX, selectedTarget.sensitivityY) },
                            accentColor = CyberColors.Amber,
                        )
                        CyberSlider(
                            label = "X Sensitivity",
                            value = selectedTarget.sensitivityX,
                            valueRange = 0.2f..4.0f,
                            onValueChange = { vm.updateSelectedTarget(selectedTarget.radius, it, selectedTarget.sensitivityY) },
                        )
                        CyberSlider(
                            label = "Y Sensitivity",
                            value = selectedTarget.sensitivityY,
                            valueRange = 0.2f..4.0f,
                            onValueChange = { vm.updateSelectedTarget(selectedTarget.radius, selectedTarget.sensitivityX, it) },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            CyberOutlinedButton(
                                text = "Delete Target",
                                onClick = { vm.deleteSelected() },
                                color = CyberColors.Red,
                                icon = Icons.Default.Delete,
                            )
                        }
                    }
                }
            }

            // Live Interactive Touch Transform Test Pad
            CyberCard(
                title = "Live Touch Response Test Pad",
                subtitle = "Touch or drag inside this pad to verify real-time coordinate transformations",
                icon = Icons.Default.Speed,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Raw Coordinate",
                        value = if (testTouchPos != null) "(%.2f, %.2f)".format(testTouchPos!!.x, testTouchPos!!.y) else "-",
                        accentColor = CyberColors.Amber,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Transformed Coordinate",
                        value = if (testResult != null) "(%.2f, %.2f)".format(testResult!!.x, testResult!!.y) else "-",
                        accentColor = CyberColors.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberColors.SurfaceVariant)
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
                        .pointerInput(mapping) {
                            detectDragGestures(
                                onDragStart = { off ->
                                    val nx = off.x / size.width
                                    val ny = off.y / size.height
                                    testTouchPos = Offset(nx, ny)
                                    testResult = TouchTransform.apply(nx, ny, 1f, 1f, mapping.sensitivityZones, mapping.targets, mapping.aimCurve)
                                },
                                onDragEnd = { testTouchPos = null; testResult = null },
                                onDragCancel = { testTouchPos = null; testResult = null },
                            ) { change, _ ->
                                val nx = change.position.x / size.width
                                val ny = change.position.y / size.height
                                testTouchPos = Offset(nx, ny)
                                testResult = TouchTransform.apply(nx, ny, 1f, 1f, mapping.sensitivityZones, mapping.targets, mapping.aimCurve)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (testResult != null) "Active Zone: ${testResult?.zoneId ?: "None (Default Sens)"}"
                        else "Touch & drag here to test coordinate transformation live",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (testResult != null) CyberColors.Cyan else CyberColors.TextSecondary,
                    )
                }
            }

            // Save & Cancel Actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton(
                    text = stringResource(R.string.save),
                    onClick = { vm.save(pkg) { onDone() } },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Check,
                )
                CyberOutlinedButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
