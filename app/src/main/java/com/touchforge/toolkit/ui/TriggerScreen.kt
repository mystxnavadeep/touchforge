package com.touchforge.toolkit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.engine.TriggerEngine
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.TriggerRepeat
import com.touchforge.toolkit.core.model.TriggerZone
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TriggerViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    var triggers by mutableStateOf<List<TriggerZone>>(emptyList())
        private set
    var displayName by mutableStateOf("")
        private set
    private var fullMapping = MappingFileV1()

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        displayName = pair.first.displayName
        fullMapping = pair.second
        triggers = pair.second.triggers
    }

    fun addTrigger(zone: TriggerZone) {
        triggers = triggers + zone
    }

    fun updateTrigger(zone: TriggerZone) {
        triggers = triggers.map { if (it.id == zone.id) zone else it }
    }

    fun removeTrigger(id: String) {
        triggers = triggers.filterNot { it.id == id }
    }

    fun save(pkg: String, onDone: () -> Unit) = viewModelScope.launch {
        profiles.saveProfile(pkg, displayName, fullMapping.copy(triggers = triggers))
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    pkg: String,
    onBack: () -> Unit,
    vm: TriggerViewModel = hiltViewModel(),
) {
    LaunchedEffect(pkg) { vm.load(pkg) }
    var editingZone by remember { mutableStateOf<TriggerZone?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Live test pad state
    var testPressing by remember { mutableStateOf(false) }
    var testTapCount by remember { mutableIntStateOf(0) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var simulatedRate by remember { mutableStateOf(0f) }

    val activeInterval = vm.triggers.firstOrNull { it.enabled }?.tapIntervalMs ?: 80L
    val activeDuration = vm.triggers.firstOrNull { it.enabled }?.tapDurationMs ?: 40L

    // Simulated tap loop when holding test pad
    LaunchedEffect(testPressing, activeInterval) {
        if (testPressing) {
            val engine = TriggerEngine()
            val simulatedZone = TriggerZone(
                id = "test",
                left = 0f, top = 0f, right = 1f, bottom = 1f,
                tapIntervalMs = activeInterval,
                tapDurationMs = activeDuration,
            )
            engine.onDown(1, 0.5f, 0.5f, listOf(simulatedZone))
            val startMs = System.currentTimeMillis()
            var localTaps = 0

            while (isActive && testPressing) {
                val due = engine.due()
                if (due.any { it.down }) {
                    testTapCount++
                    localTaps++
                    lastTapTimeMs = System.currentTimeMillis()
                    val elapsedSec = (System.currentTimeMillis() - startMs) / 1000f
                    if (elapsedSec > 0.3f) {
                        simulatedRate = localTaps / elapsedSec
                    }
                }
                delay(10)
            }
        } else {
            simulatedRate = 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.trigger_studio_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            // Overview banner
            CyberCard(
                title = "Rapid Fire & Auto-Tap",
                subtitle = stringResource(R.string.trigger_desc),
                icon = Icons.Default.Bolt,
                iconTint = CyberColors.Amber,
                badgeText = "${vm.triggers.size} CONFIGURED",
                badgeColor = CyberColors.Cyan,
            ) {
                Text(
                    stringResource(R.string.trigger_safety_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            }

            // Live Test Pad Card
            CyberCard(
                title = stringResource(R.string.trigger_test_pad),
                subtitle = "Press & hold the pad below to verify synthetic trigger fire rate",
                icon = Icons.Default.Speed,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Fired Taps",
                        value = "$testTapCount",
                        accentColor = CyberColors.Amber,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Live Fire Rate",
                        value = "%.1f / sec".format(simulatedRate),
                        accentColor = CyberColors.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                val flashColor by animateColorAsState(
                    if (testPressing && System.currentTimeMillis() - lastTapTimeMs < 40) CyberColors.Cyan else CyberColors.SurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(flashColor)
                        .border(1.dp, if (testPressing) CyberColors.Cyan else CyberColors.Border, RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    testPressing = true
                                    tryAwaitRelease()
                                    testPressing = false
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (testPressing) CyberColors.Amber else CyberColors.TextTertiary),
                        )
                        Text(
                            if (testPressing) "FIRING TRIGGER..." else "HOLD DOWN TO TEST RAPID TAP",
                            fontWeight = FontWeight.Bold,
                            color = if (testPressing) CyberColors.TextPrimary else CyberColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Trigger Zones List
            CyberCard(
                title = "Configured Trigger Zones",
                badgeText = "+ ADD NEW",
                badgeColor = CyberColors.Cyan,
                onClick = { showAddDialog = true },
            ) {
                if (vm.triggers.isEmpty()) {
                    Text(
                        "No trigger zones assigned yet. Tap '+ ADD NEW' to create a rapid-fire zone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.TextSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        vm.triggers.forEach { z ->
                            TriggerItemRow(
                                zone = z,
                                onToggle = { vm.updateTrigger(z.copy(enabled = it)) },
                                onEdit = { editingZone = z },
                                onDelete = { vm.removeTrigger(z.id) },
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

    // Add / Edit Dialog
    val activeDialog = editingZone ?: if (showAddDialog) TriggerZone(
        id = "trig_" + UUID.randomUUID().toString().take(6),
        left = 0.3f, top = 0.3f, right = 0.7f, bottom = 0.7f,
        tapIntervalMs = 75,
        tapDurationMs = 35,
    ) else null

    activeDialog?.let { currentZone ->
        var interval by remember(currentZone) { mutableLongStateOf(currentZone.tapIntervalMs) }
        var duration by remember(currentZone) { mutableLongStateOf(currentZone.tapDurationMs) }
        var repeatMode by remember(currentZone) { mutableStateOf(currentZone.repeat) }

        AlertDialog(
            onDismissRequest = {
                editingZone = null
                showAddDialog = false
            },
            title = { Text(if (editingZone != null) "Edit Trigger Zone" else "New Trigger Zone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Rate: %.1f taps / sec".format(1000f / interval.coerceAtLeast(10)),
                        fontWeight = FontWeight.Bold,
                        color = CyberColors.Cyan,
                    )
                    CyberSlider(
                        label = "Interval: ${interval}ms",
                        value = interval.toFloat(),
                        valueRange = 20f..300f,
                        onValueChange = { interval = it.toLong() },
                    )
                    CyberSlider(
                        label = "Hold Duration: ${duration}ms",
                        value = duration.toFloat(),
                        valueRange = 10f..interval.toFloat(),
                        onValueChange = { duration = it.toLong() },
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Repeat Mode", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            repeatMode = if (repeatMode == TriggerRepeat.WHILE_HELD) TriggerRepeat.FIXED_COUNT else TriggerRepeat.WHILE_HELD
                        }) {
                            Text(if (repeatMode == TriggerRepeat.WHILE_HELD) "While Held" else "Fixed Count")
                        }
                    }
                }
            },
            confirmButton = {
                NeonButton(
                    text = stringResource(R.string.ok),
                    onClick = {
                        val updated = currentZone.copy(
                            tapIntervalMs = interval,
                            tapDurationMs = duration,
                            repeat = repeatMode,
                        )
                        if (editingZone != null) vm.updateTrigger(updated) else vm.addTrigger(updated)
                        editingZone = null
                        showAddDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    editingZone = null
                    showAddDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = CyberColors.Surface,
            titleContentColor = CyberColors.TextPrimary,
            textContentColor = CyberColors.TextPrimary,
        )
    }
}

@Composable
private fun TriggerItemRow(
    zone: TriggerZone,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(zone.id, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                    StatusBadge(
                        text = "%.1f/s".format(1000f / zone.tapIntervalMs.coerceAtLeast(10)),
                        color = CyberColors.Cyan,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Interval: ${zone.tapIntervalMs}ms · Duration: ${zone.tapDurationMs}ms · ${zone.repeat.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Speed, contentDescription = "Edit", tint = CyberColors.Cyan)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberColors.Red)
                }
            }
        }
    }
}
