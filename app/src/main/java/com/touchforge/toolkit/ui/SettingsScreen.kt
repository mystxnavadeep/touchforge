package com.touchforge.toolkit.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.BuildConfig
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.CrosshairSettings
import com.touchforge.toolkit.data.HudSettings
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    val hud: StateFlow<HudSettings?> = settings.hud.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val crosshair: StateFlow<CrosshairSettings?> = settings.crosshair.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profilesList = profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val updateMessage = MutableStateFlow("")

    fun setHudInterval(ms: Int) = viewModelScope.launch { hud.value?.let { settings.setHud(it.copy(intervalMs = ms)) } }
    fun setHudOpacity(v: Float) = viewModelScope.launch { hud.value?.let { settings.setHud(it.copy(opacity = v)) } }
    fun setCrosshairSize(v: Float) = viewModelScope.launch { crosshair.value?.let { settings.setCrosshair(it.copy(size = v)) } }

    fun export(pkg: String, ctx: Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val text = profiles.exportProfile(pkg)
        val ok = if (text != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }.isSuccess
        } else false
        onDone(ok)
    }

    fun import(ctx: Context, uri: android.net.Uri, pkg: String, label: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        if (text == null) { onDone(false); return@launch }
        val result = profiles.importMapping(text, pkg, label)
        onDone(result is ProfileRepository.SaveResult.Ok)
    }

    suspend fun exportTargetPkg(): String? = profiles.firstProfilePackage()

    fun exportDefault(ctx: Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val pkg = profiles.firstProfilePackage() ?: run { onDone(false); return@launch }
        export(pkg, ctx, uri, onDone)
    }

    fun checkUpdates() = viewModelScope.launch {
        val url = settings.updateCheckUrl.first()
        if (url.isBlank()) {
            updateMessage.value = "URL not configured"
            return@launch
        }
        updateMessage.value = UpdateChecker.fetch(url).fold(
            onSuccess = { rel ->
                if (UpdateChecker.isUpdateAvailable(BuildConfig.VERSION_NAME, rel.tag_name))
                    "update: ${rel.tag_name}"
                else "up to date"
            },
            onFailure = { "error: ${it.message}" },
        )
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val hud by vm.hud.collectAsState()
    val cross by vm.crosshair.collectAsState()
    val profiles by vm.profilesList.collectAsState()
    val updateMsg by vm.updateMessage.collectAsState()
    var showChangelog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportDefault(ctx, uri) { }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    // Target chooser for imports.
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.import_pick_target)) },
            text = {
                Column {
                    profiles.forEach { p ->
                        TextButton(onClick = {
                            vm.import(ctx, uri, p.packageName, p.displayName) { }
                            pendingImportUri = null
                        }) { Text(p.displayName) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_hud), style = MaterialTheme.typography.titleMedium)
                ParamRow(stringResource(R.string.hud_interval), (hud?.intervalMs ?: 1000).toFloat(), 250f..5000f) {
                    vm.setHudInterval(it.toInt())
                }
                ParamRow(stringResource(R.string.hud_opacity), hud?.opacity ?: 0.8f, 0.2f..1f) { vm.setHudOpacity(it) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_crosshair), style = MaterialTheme.typography.titleMedium)
                ParamRow(stringResource(R.string.crosshair_size), cross?.size ?: 0.06f, 0.03f..0.2f) { vm.setCrosshairSize(it) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_mappings), style = MaterialTheme.typography.titleMedium)
                Row {
                    Button(onClick = { exportLauncher.launch("mapping.tfmap") }, enabled = profiles.isNotEmpty()) {
                        Text(stringResource(R.string.export))
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Text(stringResource(R.string.import_title))
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
                Row {
                    OutlinedButton(onClick = { showChangelog = true }) { Text(stringResource(R.string.changelog)) }
                    OutlinedButton(onClick = { vm.checkUpdates() }) { Text(stringResource(R.string.check_updates)) }
                }
                if (updateMsg.isNotBlank()) Text(updateMsg, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.language_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.changelog)) },
            text = { Text(stringResource(R.string.changelog_body)) },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ParamRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onCommit: (Float) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    Column {
        Text("$label  ${"%.2f".format(v)}")
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onCommit(v) }, valueRange = range)
    }
}
