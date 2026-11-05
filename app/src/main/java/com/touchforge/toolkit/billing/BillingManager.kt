package com.touchforge.toolkit.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.touchforge.toolkit.core.gate.Tier
import com.touchforge.toolkit.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Billing wrapper for our own two subscription tiers.
 * Original product ids and tier mapping; no dependency on any other product.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val externalScope: CoroutineScope,
) : PurchasesUpdatedListener {

    data class Product(val id: String, val tier: Tier)

    companion object {
        val PRODUCTS = listOf(
            Product("tf_pro_monthly", Tier.PRO),
            Product("tf_ultra_monthly", Tier.ULTRA),
        )
    }

    sealed interface BillingState {
        data object Disconnected : BillingState
        data object Ready : BillingState
        data class Unavailable(val code: Int, val message: String) : BillingState
    }

    private val _state = MutableStateFlow<BillingState>(BillingState.Disconnected)
    val state: StateFlow<BillingState> = _state

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = BillingState.Ready
                    externalScope.launch { queryProducts(); restore() }
                } else {
                    _state.value = BillingState.Unavailable(result.responseCode, result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = BillingState.Disconnected
            }
        })
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCTS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val (result, list) = client.queryProductDetails(params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            _products.value = list.filterIsInstance<ProductDetails>()
        }
    }

    fun launchPurchase(activity: Activity, details: ProductDetails) {
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        for (p in purchases) handle(p)
    }

    private fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val tier = PRODUCTS.firstOrNull { prod -> p.products.contains(prod.id) }?.tier ?: return
        externalScope.launch {
            if (!p.isAcknowledged) {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
                ) { }
            }
            settings.setTier(tier)
        }
    }

    /** Restores prior subscriptions from the Play account. */
    suspend fun restore() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handle(it) }
            }
        }
    }
}
