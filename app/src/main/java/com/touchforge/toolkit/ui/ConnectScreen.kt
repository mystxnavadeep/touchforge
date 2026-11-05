package com.touchforge.toolkit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.session.SessionManager
import com.touchforge.toolkit.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectViewModel @Inject constructor(
    val session: SessionManager,
) : ViewModel() {
    val state: StateFlow<SessionState> = session.state

    fun shizukuInstalled() = session.isShizukuInstalled()
    fun shizukuAuthorized() = session.isShizukuAuthorized()
    fun requestPermission() = session.requestPermission()
    fun connect() = session.connect()
    fun disconnect() = session.disconnect()

    fun diagnostics(onResult: (List<String>) -> Unit) = viewModelScope.launch {
        val lines = buildList {
            add(session.exec("cat /proc/stat")?.lineSequence()?.firstOrNull() ?: "cat /proc/stat: unavailable")
            add("shell uid: ${session.service?.shellUid ?: "-"}")
            add("protocol: ${session.service?.protocolVersion ?: "-"}")
            add("inject: ${session.service?.canInjectProbe ?: false}")
            add("grab lib: ${session.service?.canGrabProbe ?: false}")
        }
        onResult(lines)
    }
}

@Composable
fun ConnectScreen(vm: ConnectViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val installed = vm.shizukuInstalled()
    val authorized = vm.shizukuAuthorized()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.connect_shizuku), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !installed -> stringResource(R.string.shizuku_missing)
                        !authorized -> stringResource(R.string.shizuku_needs_permission)
                        else -> stringResource(R.string.shizuku_ready)
                    },
                )
                if (!installed) {
                    Button(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                        }
                    }) { Text(stringResource(R.string.shizuku_open_site)) }
                } else if (!authorized) {
                    Button(onClick = { vm.requestPermission() }) { Text(stringResource(R.string.shizuku_grant)) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.connect() }) { Text(stringResource(R.string.connect_start)) }
                        OutlinedButton(onClick = { vm.disconnect() }) { Text(stringResource(R.string.connect_stop)) }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.connect_state), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val s = state) {
                        is SessionState.Connected ->
                            stringResource(R.string.connect_state_ok, s.protocol, s.shellUid) +
                                "\ninject=" + s.caps.canInject + " grab=" + s.caps.canGrabRawInput
                        SessionState.Connecting -> stringResource(R.string.session_connecting)
                        SessionState.Idle -> stringResource(R.string.session_idle)
                        is SessionState.Failed -> stringResource(R.string.session_failed, s.reason)
                    },
                )
                if (state is SessionState.Connected) {
                    DiagnosticsButton(vm)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.safety_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.safety_watchdog), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiagnosticsButton(vm: ConnectViewModel) {
    var result by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(listOf<String>()) }
    Column {
        Button(onClick = { vm.diagnostics { result = it } }) { Text(stringResource(R.string.diagnostics_run)) }
        result.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
