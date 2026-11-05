package com.touchforge.toolkit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.touchforge.toolkit.billing.BillingManager
import com.touchforge.toolkit.core.gate.CreditLedger
import com.touchforge.toolkit.core.gate.CreditState
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.core.gate.TierGate
import com.touchforge.toolkit.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val settings: SettingsRepository,
    val billing: BillingManager,
) : ViewModel() {

    private val ledger = CreditLedger()

    val tier: StateFlow<Tier> = settings.tier.stateIn(viewModelScope, SharingStarted.Eagerly, Tier.FREE)
    val credits: StateFlow<CreditState> = settings.credits.stateIn(viewModelScope, SharingStarted.Eagerly, CreditState())
    val products = billing.products

    var claimTick by mutableLongStateOf(0)
        private set

    init {
        billing.connect()
        viewModelScope.launch {
            while (isActive) {
                claimTick++
                delay(1_000)
            }
        }
    }

    fun claimRemainingSec(): Long = ledger.claimIn(credits.value) / 1000

    fun claim() = viewModelScope.launch {
        ledger.claim(credits.value)?.let { settings.setCredits(it) }
    }

    fun restorePurchases() = viewModelScope.launch { billing.restore() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    vm: AccountViewModel = hiltViewModel(),
) {
    val tier by vm.tier.collectAsState()
    val credits by vm.credits.collectAsState()
    val products by vm.products.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    val tick = vm.claimTick

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tab_account), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("TouchForge Identity & Subscriptions", fontSize = 12.sp, color = CyberColors.TextSecondary)
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
            // Identity Hero Card
            CyberCard(
                title = "TouchForge Tier: ${tier.name}",
                subtitle = "Active License Status",
                icon = Icons.Default.Shield,
                iconTint = if (tier == Tier.FREE) CyberColors.Cyan else CyberColors.Amber,
                badgeText = tier.name,
                badgeColor = if (tier == Tier.FREE) CyberColors.Cyan else CyberColors.Amber,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile(
                        label = "Max Sensitivity",
                        value = "%.1fx".format(TierGate.limits(tier).maxSensitivity),
                        accentColor = CyberColors.Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Max Touch Zones",
                        value = "${TierGate.limits(tier).maxSensitivityZones}",
                        accentColor = CyberColors.Teal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Credit Energy Ledger Card
            CyberCard(
                title = stringResource(R.string.account_credits),
                subtitle = "Energy points for macro executions & premium actions",
                icon = Icons.Default.Bolt,
                iconTint = CyberColors.Amber,
                badgeText = "${credits.balance} CREDITS",
                badgeColor = CyberColors.Amber,
            ) {
                val remaining = vm.claimRemainingSec()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Current Balance: ${credits.balance}", fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                        Text(
                            if (remaining > 0) "Next recharge available in ${remaining}s" else "Recharge ready to claim!",
                            fontSize = 11.sp,
                            color = if (remaining > 0) CyberColors.TextSecondary else CyberColors.Green,
                        )
                    }
                    NeonButton(
                        text = stringResource(R.string.credits_claim),
                        onClick = { vm.claim() },
                        enabled = remaining <= 0,
                        color = CyberColors.Amber,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.credits_ads_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.TextSecondary,
                )
            }

            // Tier Comparison Matrix
            CyberCard(title = "Membership Tiers") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TierComparisonRow(
                        tierName = "FREE",
                        maxSens = "2.0x",
                        zones = "1 Zone",
                        features = "Basic HUD · Classic Crosshair",
                        isCurrent = tier == Tier.FREE,
                    )
                    TierComparisonRow(
                        tierName = "PRO",
                        maxSens = "3.5x",
                        zones = "5 Zones",
                        features = "Aim Curves · Triggers · All Crosshairs",
                        isCurrent = tier == Tier.PRO,
                    )
                    TierComparisonRow(
                        tierName = "ULTRA",
                        maxSens = "5.0x",
                        zones = "Unlimited",
                        features = "Macro Studio · Key Mapping · Priority Engine",
                        isCurrent = tier == Tier.ULTRA,
                    )
                }
            }

            // Google Play Subscriptions Card
            CyberCard(
                title = "Upgrade to Pro / Ultra",
                subtitle = "Unlock maximum sensitivity limits and advanced studios",
                icon = Icons.Default.Diamond,
            ) {
                if (products.isEmpty()) {
                    Text(
                        stringResource(R.string.billing_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.TextSecondary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.forEach { p ->
                            val price = p.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
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
                                        Text(p.title, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                                        Text(p.description, fontSize = 11.sp, color = CyberColors.TextSecondary)
                                    }
                                    NeonButton(
                                        text = price?.formattedPrice ?: stringResource(R.string.buy),
                                        onClick = {
                                            if (activity != null) vm.billing.launchPurchase(activity, p)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                CyberOutlinedButton(
                    text = stringResource(R.string.billing_restore),
                    onClick = { vm.restorePurchases() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TierComparisonRow(
    tierName: String,
    maxSens: String,
    zones: String,
    features: String,
    isCurrent: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) CyberColors.SurfaceVariant else CyberColors.Surface,
        border = BorderStroke(1.dp, if (isCurrent) CyberColors.Cyan else CyberColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) CyberColors.Cyan.copy(alpha = 0.2f) else CyberColors.Border),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isCurrent) CyberColors.Cyan else CyberColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tierName, fontWeight = FontWeight.Bold, color = CyberColors.TextPrimary)
                    if (isCurrent) {
                        StatusBadge(text = "CURRENT", color = CyberColors.Cyan)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("Sens up to $maxSens · $zones", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = CyberColors.Teal)
                Text(features, fontSize = 10.sp, color = CyberColors.TextSecondary)
            }
        }
    }
}
