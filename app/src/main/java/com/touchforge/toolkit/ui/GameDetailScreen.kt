package com.touchforge.toolkit.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
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
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.model.AimCurve
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.core.model.SensitivityZone
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.db.ProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {
    var profileEntity by mutableStateOf<ProfileEntity?>(null)
        private set
    var mapping by mutableStateOf(MappingFileV1())
        private set

    fun load(pkg: String) = viewModelScope.launch {
        val pair = profiles.get(pkg) ?: return@launch
        profileEntity = pair.first
        mapping = pair.second
    }

    fun setEnabled(pkg: String, on: Boolean) = viewModelScope.launch {
        profiles.setEnabled(pkg, on)
        profileEntity = profileEntity?.copy(enabled = on)
    }

    fun duplicate(pkg: String, targetPkg: String, targetName: String, onDone: () -> Unit) = viewModelScope.launch {
        profiles.duplicate(pkg, targetPkg, targetName)
        onDone()
    }

    fun resetToTemplate(pkg: String, template: String) = viewModelScope.launch {
        val newMapping = when (template) {
            "fps" -> MappingFileV1(
                name = profileEntity?.displayName ?: "FPS Profile",
                packageName = pkg,
                createdAt = System.currentTimeMillis(),
                sensitivityZones = listOf(
                    SensitivityZone(id = "aim", left = 0.45f, top = 0.2f, right = 0.95f, bottom = 0.8f, sensitivityX = 1.6f, sensitivityY = 1.4f),
                ),
                aimCurve = AimCurve(enabled = true, x1 = 0.35f, y1 = 0.15f, x2 = 0.8f, y2 = 0.7f),
            )
            "moba" -> MappingFileV1(
                name = profileEntity?.displayName ?: "MOBA Profile",
                packageName = pkg,
                createdAt = System.currentTimeMillis(),
                sensitivityZones = listOf(
                    SensitivityZone(id = "move_stick", left = 0.05f, top = 0.45f, right = 0.4f, bottom = 0.95f, sensitivityX = 1.2f, sensitivityY = 1.2f),
                    SensitivityZone(id = "skill_pad", left = 0.6f, top = 0.45f, right = 0.95f, bottom = 0.95f, sensitivityX = 1.5f, sensitivityY = 1.5f),
                ),
            )
            else -> MappingFileV1(name = profileEntity?.displayName ?: "Default", packageName = pkg)
        }
        profiles.saveProfile(pkg, profileEntity?.displayName ?: pkg, newMapping)
        mapping = newMapping
    }

    fun delete(pkg: String, onDeleted: () -> Unit) = viewModelScope.launch {
        profiles.delete(pkg)
        onDeleted()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    pkg: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenAimCurve: (String) -> Unit,
    onOpenTriggers: (String) -> Unit,
    onOpenKeyMap: (String) -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    LaunchedEffect(pkg) { vm.load(pkg) }

    val entity = vm.profileEntity
    val mapping = vm.mapping
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(entity?.displayName ?: pkg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(pkg, fontSize = 11.sp, color = CyberColors.TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberColors.Cyan)
                    }
                },
                actions = {
                    IconButton(onClick = { showDuplicateDialog = true }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = CyberColors.Cyan)
                    }
                    IconButton(onClick = { vm.delete(pkg, onBack) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberColors.Red)
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
            // Header card with launch button
            CyberCard(
                title = entity?.displayName ?: pkg,
                subtitle = pkg,
                icon = Icons.Default.Gamepad,
                badgeText = if (entity?.enabled == true) "ENABLED" else "DISABLED",
                badgeColor = if (entity?.enabled == true) CyberColors.Green else CyberColors.Amber,
            ) {
                CyberSwitchRow(
                    label = "Auto-Apply Profile for this Game",
                    checked = entity?.enabled ?: true,
                    onCheckedChange = { vm.setEnabled(pkg, it) },
                    subtitle = "Automatically attaches touch zones when game enters foreground",
                )
                Spacer(Modifier.height(12.dp))
                NeonButton(
                    text = stringResource(R.string.game_launch),
                    onClick = {
                        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            ctx.startActivity(launchIntent)
                        } else {
                            Toast.makeText(ctx, "Could not launch $pkg", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.PlayArrow,
                )
            }

            // Stats breakdown
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = "Zones",
                    value = "${mapping.sensitivityZones.size}",
                    accentColor = CyberColors.Cyan,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Triggers",
                    value = "${mapping.triggers.size}",
                    accentColor = CyberColors.Amber,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Keys",
                    value = "${mapping.keyMaps.size}",
                    accentColor = CyberColors.Violet,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Curve",
                    value = if (mapping.aimCurve.enabled) "ON" else "OFF",
                    accentColor = if (mapping.aimCurve.enabled) CyberColors.Green else CyberColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }

            // Quick Studio Navigation Hub for this Game
            CyberCard(title = "Toolkit Studios for this Game") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioRow(
                        title = stringResource(R.string.tool_zones),
                        desc = "Interactive on-screen sensitivity & target canvas",
                        icon = Icons.Default.TouchApp,
                        iconTint = CyberColors.Cyan,
                        onClick = { onOpenEditor(pkg) },
                    )
                    StudioRow(
                        title = stringResource(R.string.tool_aim_curve),
                        desc = "Piecewise-cubic Bezier acceleration response curve",
                        icon = Icons.Default.Timeline,
                        iconTint = CyberColors.Teal,
                        onClick = { onOpenAimCurve(pkg) },
                    )
                    StudioRow(
                        title = stringResource(R.string.tool_triggers),
                        desc = "Rapid-fire & auto-tap touch frequencies",
                        icon = Icons.Default.Bolt,
                        iconTint = CyberColors.Amber,
                        onClick = { onOpenTriggers(pkg) },
                    )
                    StudioRow(
                        title = stringResource(R.string.tool_keymap),
                        desc = "Hardware key & controller to screen mappings",
                        icon = Icons.Default.Keyboard,
                        iconTint = CyberColors.Violet,
                        onClick = { onOpenKeyMap(pkg) },
                    )
                }
            }

            // Templates & Reset Card
            CyberCard(
                title = "Preset Templates",
                subtitle = "Apply balanced esports configs for FPS or MOBA games",
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberOutlinedButton(
                        text = "FPS Preset",
                        onClick = { vm.resetToTemplate(pkg, "fps") },
                        modifier = Modifier.weight(1f),
                        color = CyberColors.Cyan,
                    )
                    CyberOutlinedButton(
                        text = "MOBA Preset",
                        onClick = { vm.resetToTemplate(pkg, "moba") },
                        modifier = Modifier.weight(1f),
                        color = CyberColors.Teal,
                    )
                }
            }
        }
    }

    if (showDuplicateDialog) {
        var copyPkg by remember { mutableStateOf("$pkg.copy") }
        var copyName by remember { mutableStateOf("${entity?.displayName ?: "Game"} Copy") }

        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(stringResource(R.string.game_duplicate)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = copyName,
                        onValueChange = { copyName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = copyPkg,
                        onValueChange = { copyPkg = it },
                        label = { Text("Package Name Identifier") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                NeonButton(
                    text = stringResource(R.string.ok),
                    onClick = {
                        vm.duplicate(pkg, copyPkg, copyName) {
                            showDuplicateDialog = false
                            onBack()
                        }
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
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
private fun StudioRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberColors.SurfaceVariant,
        border = BorderStroke(1.dp, CyberColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary, fontSize = 14.sp)
                Text(desc, fontSize = 11.sp, color = CyberColors.TextSecondary)
            }
            Text("➔", color = CyberColors.Cyan, fontWeight = FontWeight.Bold)
        }
    }
}
