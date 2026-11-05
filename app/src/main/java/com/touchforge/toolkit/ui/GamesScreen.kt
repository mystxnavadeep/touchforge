package com.touchforge.toolkit.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.model.MappingFileV1
import com.touchforge.toolkit.data.ProfileRepository
import com.touchforge.toolkit.data.db.ProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onOpenGameDetail: (String) -> Unit,
    onOpenEditor: (String) -> Unit,
    vm: GamesViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val profiles by vm.list.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val filtered = remember(profiles, searchQuery) {
        if (searchQuery.isBlank()) profiles
        else profiles.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.game_library), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${profiles.size} Games Configured", fontSize = 12.sp, color = CyberColors.TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Game", tint = CyberColors.Cyan)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.game_search_hint), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberColors.Cyan) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberColors.Cyan,
                    unfocusedBorderColor = CyberColors.Border,
                    focusedContainerColor = CyberColors.Surface,
                    unfocusedContainerColor = CyberColors.Surface,
                ),
                singleLine = true,
            )

            // Games List
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Gamepad, contentDescription = null, tint = CyberColors.TextTertiary, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (profiles.isEmpty()) stringResource(R.string.no_games) else "No matching games found",
                            color = CyberColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        NeonButton(
                            text = stringResource(R.string.add_game),
                            onClick = { showPicker = true },
                            icon = Icons.Default.Add,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.packageName }) { p ->
                        GameCardItem(
                            profile = p,
                            onToggle = { vm.setEnabled(p.packageName, it) },
                            onOpenDetail = { onOpenGameDetail(p.packageName) },
                            onOpenEditor = { onOpenEditor(p.packageName) },
                            onLaunch = {
                                val launchIntent = pm.getLaunchIntentForPackage(p.packageName)
                                if (launchIntent != null) ctx.startActivity(launchIntent)
                                else Toast.makeText(ctx, "App not found", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        val installed = remember { vm.installedApps(pm) }
        var pickerFilter by remember { mutableStateOf("") }
        val pickerList = remember(installed, pickerFilter) {
            if (pickerFilter.isBlank()) installed
            else installed.filter {
                it.label.contains(pickerFilter, ignoreCase = true) ||
                    it.pkg.contains(pickerFilter, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.pick_app)) },
            text = {
                Column(Modifier.height(350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pickerFilter,
                        onValueChange = { pickerFilter = it },
                        placeholder = { Text("Search installed apps...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(pickerList) { app ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.add(pm, app.pkg)
                                        showPicker = false
                                    }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = CyberColors.SurfaceVariant,
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(CyberColors.Cyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(app.label.take(1).uppercase(), fontWeight = FontWeight.Bold, color = CyberColors.Cyan)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = CyberColors.TextPrimary)
                                        Text(app.pkg, fontSize = 10.sp, color = CyberColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = CyberColors.Surface,
            titleContentColor = CyberColors.TextPrimary,
            textContentColor = CyberColors.TextPrimary,
        )
    }
}

@Composable
private fun GameCardItem(
    profile: ProfileEntity,
    onToggle: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenEditor: () -> Unit,
    onLaunch: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberColors.Surface,
        border = BorderStroke(1.dp, CyberColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.Cyan.copy(alpha = 0.12f))
                            .border(1.dp, CyberColors.BorderGlow, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.displayName.take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberColors.Cyan,
                        )
                    }
                    Column {
                        Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = CyberColors.TextPrimary)
                        Text(profile.packageName, fontSize = 11.sp, color = CyberColors.TextSecondary)
                    }
                }
                StatusBadge(
                    text = if (profile.enabled) "ACTIVE" else "OFF",
                    color = if (profile.enabled) CyberColors.Green else CyberColors.Amber,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CyberOutlinedButton(
                    text = "Edit Zones",
                    onClick = onOpenEditor,
                    modifier = Modifier.weight(1f),
                )
                CyberOutlinedButton(
                    text = "Details",
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f),
                    color = CyberColors.Teal,
                )
                NeonButton(
                    text = "Launch",
                    onClick = onLaunch,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow,
                )
            }
        }
    }
}
