package com.touchforge.toolkit.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.core.gate.TierGate
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.SensitivityZone
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.db.ProfileEntity
import com.touchforge.toolkit.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AppEntry(val pkg: String, val label: String)

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    val list: StateFlow<List<ProfileEntity>> =
        profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun installedApps(pm: PackageManager): List<AppEntry> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != "com.touchforge.toolkit" }
            .distinctBy { it.activityInfo.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()

    fun add(pm: PackageManager, pkg: String) {
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        viewModelScope.launch {
            profiles.saveProfile(pkg, label, MappingFileV1(name = label, createdAt = System.currentTimeMillis()))
        }
    }

    fun setEnabled(pkg: String, on: Boolean) = viewModelScope.launch { profiles.setEnabled(pkg, on) }
    fun remove(pkg: String) = viewModelScope.launch { profiles.delete(pkg) }
}

@Composable
fun GamesScreen(onOpenEditor: (String) -> Unit, vm: GamesViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val profiles by vm.list.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.game_library), style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showPicker = true }) { Text(stringResource(R.string.add_game)) }
        }
        if (profiles.isEmpty()) Text(stringResource(R.string.no_games))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.packageName }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(p.packageName, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onOpenEditor(p.packageName) }) {
                                Text(stringResource(R.string.edit))
                            }
                            OutlinedButton(onClick = { vm.setEnabled(p.packageName, !p.enabled) }) {
                                Text(stringResource(if (p.enabled) R.string.enabled else R.string.disabled))
                            }
                            OutlinedButton(onClick = { vm.remove(p.packageName) }) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val installed = remember { vm.installedApps(pm) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.pick_app)) },
            text = {
                LazyColumn {
                    items(installed) { app ->
                        TextButton(onClick = {
                            vm.add(pm, app.pkg)
                            showPicker = false
                        }) { Text(app.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ---------------------------------------------------------------------------
// Zone editor
// ---------------------------------------------------------------------------

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val session: SessionManager,
) : ViewModel() {

    var mapping by mutableStateOf(MappingFileV1())
        private set
    var displayName by mutableStateOf("")
        private set
    var selectedZoneId by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
        private set

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        mapping = pair.second
        displayName = pair.first.displayName
    }

    fun addZone(zone: SensitivityZone, tier: Tier) {
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
        dirty = true
    }

    fun updateSelected(sx: Float, sy: Float, tier: Tier) {
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

    fun deleteSelected() {
        val id = selectedZoneId ?: return
        mapping = mapping.copy(sensitivityZones = mapping.sensitivityZones.filterNot { it.id == id })
        selectedZoneId = null
        dirty = true
    }

    fun save(pkg: String, onSaved: (Boolean) -> Unit) = viewModelScope.launch {
        val result = profiles.saveProfile(pkg, displayName, mapping)
        dirty = result is ProfileRepository.SaveResult.Invalid
        onSaved(result is ProfileRepository.SaveResult.Ok)
    }
}

@Composable
fun ProfileEditorScreen(pkg: String, onDone: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    LaunchedEffect(pkg) { vm.load(pkg) }
    val mapping = vm.mapping
    val selectedId = vm.selectedZoneId
    val selected = mapping.sensitivityZones.firstOrNull { it.id == selectedId }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val tier = Tier.PRO // editor limits; actual store enforces final tier on save via schema + gate

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.editor_title, vm.displayName), style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19f)
                .pointerInput(mapping.sensitivityZones) {
                    detectDragGestures(
                        onDragStart = { off -> dragStart = off; dragEnd = off },
                        onDragEnd = {
                            val s = dragStart
                            val e = dragEnd
                            if (s != null && e != null) {
                                val l = minOf(s.x, e.x) / size.width
                                val t = minOf(s.y, e.y) / size.height
                                val r = maxOf(s.x, e.x) / size.width
                                val b = maxOf(s.y, e.y) / size.height
                                if (r - l > 0.03f && b - t > 0.03f) {
                                    vm.addZone(
                                        SensitivityZone(
                                            id = "z" + UUID.randomUUID().toString().take(8),
                                            left = l, top = t, right = r, bottom = b,
                                            sensitivityX = 1.5f, sensitivityY = 1.5f,
                                        ),
                                        tier,
                                    )
                                }
                            }
                            dragStart = null; dragEnd = null
                        },
                        onDragCancel = { dragStart = null; dragEnd = null },
                    ) { change, _ ->
                        dragEnd = change.position
                    }
                },
        ) {
            drawRect(Color(0xFF10151C))
            mapping.sensitivityZones.forEach { z ->
                val isSel = z.id == selectedId
                drawRect(
                    color = if (isSel) Color(0xFF00BFA5) else Color(0xFF3A7CA5),
                    topLeft = Offset(z.left * size.width, z.top * size.height),
                    size = Size((z.right - z.left) * size.width, (z.bottom - z.top) * size.height),
                    style = Stroke(width = if (isSel) 5f else 3f),
                )
            }
            val s = dragStart
            val e = dragEnd
            if (s != null && e != null) {
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(minOf(s.x, e.x), minOf(s.y, e.y)),
                    size = Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y)),
                    style = Stroke(width = 2f),
                )
            }
        }

        if (selected != null) {
            Text(stringResource(R.string.editor_selected, selected.id))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("X ${"%.2f".format(selected.sensitivityX)}")
                    Slider(
                        value = selected.sensitivityX,
                        onValueChange = { vm.updateSelected(it, selected.sensitivityY, tier) },
                        valueRange = 0.1f..TierGate.limits(tier).maxSensitivity,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Y ${"%.2f".format(selected.sensitivityY)}")
                    Slider(
                        value = selected.sensitivityY,
                        onValueChange = { vm.updateSelected(selected.sensitivityX, it, tier) },
                        valueRange = 0.1f..TierGate.limits(tier).maxSensitivity,
                    )
                }
            }
            OutlinedButton(onClick = { vm.deleteSelected() }) { Text(stringResource(R.string.delete_zone)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.save(pkg) { onDone() } }) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
        }
        Text(
            stringResource(R.string.editor_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
