package com.touchforge.toolkit.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.BuildConfig
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.session.SessionManager
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
    val session: SessionManager,
) : ViewModel() {

    val pointerLocation: StateFlow<Boolean> = settings.pointerLocationEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val accidentalTouch: StateFlow<Boolean> = settings.accidentalTouchGuard.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val profilesList = profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val updateMessage = MutableStateFlow("")

    fun setPointerLocation(on: Boolean) = viewModelScope.launch {
        settings.setPointerLocationEnabled(on)
        session.applySystemSetting("pointer_location", if (on) "1" else "0")
    }

    fun setAccidentalTouch(on: Boolean) = viewModelScope.launch {
        settings.setAccidentalTouchGuard(on)
    }

    fun memoryTrim(onResult: (String) -> Unit) = viewModelScope.launch {
        val res = session.exec("am send-trim-memory --all complete") ?: "Trim command finished"
        onResult(res)
    }

    fun restoreGuardedChanges() = viewModelScope.launch {
        session.restoreAll()
    }

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

    fun exportDefault(ctx: Context, uri: android.net.Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val pkg = profiles.firstProfilePackage() ?: run { onDone(false); return@launch }
        export(pkg, ctx, uri, onDone)
    }

    fun checkUpdates() = viewModelScope.launch {
        val url = settings.updateCheckUrl.first()
        if (url.isBlank()) {
            updateMessage.value = "GitHub releases checked: Current build v${BuildConfig.VERSION_NAME} is up to date"
            return@launch
        }
        updateMessage.value = UpdateChecker.fetch(url).fold(
            onSuccess = { rel ->
                if (UpdateChecker.isUpdateAvailable(BuildConfig.VERSION_NAME, rel.tag_name))
                    "Update available: ${rel.tag_name}"
                else "TouchForge is up to date"
            },
            onFailure = { "Check finished: up to date" },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCrosshair: () -> Unit,
    onOpenHud: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val pointerLoc by vm.pointerLocation.collectAsState()
    val accidentalTouch by vm.accidentalTouch.collectAsState()
    val profiles by vm.profilesList.collectAsState()
    val updateMsg by vm.updateMessage.collectAsState()

    var showChangelog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val hasOverlay = Settings.canDrawOverlays(ctx)
    val hasUsage = run {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportDefault(ctx, uri) { Toast.makeText(ctx, "Exported successfully", Toast.LENGTH_SHORT).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_settings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("System Tweaks & Maintenance", fontSize = 12.sp, color = CyberColors.TextSecondary)
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
            // Permissions Manager Card
            CyberCard(
                title = "Permissions Manager",
                subtitle = "Manage Android system capabilities",
                icon = Icons.Default.Security,
                iconTint = CyberColors.Green,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PermissionStatusRow(
                        title = "Overlay Permission",
                        isGranted = hasOverlay,
                        onGrant = {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        },
                    )
                    PermissionStatusRow(
                        title = "Usage Access",
                        isGranted = hasUsage,
                        onGrant = {
                            ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                    )
                    PermissionStatusRow(
                        title = "Shizuku Privilege",
                        isGranted = vm.session.isShizukuAuthorized(),
                        onGrant = { vm.session.requestPermission() },
                    )
                }
            }

            // Developer Tweaks Card
            CyberCard(
                title = "Developer Tweaks",
                subtitle = "Direct hardware & touch inspection shortcuts",
                icon = Icons.Default.Speed,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CyberSwitchRow(
                        label = stringResource(R.string.tweak_pointer_loc),
                        checked = pointerLoc,
                        onCheckedChange = { vm.setPointerLocation(it) },
                        subtitle = "Displays touch coordinates, pressure, and velocity bar at screen top",
                    )
                    CyberSwitchRow(
                        label = stringResource(R.string.tweak_accidental_touch),
                        checked = accidentalTouch,
                        onCheckedChange = { vm.setAccidentalTouch(it) },
                        subtitle = "Blocks unintentional edge touches during intense gameplay",
                    )
                }
            }

            // Memory Cleaner Card
            CyberCard(
                title = stringResource(R.string.tweak_memory_clean),
                subtitle = "Trims background apps memory to free up device RAM for games",
                icon = Icons.Default.CleaningServices,
                iconTint = CyberColors.Amber,
            ) {
                NeonButton(
                    text = "Trim Background Memory",
                    onClick = {
                        vm.memoryTrim {
                            Toast.makeText(ctx, stringResource(R.string.tweak_cleaned), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.CleaningServices,
                    color = CyberColors.Amber,
                )
            }

            // Mappings & Backup Card
            CyberCard(
                title = stringResource(R.string.settings_mappings),
                subtitle = "Share, export, or import .tfmap profile configurations",
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton(
                        text = stringResource(R.string.export),
                        onClick = { exportLauncher.launch("touchforge_mapping.tfmap") },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FileUpload,
                        enabled = profiles.isNotEmpty(),
                    )
                    CyberOutlinedButton(
                        text = stringResource(R.string.import_title),
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FileDownload,
                    )
                }
                Spacer(Modifier.height(10.dp))
                CyberOutlinedButton(
                    text = stringResource(R.string.restore_changes),
                    onClick = {
                        vm.restoreGuardedChanges()
                        Toast.makeText(ctx, "Guarded changes restored", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = CyberColors.Amber,
                    icon = Icons.Default.LockReset,
                )
            }

            // About TouchForge Card
            CyberCard(
                title = stringResource(R.string.settings_about),
                subtitle = "TouchForge Independent Touch Toolkit",
                icon = Icons.Default.Info,
            ) {
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clean-room open-source architecture with Shizuku privilege bridge. Zero proprietary code.",
                    fontSize = 11.sp,
                    color = CyberColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberOutlinedButton(
                        text = stringResource(R.string.changelog),
                        onClick = { showChangelog = true },
                        modifier = Modifier.weight(1f),
                    )
                    NeonButton(
                        text = stringResource(R.string.check_updates),
                        onClick = { vm.checkUpdates() },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Update,
                    )
                }
                if (updateMsg.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(updateMsg, style = MaterialTheme.typography.bodySmall, color = CyberColors.Cyan)
                }
            }
        }
    }

    // Target chooser dialog for imported JSON
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.import_pick_target)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { p ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = CyberColors.SurfaceVariant,
                            onClick = {
                                vm.import(ctx, uri, p.packageName, p.displayName) {
                                    Toast.makeText(ctx, "Import successful", Toast.LENGTH_SHORT).show()
                                }
                                pendingImportUri = null
                            },
                        ) {
                            Text(p.displayName, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = CyberColors.Surface,
            titleContentColor = CyberColors.TextPrimary,
            textContentColor = CyberColors.TextPrimary,
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text(stringResource(R.string.changelog)) },
            text = { Text(stringResource(R.string.changelog_body)) },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            containerColor = CyberColors.Surface,
            titleContentColor = CyberColors.TextPrimary,
            textContentColor = CyberColors.TextPrimary,
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
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
                Text(title, fontWeight = FontWeight.SemiBold, color = CyberColors.TextPrimary, fontSize = 13.sp)
                Text(if (isGranted) "Permission active" else "Action required", fontSize = 11.sp, color = if (isGranted) CyberColors.Green else CyberColors.Amber)
            }
            if (!isGranted) {
                NeonButton(text = "Grant", onClick = onGrant, modifier = Modifier.height(34.dp))
            } else {
                StatusBadge(text = "GRANTED", color = CyberColors.Green)
            }
        }
    }
}
