package com.sereneflow.timer.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A product as StoreKit returns it: its ID and localized price. */
class TipProductInfo(val id: String, val displayPrice: String)

/**
 * Implemented in Swift (`TipStoreManager` in iosApp) and registered in [TipStoreRegistry] at launch:
 * StoreKit 2 has no Objective-C API. Callbacks come on the main thread.
 */
interface TipStoreBridge {
    fun loadProducts(ids: List<String>, onDone: (List<TipProductInfo>) -> Unit)

    fun purchase(id: String, onDone: (TipResult) -> Unit)
}

object TipStoreRegistry {
    var bridge: TipStoreBridge? = null
}

@Composable
actual fun rememberTipStore(): TipStore = StoreKitTipStore

// TipStoreManager listens to StoreKit's unfinished and updated transactions from launch.
actual fun finishPendingTipPurchases() {}

private object StoreKitTipStore : TipStore {
    override suspend fun products(): List<TipProduct> {
        val bridge = TipStoreRegistry.bridge ?: return emptyList()
        val loaded = suspendCancellableCoroutine { continuation ->
            bridge.loadProducts(TipJar.productIds) { continuation.resume(it) }
        }
        return TipJar.products(loaded.associate { it.id to it.displayPrice })
    }

    override suspend fun buy(product: TipProduct): TipResult {
        val bridge = TipStoreRegistry.bridge ?: return TipResult.FAILED
        return suspendCancellableCoroutine { continuation ->
            bridge.purchase(product.productId) { continuation.resume(it) }
        }
    }
}
