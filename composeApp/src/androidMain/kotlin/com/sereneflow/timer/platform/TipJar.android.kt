package com.sereneflow.timer.platform

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
actual fun rememberTipStore(): TipStore {
    val activity = LocalActivity.current
    val context = LocalContext.current.applicationContext
    return remember(activity) {
        object : TipStore {
            override suspend fun products() = PlayTipStore.get(context).products()
            override suspend fun buy(product: TipProduct) =
                activity?.let { PlayTipStore.get(context).buy(it, product) } ?: TipResult.FAILED
        }
    }
}

actual fun finishPendingTipPurchases() {
    val context = AndroidAppContext.appContext ?: return
    PlayTipStore.get(context).finishOutstanding()
}

/**
 * The Google Play Billing client, one per process: a purchase still completes (and is consumed)
 * if the activity is recreated while Google's sheet is open.
 */
private class PlayTipStore(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectLock = Mutex()
    private val details = mutableMapOf<String, ProductDetails>()
    private var pendingBuy: CompletableDeferred<TipResult>? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(::onPurchasesUpdated)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    /** Connecting consumes whatever is outstanding (see [connect]). */
    fun finishOutstanding() {
        scope.launch { connect() }
    }

    suspend fun products(): List<TipProduct> {
        if (!connect()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(TipJar.productIds.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return emptyList()
        val found = result.productDetailsList.orEmpty()
        found.forEach { details[it.productId] = it }
        return TipJar.products(found.mapNotNull { product ->
            product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
        }.toMap())
    }

    suspend fun buy(activity: Activity, product: TipProduct): TipResult {
        if (!connect()) return TipResult.FAILED
        val productDetails = details[product.productId] ?: return TipResult.FAILED
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build()
            ))
            .build()
        val outcome = CompletableDeferred<TipResult>()
        pendingBuy?.complete(TipResult.CANCELLED)
        pendingBuy = outcome
        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingBuy = null
            return launch.toTipResult()
        }
        return outcome.await()
    }

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        val connected = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected.complete(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                connected.complete(false)
            }
        })
        connected.await().also { if (it) consumeOutstanding() }
    }

    /** Purchases not yet consumed: a pending payment that has since gone through, or a killed app. */
    private suspend fun consumeOutstanding() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params).purchasesList.forEach { complete(it) }
    }

    // Called on the main thread, for purchases made here and pending ones that complete later.
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val outcome = pendingBuy
        pendingBuy = null
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                scope.launch { consumeOutstanding() }
            }
            outcome?.complete(result.toTipResult())
            return
        }
        val states = purchases.orEmpty().map { it.purchaseState }
        purchases.orEmpty().forEach { scope.launch { complete(it) } }
        outcome?.complete(
            when {
                Purchase.PurchaseState.PURCHASED in states -> TipResult.SUCCESS
                Purchase.PurchaseState.PENDING in states -> TipResult.PENDING
                else -> TipResult.FAILED
            }
        )
    }

    // Counted before consuming: the purchase is real even if consuming fails, and the next
    // connection retries the consume (purchaseCompleted only sets a flag, so twice is fine).
    private suspend fun complete(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        TipJar.purchaseCompleted()
        client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }

    private fun BillingResult.toTipResult() = when (responseCode) {
        BillingClient.BillingResponseCode.OK -> TipResult.SUCCESS
        BillingClient.BillingResponseCode.USER_CANCELED -> TipResult.CANCELLED
        else -> TipResult.FAILED
    }

    companion object {
        private var instance: PlayTipStore? = null

        fun get(context: Context): PlayTipStore =
            instance ?: PlayTipStore(context.applicationContext).also { instance = it }
    }
}
