package com.touchforge.toolkit.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.touchforge.toolkit.core.model.KeyMapDef
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class KeyMapViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    var keyMaps by mutableStateOf<List<KeyMapDef>>(emptyList())
        private set
    var displayName by mutableStateOf("")
        private set
    private var fullMapping = MappingFileV1()

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        displayName = pair.first.displayName
        fullMapping = pair.second
        keyMaps = pair.second.keyMaps
    }

    fun addKeyMap(def: KeyMapDef) {
        keyMaps = keyMaps + def
    }

    fun updateKeyMap(def: KeyMapDef) {
        keyMaps = keyMaps.map { if (it.id == def.id) def else it }
    }

    fun removeKeyMap(id: String) {
        keyMaps = keyMaps.filterNot { it.id == id }
    }

    fun save(pkg: String, onDone: () -> Unit) = viewModelScope.launch {
        profiles.saveProfile(pkg, displayName, fullMapping.copy(keyMaps = keyMaps))
        onDone()
    }
}

private val commonKeyCodes = listOf(
    "VOL_UP" to KeyEvent.KEYCODE_VOLUME_UP,
    "VOL_DOWN" to KeyEvent.KEYCODE_VOLUME_DOWN,
    "BTN_A" to KeyEvent.KEYCODE_BUTTON_A,
    "BTN_B" to KeyEvent.KEYCODE_BUTTON_B,
    "BTN_X" to KeyEvent.KEYCODE_BUTTON_X,
    "BTN_Y" to KeyEvent.KEYCODE_BUTTON_Y,
    "L1" to KeyEvent.KEYCODE_BUTTON_L1,
    "R1" to KeyEvent.KEYCODE_BUTTON_R1,
    "L2" to KeyEvent.KEYCODE_BUTTON_L2,
    "R2" to KeyEvent.KEYCODE_BUTTON_R2,
    "SPACE" to KeyEvent.KEYCODE_SPACE,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapScreen(
    pkg: String,
    onBack: () -> Unit,
    vm: KeyMapViewModel = hiltViewModel(),
) {
    LaunchedEffect(pkg) { vm.load(pkg) }
    var selectedKeyId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.keymap_studio_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            // Overview card
            CyberCard(
                title = "Hardware & Screen Key Mapper",
                subtitle = stringResource(R.string.keymap_desc),
                icon = Icons.Default.Keyboard,
                badgeText = "${vm.keyMaps.size} MAPPINGS",
            ) {
                Text(
                    "Virtual on-screen targets are triggered when hardware controller or volume keys are pressed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            }

            // Visual Canvas Preview
            CyberCard(
                title = "Mapping Placement Canvas",
                subtitle = "Tap on the canvas to place a virtual touch button",
                badgeText = "+ ADD KEY",
                onClick = { showAddDialog = true },
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090E18))
                        .border(1.dp, CyberColors.Border, RoundedCornerShape(12.dp))
                        .pointerInput(vm.keyMaps) {
                            detectTapGestures { tapOffset ->
                                val normX = tapOffset.x / size.width
                                val normY = tapOffset.y / size.height
                                val hit = vm.keyMaps.firstOrNull { km ->
                                    val kx = km.x * size.width
                                    val ky = km.y * size.height
                                    val r = km.size * size.width * 0.5f
                                    (tapOffset - Offset(kx, ky)).getDistance() <= r
                                }
                                selectedKeyId = hit?.id
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height

                    vm.keyMaps.forEach { km ->
                        val isSel = km.id == selectedKeyId
                        val center = Offset(km.x * w, km.y * h)
                        val radius = km.size * w * 0.5f
                        val color = if (isSel) CyberColors.Cyan else CyberColors.Violet

                        drawCircle(
                            color = color.copy(alpha = km.opacity * 0.4f),
                            radius = radius,
                            center = center,
                        )
                        drawCircle(
                            color = color,
                            radius = radius,
                            center = center,
                            style = Stroke(width = if (isSel) 4f else 2f),
                        )
                    }
                }
            }

            // Mapped Keys List
            CyberCard(title = "Configured Mappings") {
                if (vm.keyMaps.isEmpty()) {
                    Text(
                        stringResource(R.string.keymap_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.TextSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.keyMaps.forEach { km ->
                            KeyMapRow(
                                item = km,
                                isSelected = km.id == selectedKeyId,
                                onSelect = { selectedKeyId = km.id },
                                onDelete = { vm.removeKeyMap(km.id) },
                            )
                        }
                    }
                }
            }

            // Save & Cancel
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

    if (showAddDialog) {
        var label by remember { mutableStateOf("BTN") }
        var selectedCode by remember { mutableIntStateOf(KeyEvent.KEYCODE_VOLUME_UP) }
        var sizeFrac by remember { mutableFloatStateOf(0.08f) }
        var opacityFrac by remember { mutableFloatStateOf(0.7f) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.keymap_add_button)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.keymap_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.keymap_keycode), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        commonKeyCodes.take(5).forEach { (name, code) ->
                            StatusBadge(
                                text = name,
                                color = if (selectedCode == code) CyberColors.Cyan else CyberColors.TextSecondary,
                                modifier = Modifier.pointerInput(code) {
                                    detectTapGestures { selectedCode = code }
                                },
                            )
                        }
                    }
                    CyberSlider(
                        label = "Button Size",
                        value = sizeFrac,
                        valueRange = 0.04f..0.20f,
                        onValueChange = { sizeFrac = it },
                    )
                    CyberSlider(
                        label = "Opacity",
                        value = opacityFrac,
                        valueRange = 0.2f..1f,
                        onValueChange = { opacityFrac = it },
                    )
                }
            },
            confirmButton = {
                NeonButton(
                    text = stringResource(R.string.ok),
                    onClick = {
                        vm.addKeyMap(
                            KeyMapDef(
                                id = "km_" + UUID.randomUUID().toString().take(6),
                                keyCode = selectedCode,
                                x = 0.5f,
                                y = 0.5f,
                                label = label.ifBlank { "BTN" },
                                size = sizeFrac,
                                opacity = opacityFrac,
                            ),
                        )
                        showAddDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = CyberColors.Surface,
            titleContentColor = CyberColors.TextPrimary,
            textContentColor = CyberColors.TextPrimary,
        )
    }
}

@Composable
private fun KeyMapRow(
    item: KeyMapDef,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CyberColors.SurfaceVariant else CyberColors.Surface,
        border = BorderStroke(1.dp, if (isSelected) CyberColors.Cyan else CyberColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.id) { detectTapGestures { onSelect() } },
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CyberColors.Violet.copy(alpha = 0.2f))
                        .border(1.dp, CyberColors.Violet, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.label.take(3), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Violet)
                }
                Column {
                    Text(item.label, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                    Text("KeyCode: ${item.keyCode} · Pos: (%.2f, %.2f)".format(item.x, item.y), fontSize = 11.sp, color = CyberColors.TextSecondary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberColors.Red)
            }
        }
    }
}
