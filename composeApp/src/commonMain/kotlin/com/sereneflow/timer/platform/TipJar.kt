package com.sereneflow.timer.platform

import androidx.compose.runtime.Composable
import com.sereneflow.timer.repository.Repositories

/** One size of tip: [cups] coffees at the store's localized [price] (e.g. "€6.00"). */
data class TipProduct(val cups: Int, val productId: String, val price: String)

enum class TipResult { SUCCESS, CANCELLED, PENDING, FAILED }

/**
 * Tips go through the stores' in-app purchase (Google Play Billing, StoreKit), as both require for
 * tips to the developer. Each size is a consumable product, so it can be bought again.
 */
interface TipStore {
    /** The sizes the store offers, smallest first; empty when the store can't be reached. */
    suspend fun products(): List<TipProduct>

    suspend fun buy(product: TipProduct): TipResult
}

/** The platform's store; Android's purchase sheet needs the composition's activity. */
@Composable
expect fun rememberTipStore(): TipStore

/**
 * Finishes purchases that completed while the tip jar wasn't open (a pending payment that went
 * through later), called at launch: Play refunds one that isn't consumed within 3 days. iOS does
 * this from launch in Swift (TipStoreManager), so it does nothing there.
 */
expect fun finishPendingTipPurchases()

object TipJar {
    /** The sizes offered; a cup is €2, and the stores round the price per country. */
    val cups = listOf(1, 3, 5)

    /** The same product IDs in App Store Connect and Play Console. */
    fun productId(cups: Int): String = "coffee_$cups"

    val productIds: List<String> get() = cups.map(::productId)

    /** The offered sizes the store returned a price for, from product ID → localized price. */
    fun products(prices: Map<String, String>): List<TipProduct> =
        cups.mapNotNull { cups -> prices[productId(cups)]?.let { TipProduct(cups, productId(cups), it) } }

    /**
     * Called by the platform for every verified purchase, including ones completed later (Ask to
     * Buy, a pending payment) or left unfinished by an earlier run. Ends the coffee banner for good.
     */
    fun purchaseCompleted() {
        Repositories.preferences.recordDonation()
    }
}
