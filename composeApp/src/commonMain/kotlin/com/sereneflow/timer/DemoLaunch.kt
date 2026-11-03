package com.sereneflow.timer

import com.sereneflow.timer.platform.TipJar
import com.sereneflow.timer.platform.TipProduct
import com.sereneflow.timer.platform.TipResult
import com.sereneflow.timer.platform.TipStore

/**
 * Opens a screen straight away instead of the routine list, for the store screenshot scripts
 * (scripts/ios_screenshots.sh, scripts/android_screenshots.sh). Only debug builds read it: from
 * launch arguments on iOS (MainViewController) and intent extras on Android (SereneFlowActivity).
 */
sealed class DemoLaunch {
    /**
     * Runs [routineId], [elapsedSeconds] into its first step. [backgroundMode] also mirrors it
     * outside the app (notification, Live Activity; the watch app follows either way), for checking
     * those in a simulator;
     * screenshots leave it off.
     */
    data class Timer(val routineId: String, val elapsedSeconds: Int, val backgroundMode: Boolean = false) : DemoLaunch()
    data class Editor(val routineId: String) : DemoLaunch()
    data object Settings : DemoLaunch()
    /** The tip jar with [DemoTipStore]'s prices, as a simulator has no store products to show. */
    data object TipJar : DemoLaunch()

    companion object {
        /** `screen` is "timer", "editor", "settings" or "tipjar"; anything else opens the routine list. */
        fun parse(
            screen: String?,
            routineId: String?,
            elapsedSeconds: Int?,
            backgroundMode: Boolean = false
        ): DemoLaunch? = when (screen) {
            "timer" -> routineId?.let { Timer(it, elapsedSeconds ?: 0, backgroundMode) }
            "editor" -> routineId?.let { Editor(it) }
            "settings" -> Settings
            "tipjar" -> TipJar
            else -> null
        }
    }
}

/** The euro prices (one cup is €2), for screenshots of the tip jar; buying does nothing. */
object DemoTipStore : TipStore {
    override suspend fun products(): List<TipProduct> =
        TipJar.products(TipJar.cups.associate { TipJar.productId(it) to "€${2 * it - 1}.99" })

    override suspend fun buy(product: TipProduct): TipResult = TipResult.CANCELLED
}
