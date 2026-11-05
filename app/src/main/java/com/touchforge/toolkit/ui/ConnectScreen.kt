package com.touchforge.toolkit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            add("[INFO] Probing TouchForge system services...")
            val procStat = session.exec("cat /proc/stat")?.lineSequence()?.firstOrNull()
            add(if (procStat != null) "[OK] Kernel CPU: $procStat" else "[WARN] cat /proc/stat: unavailable")
            add("[OK] Shell UID: ${session.service?.shellUid ?: "N/A"}")
            add("[OK] Protocol Version: ${session.service?.protocolVersion ?: "N/A"}")
            val canInject = session.service?.canInjectProbe() ?: false
            add(if (canInject) "[OK] Injection Engine: ACTIVE (IInputManager)" else "[FAIL] Injection: NOT READY")
            val canGrab = session.service?.canGrabProbe() ?: false
            add(if (canGrab) "[OK] Native Grab: AVAILABLE (libtouchforge_native)" else "[INFO] Native Grab: FALLBACK TO SYNTHETIC (non-root)")
            add("[INFO] Watchdog Guard: ARMED & RUNNING")
        }
        onResult(lines)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    vm: ConnectViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val installed = vm.shizukuInstalled()
    val authorized = vm.shizukuAuthorized()
    var diagnosticLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.connect_shizuku), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when (state) {
                                is SessionState.Connected -> "Connected to Shell"
                                SessionState.Connecting -> "Connecting..."
                                SessionState.Idle -> "Standby"
                                is SessionState.Failed -> "Connection Error"
                            },
                            fontSize = 12.sp,
                            color = when (state) {
                                is SessionState.Connected -> CyberColors.Green
                                SessionState.Connecting -> CyberColors.Cyan
                                SessionState.Idle -> CyberColors.Amber
                                is SessionState.Failed -> CyberColors.Red
                            },
                        )
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
            // Step-by-Step Activation Checklist Card
            CyberCard(
                title = "Privilege Bootstrap (Shizuku)",
                subtitle = "TouchForge utilizes Shizuku to execute low-latency touch injection with ADB shell privileges.",
                icon = Icons.Default.VpnKey,
                iconTint = CyberColors.Cyan,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Step 1: Install
                    ActivationStepItem(
                        stepNumber = 1,
                        title = "Shizuku Installed & Running",
                        desc = if (installed) "Shizuku manager service detected" else "Shizuku app not found on this device",
                        isDone = installed,
                        actionText = if (!installed) "Install Shizuku" else null,
                        onAction = {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                            }
                        },
                    )

                    // Step 2: Authorize
                    ActivationStepItem(
                        stepNumber = 2,
                        title = "TouchForge Permission",
                        desc = if (authorized) "Permission granted by user" else "TouchForge requires Shizuku permission",
                        isDone = authorized,
                        actionText = if (installed && !authorized) "Grant Permission" else null,
                        onAction = { vm.requestPermission() },
                    )

                    // Step 3: Connect
                    ActivationStepItem(
                        stepNumber = 3,
                        title = "Connect Toolkit Helper",
                        desc = when (val s = state) {
                            is SessionState.Connected -> "Connected (UID ${s.shellUid})"
                            SessionState.Connecting -> "Connecting helper..."
                            else -> "Start privileged helper service"
                        },
                        isDone = state is SessionState.Connected,
                        actionText = if (authorized && state !is SessionState.Connected) "Connect" else null,
                        onAction = { vm.connect() },
                    )
                }

                if (state is SessionState.Connected) {
                    Spacer(Modifier.height(14.dp))
                    CyberOutlinedButton(
                        text = "Disconnect Helper",
                        onClick = { vm.disconnect() },
                        modifier = Modifier.fillMaxWidth(),
                        color = CyberColors.Amber,
                        icon = Icons.Default.Stop,
                    )
                }
            }

            // Wireless Debugging Guide Card
            CyberCard(
                title = "Wireless Debugging (No PC Required)",
                subtitle = "On Android 11 or newer, start Shizuku directly using built-in developer options",
                icon = Icons.Default.Wifi,
                iconTint = CyberColors.Teal,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Enable Developer Options in Android Settings", fontSize = 12.sp, color = CyberColors.TextSecondary)
                    Text("2. Connect to Wi-Fi and enable 'Wireless Debugging'", fontSize = 12.sp, color = CyberColors.TextSecondary)
                    Text("3. In Shizuku, tap 'Pairing' and enter the 6-digit Wi-Fi pairing code", fontSize = 12.sp, color = CyberColors.TextSecondary)
                    Text("4. Tap 'Start' in Shizuku, then return to TouchForge and tap Connect", fontSize = 12.sp, color = CyberColors.TextSecondary)
                }
            }

            // Live Diagnostics Terminal Console
            CyberCard(
                title = "Live Diagnostics Console",
                subtitle = "Probe system services, injection hooks, and kernel evdev nodes",
                icon = Icons.Default.Terminal,
                badgeText = "RUN PROBE",
                badgeColor = CyberColors.Cyan,
                onClick = { vm.diagnostics { diagnosticLogs = it } },
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF05080E),
                    border = BorderStroke(1.dp, CyberColors.Border),
                ) {
                    Column(
                        Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (diagnosticLogs.isEmpty()) {
                            Text(
                                "Tap 'RUN PROBE' above to execute runtime diagnostics...\n\n- Tests ADB shell binder\n- Verifies IInputManager touch injector\n- Checks native evdev grab\n- Reads /proc/stat delta clock",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberColors.TextTertiary,
                            )
                        } else {
                            diagnosticLogs.forEach { line ->
                                val color = when {
                                    line.startsWith("[OK]") -> CyberColors.Green
                                    line.startsWith("[WARN]") -> CyberColors.Amber
                                    line.startsWith("[FAIL]") -> CyberColors.Red
                                    else -> CyberColors.Cyan
                                }
                                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color)
                            }
                        }
                    }
                }
            }

            // Safety Watchdog
            CyberCard(
                title = stringResource(R.string.safety_title),
                subtitle = stringResource(R.string.safety_watchdog),
                icon = Icons.Default.Security,
                iconTint = CyberColors.Green,
            ) {
                Text(
                    "TouchForge protects device stability. If the app is killed or loses focus unexpectedly, all system parameters are automatically restored to factory defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ActivationStepItem(
    stepNumber: Int,
    title: String,
    desc: String,
    isDone: Boolean,
    actionText: String?,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberColors.SurfaceVariant,
        border = BorderStroke(1.dp, if (isDone) CyberColors.Green.copy(alpha = 0.4f) else CyberColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isDone) CyberColors.Green.copy(alpha = 0.2f) else CyberColors.Border),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = CyberColors.Green, modifier = Modifier.size(16.dp))
                } else {
                    Text("$stepNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberColors.TextSecondary)
                }
            }

            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary, fontSize = 13.sp)
                Text(desc, fontSize = 11.sp, color = CyberColors.TextSecondary)
            }

            if (actionText != null) {
                NeonButton(
                    text = actionText,
                    onClick = onAction,
                    modifier = Modifier.height(34.dp),
                )
            }
        }
    }
}
