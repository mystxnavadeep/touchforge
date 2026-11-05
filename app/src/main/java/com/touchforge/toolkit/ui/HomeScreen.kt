package com.touchforge.toolkit.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.capability.Availability
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.core.gate.TierGate
import com.touchforge.toolkit.data.CrosshairSettings
import com.touchforge.toolkit.data.HudSettings
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.service.OverlayHostService
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
) : ViewModel() {

    val state: StateFlow<SessionState> = session.state
    val features = session.features()
    val tier: StateFlow<Tier> = settings.tier.stateIn(viewModelScope, SharingStarted.Eagerly, Tier.FREE)
    val sensitivity: StateFlow<Float> = settings.globalSensitivity.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val hud: StateFlow<HudSettings?> = settings.hud.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val crosshair: StateFlow<CrosshairSettings?> = settings.crosshair.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSensitivity(v: Float) = viewModelScope.launch {
        val decision = TierGate.sensitivity(tier.value, v)
        settings.setGlobalSensitivity(decision.let { d ->
            when (d) {
                is TierGate.SensitivityDecision.Allowed -> d.value
                is TierGate.SensitivityDecision.Clamped -> d.value
            }
        })
    }

    fun applyShowTouches(on: Boolean) = viewModelScope.launch {
        session.applySystemSetting("show_touches", if (on) "1" else "0")
    }

    fun toggleHud(current: HudSettings?) = viewModelScope.launch {
        val next = (current ?: HudSettings(true, 1000, true, true, true, true, 0.8f)).let { it.copy(enabled = !it.enabled) }
        settings.setHud(next)
        syncOverlayService(next.enabled || (crosshair.value?.enabled ?: false))
    }

    fun toggleCrosshair(current: CrosshairSettings?) = viewModelScope.launch {
        val next = (current ?: CrosshairSettings(true, 0.5f, 0.5f, 0.06f, 0xFF00E5FF, 0.9f)).let { it.copy(enabled = !it.enabled) }
        settings.setCrosshair(next)
        syncOverlayService(next.enabled || (hud.value?.enabled ?: false))
    }

    private fun syncOverlayService(anyOverlay: Boolean) {
        val ctx = sessionContext ?: return
        if (anyOverlay) OverlayHostService.start(ctx) else OverlayHostService.stop(ctx)
    }

    fun openOverlayPermission(ctx: android.content.Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
    }

    fun memoryTrim(pkg: String) = viewModelScope.launch { session.exec("am send-trim-memory $pkg complete") }

    fun applyResolution(w: Int, h: Int) = viewModelScope.launch {
        session.startResolution(w, h, 0)
    }

    fun restoreAll() = viewModelScope.launch { session.restoreAll() }

    fun stopEverything() {
        session.disconnect()
        sessionContext?.let { OverlayHostService.stop(it) }
    }

    // Context for service start; set from the screen (framework requires application ctx).
    var sessionContext: android.content.Context? = null
}

@Composable
fun HomeScreen(onOpenConnect: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    vm.sessionContext = ctx.applicationContext
    val state by vm.state.collectAsState()
    val tier by vm.tier.collectAsState()
    val sens by vm.sensitivity.collectAsState()
    val hud by vm.hud.collectAsState()
    val cross by vm.crosshair.collectAsState()
    var showResDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.session_status), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val s = state) {
                        is SessionState.Connected -> stringResource(R.string.session_connected, s.shellUid)
                        is SessionState.Connecting -> stringResource(R.string.session_connecting)
                        is SessionState.Idle -> stringResource(R.string.session_idle)
                        is SessionState.Failed -> stringResource(R.string.session_failed, s.reason)
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenConnect) { Text(stringResource(R.string.open_connect)) }
                    OutlinedButton(onClick = { vm.stopEverything() }) { Text(stringResource(R.string.stop_all)) }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.global_sensitivity), style = MaterialTheme.typography.titleMedium)
                val limit = TierGate.limits(tier).maxSensitivity
                Text(stringResource(R.string.sensitivity_tier_note, sens, limit))
                Slider(
                    value = sens.coerceIn(0.1f, 5f),
                    onValueChange = { vm.setSensitivity(it) },
                    valueRange = 0.1f..5f,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quick_toggles), style = MaterialTheme.typography.titleMedium)
                ToggleRow(stringResource(R.string.show_touches)) { on -> vm.applyShowTouches(on) }
                ToggleRow(stringResource(R.string.crosshair), initial = cross?.enabled ?: false) {
                    if (!Settings.canDrawOverlays(ctx)) vm.openOverlayPermission(ctx) else vm.toggleCrosshair(cross)
                }
                ToggleRow(stringResource(R.string.hud), initial = hud?.enabled ?: false) {
                    if (!Settings.canDrawOverlays(ctx)) vm.openOverlayPermission(ctx) else vm.toggleHud(hud)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.capabilities), style = MaterialTheme.typography.titleMedium)
                vm.features.forEach { (f, st) ->
                    val label = when (st.availability) {
                        Availability.AVAILABLE -> "✔"
                        Availability.FALLBACK -> "~"
                        Availability.UNAVAILABLE -> "✖"
                    }
                    Text("$label ${f.name} — ${st.mode.ifBlank { st.reason }}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showResDialog = true }) { Text(stringResource(R.string.resolution_scale)) }
            OutlinedButton(onClick = { vm.restoreAll() }) { Text(stringResource(R.string.restore_changes)) }
        }
    }

    if (showResDialog) {
        ResolutionDialog(
            vm = vm,
            onDismiss = { showResDialog = false },
        )
    }
}

@Composable
private fun ToggleRow(label: String, initial: Boolean = false, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}

@Composable
private fun ResolutionDialog(vm: HomeViewModel, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(0.85f) }
    var current by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        current = vm.session.exec("wm size")?.let { com.touchforge.toolkit.core.shell.CommandPolicy.parseWmSize(it) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resolution_scale)) },
        text = {
            Column {
                Text(stringResource(R.string.resolution_current, current?.first ?: 0, current?.second ?: 0))
                Text(stringResource(R.string.resolution_warning))
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.5f..1f)
                val (w, h) = current ?: (0 to 0)
                Text(stringResource(R.string.resolution_target, (w * scale).toInt(), (h * scale).toInt()))
            }
        },
        confirmButton = {
            Button(onClick = {
                val (w, h) = current ?: return@Button
                vm.applyResolution((w * scale).toInt(), (h * scale).toInt())
                onDismiss()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
