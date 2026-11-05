package com.touchforge.toolkit.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchforge.toolkit.R
import com.touchforge.toolkit.billing.BillingManager
import com.touchforge.toolkit.core.gate.CreditLedger
import com.touchforge.toolkit.core.gate.CreditState
import com.touchforge.toolkit.core.gate.Tier
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

@Composable
fun AccountScreen(vm: AccountViewModel = hiltViewModel()) {
    val tier by vm.tier.collectAsState()
    val credits by vm.credits.collectAsState()
    val products by vm.products.collectAsState()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    // Re-render the countdown every second (claimTick is snapshot-backed state).
    val tick = vm.claimTick

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.account_tier), style = MaterialTheme.typography.titleMedium)
                Text(tier.name)
                Text(
                    stringResource(
                        R.string.account_limits,
                        com.touchforge.toolkit.core.gate.TierGate.limits(tier).maxSensitivity,
                        com.touchforge.toolkit.core.gate.TierGate.limits(tier).maxSensitivityZones,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_credits), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.credits_balance, credits.balance))
                val remaining = vm.claimRemainingSec()
                if (remaining > 0) {
                    Text(stringResource(R.string.credits_wait, remaining), style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(onClick = { vm.claim() }) { Text(stringResource(R.string.credits_claim)) }
                }
                Text(stringResource(R.string.credits_ads_note), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.account_upgrade), style = MaterialTheme.typography.titleMedium)
                if (products.isEmpty()) {
                    Text(stringResource(R.string.billing_unavailable), style = MaterialTheme.typography.bodySmall)
                }
                products.forEach { p ->
                    val price = p.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.title)
                        Button(onClick = {
                            if (activity != null) vm.billing.launchPurchase(activity, p)
                        }) { Text(price?.formattedPrice ?: stringResource(R.string.buy)) }
                    }
                }
                OutlinedButton(onClick = { vm.restorePurchases() }) { Text(stringResource(R.string.billing_restore)) }
            }
        }
    }
}
