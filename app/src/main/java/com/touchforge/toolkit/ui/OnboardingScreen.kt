package com.touchforge.toolkit.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.data.SettingsRepository
import com.touchforge.toolkit.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val session: SessionManager,
) : ViewModel() {
    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        settings.setOnboardingCompleted(true)
        onDone()
    }

    fun hasOverlayPermission(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isShizukuReady(): Boolean = session.isShizukuInstalled() && session.isShizukuAuthorized()
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current

    var hasOverlay by remember { mutableStateOf(vm.hasOverlayPermission(ctx)) }
    var hasUsage by remember { mutableStateOf(vm.hasUsageAccess(ctx)) }
    var shizukuReady by remember { mutableStateOf(vm.isShizukuReady()) }

    // Re-check permissions when returning from system settings
    LaunchedEffect(Unit) {
        hasOverlay = vm.hasOverlayPermission(ctx)
        hasUsage = vm.hasUsageAccess(ctx)
        shizukuReady = vm.isShizukuReady()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CyberColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // Hero Logo & Title
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CyberColors.Cyan.copy(alpha = 0.15f))
                    .border(2.dp, CyberColors.Cyan, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = CyberColors.Cyan, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.onboarding_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CyberColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CyberColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Permission Card 1: Overlays
        PermissionSetupCard(
            title = stringResource(R.string.onboarding_perm_overlay),
            desc = stringResource(R.string.onboarding_perm_overlay_desc),
            icon = Icons.Default.Layers,
            granted = hasOverlay,
            onAction = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            },
        )

        // Permission Card 2: Usage Access
        PermissionSetupCard(
            title = stringResource(R.string.onboarding_perm_usage),
            desc = stringResource(R.string.onboarding_perm_usage_desc),
            icon = Icons.Default.Timeline,
            granted = hasUsage,
            onAction = {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )

        // Permission Card 3: Shizuku Privilege
        PermissionSetupCard(
            title = stringResource(R.string.onboarding_perm_shizuku),
            desc = stringResource(R.string.onboarding_perm_shizuku_desc),
            icon = Icons.Default.VpnKey,
            granted = shizukuReady,
            onAction = {
                if (!vm.session.isShizukuInstalled()) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                    }
                } else {
                    vm.session.requestPermission()
                }
            },
        )

        Spacer(Modifier.height(16.dp))

        // Get Started Action Button
        NeonButton(
            text = stringResource(R.string.onboarding_start),
            onClick = { vm.completeOnboarding(onFinished) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            icon = Icons.Default.ChevronRight,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionSetupCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Boolean,
    onAction: () -> Unit,
) {
    CyberCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (granted) CyberColors.Green.copy(alpha = 0.15f) else CyberColors.Cyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (granted) CyberColors.Green else CyberColors.Cyan,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary, fontSize = 15.sp)
                    if (granted) {
                        StatusBadge(text = "READY", color = CyberColors.Green)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(desc, fontSize = 11.sp, color = CyberColors.TextSecondary)
            }
            if (!granted) {
                CyberOutlinedButton(
                    text = "Grant",
                    onClick = onAction,
                    color = CyberColors.Cyan,
                )
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CyberColors.Green.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Granted", tint = CyberColors.Green, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
