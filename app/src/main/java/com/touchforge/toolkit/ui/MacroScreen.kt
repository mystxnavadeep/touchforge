package com.touchforge.toolkit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.touchforge.toolkit.core.engine.MacroPlayer
import com.touchforge.toolkit.core.engine.MacroRecorder
import com.touchforge.toolkit.core.model.MacroDef
import com.touchforge.toolkit.core.model.MacroStep
import com.touchforge.toolkit.core.model.MacroStepType
import com.touchforge.toolkit.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MacroViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }

    var macroList by mutableStateOf<List<MacroDef>>(emptyList())
        private set

    var recordingMacro by mutableStateOf(false)
        private set
    private val recorder = MacroRecorder()
    var recordedStepsCount by mutableIntStateOf(0)
        private set

    var playingMacroId by mutableStateOf<String?>(null)
        private set
    var playbackProgress by mutableStateOf(0f)
        private set
    private var playbackJob: Job? = null

    init {
        loadMacros()
    }

    fun loadMacros() = viewModelScope.launch {
        macroList = profiles.listMacros()
    }

    fun startRecording() {
        recorder.begin(System.currentTimeMillis())
        recordedStepsCount = 0
        recordingMacro = true
    }

    fun recordEvent(type: MacroStepType, x: Float, y: Float) {
        if (!recordingMacro) return
        recorder.add(type, x, y, System.currentTimeMillis())
        recordedStepsCount++
    }

    fun stopRecording(name: String, loop: Boolean) {
        if (!recordingMacro) return
        val macro = recorder.finish(
            name = name.ifBlank { "Macro ${macroList.size + 1}" },
            id = "mac_" + UUID.randomUUID().toString().take(6),
            loop = loop,
        )
        recordingMacro = false
        recordedStepsCount = 0
        profiles.saveMacro(macro)
        loadMacros()
    }

    fun playMacro(macro: MacroDef) {
        playbackJob?.cancel()
        playingMacroId = macro.id
        val player = MacroPlayer(macro)
        val totalMs = player.durationMs.coerceAtLeast(300)

        playbackJob = viewModelScope.launch {
            var elapsed = 0L
            while (isActive && elapsed <= totalMs) {
                playbackProgress = elapsed.toFloat() / totalMs
                delay(20)
                elapsed += 20
            }
            playbackProgress = 0f
            playingMacroId = null
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playingMacroId = null
        playbackProgress = 0f
    }

    fun deleteMacro(id: String) = viewModelScope.launch {
        profiles.deleteMacro(id)
        loadMacros()
    }

    fun saveMacro(macro: MacroDef) {
        profiles.saveMacro(macro)
        loadMacros()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroScreen(
    onBack: () -> Unit,
    vm: MacroViewModel = hiltViewModel(),
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedMacroForDetail by remember { mutableStateOf<MacroDef?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.macro_studio_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${vm.macroList.size} Saved Macros", fontSize = 12.sp, color = CyberColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberColors.Cyan)
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
                title = "Macro Studio",
                subtitle = stringResource(R.string.macro_desc),
                icon = Icons.Default.TouchApp,
                iconTint = CyberColors.Violet,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!vm.recordingMacro) {
                        NeonButton(
                            text = stringResource(R.string.macro_record),
                            onClick = { vm.startRecording() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.FiberManualRecord,
                            color = CyberColors.Red,
                        )
                    } else {
                        NeonButton(
                            text = stringResource(R.string.macro_stop_record),
                            onClick = { showNameDialog = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Stop,
                            color = CyberColors.Amber,
                        )
                    }
                }
            }

            // Live Recording Capture Pad (visible when recording)
            AnimatedVisibility(visible = vm.recordingMacro) {
                CyberCard(
                    title = "Recording Gestures...",
                    subtitle = "${vm.recordedStepsCount} events captured. Tap or swipe in the canvas below.",
                    badgeText = "REC",
                    badgeColor = CyberColors.Red,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF140D18))
                            .border(1.dp, CyberColors.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { off ->
                                        vm.recordEvent(MacroStepType.DOWN, off.x / size.width, off.y / size.height)
                                    },
                                    onDragEnd = {
                                        vm.recordEvent(MacroStepType.UP, 0.5f, 0.5f)
                                    },
                                    onDragCancel = {
                                        vm.recordEvent(MacroStepType.UP, 0.5f, 0.5f)
                                    },
                                ) { change, _ ->
                                    vm.recordEvent(MacroStepType.MOVE, change.position.x / size.width, change.position.y / size.height)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "TAP OR SWIPE HERE TO RECORD TOUCH",
                            fontWeight = FontWeight.Bold,
                            color = CyberColors.Red,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // Macro List
            CyberCard(title = "Recorded Macros") {
                if (vm.macroList.isEmpty()) {
                    Text(
                        stringResource(R.string.macro_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.TextSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        vm.macroList.forEach { m ->
                            val isPlaying = vm.playingMacroId == m.id
                            MacroItemCard(
                                macro = m,
                                isPlaying = isPlaying,
                                progress = if (isPlaying) vm.playbackProgress else 0f,
                                onPlay = { if (isPlaying) vm.stopPlayback() else vm.playMacro(m) },
                                onDelete = { vm.deleteMacro(m.id) },
                                onSelect = { selectedMacroForDetail = m },
                            )
                        }
                    }
                }
            }

            // Macro Timeline Detail view (if a macro is selected)
            selectedMacroForDetail?.let { detail ->
                CyberCard(
                    title = "Timeline: ${detail.name}",
                    subtitle = "${detail.steps.size} steps",
                    badgeText = if (detail.loop) "LOOPING" else "SINGLE",
                    badgeColor = if (detail.loop) CyberColors.Cyan else CyberColors.TextSecondary,
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        itemsIndexed(detail.steps) { idx, step ->
                            StepChip(index = idx + 1, step = step)
                        }
                    }
                }
            }
        }
    }

    // Name dialog after stopping recording
    if (showNameDialog) {
        var macroName by remember { mutableStateOf("") }
        var loop by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save Recorded Macro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = macroName,
                        onValueChange = { macroName = it },
                        label = { Text("Macro Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CyberSwitchRow(
                        label = "Loop Continuously",
                        checked = loop,
                        onCheckedChange = { loop = it },
                    )
                }
            },
            confirmButton = {
                NeonButton(
                    text = stringResource(R.string.save),
                    onClick = {
                        vm.stopRecording(macroName, loop)
                        showNameDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
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
private fun MacroItemCard(
    macro: MacroDef,
    isPlaying: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    val totalDelayMs = macro.steps.sumOf { it.delayMs }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberColors.SurfaceVariant,
        border = BorderStroke(1.dp, if (isPlaying) CyberColors.Cyan else CyberColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(macro.id) { detectTapGestures { onSelect() } },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(macro.name, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                        if (macro.loop) {
                            StatusBadge(text = "LOOP", color = CyberColors.Cyan)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${macro.steps.size} steps · ${totalDelayMs}ms duration",
                        fontSize = 11.sp,
                        color = CyberColors.TextSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play/Stop",
                            tint = if (isPlaying) CyberColors.Amber else CyberColors.Cyan,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberColors.Red)
                    }
                }
            }
            if (isPlaying) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = CyberColors.Cyan,
                    trackColor = CyberColors.Border,
                )
            }
        }
    }
}

@Composable
private fun StepChip(index: Int, step: MacroStep) {
    val chipColor = when (step.type) {
        MacroStepType.DOWN -> CyberColors.Cyan
        MacroStepType.UP -> CyberColors.Teal
        MacroStepType.MOVE -> CyberColors.Violet
        MacroStepType.SLEEP -> CyberColors.Amber
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = chipColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("#$index ${step.type.name}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chipColor)
            if (step.type == MacroStepType.SLEEP) {
                Text("${step.delayMs}ms", fontSize = 10.sp, color = CyberColors.TextSecondary)
            } else {
                Text("(%.2f, %.2f)".format(step.x, step.y), fontSize = 10.sp, color = CyberColors.TextSecondary)
            }
        }
    }
}
