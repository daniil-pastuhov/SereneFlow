package com.sereneflow.timer

import com.sereneflow.timer.model.AppPreferences
import com.sereneflow.timer.platform.TipJar
import com.sereneflow.timer.platform.TipProduct
import com.sereneflow.timer.repository.PreferencesRepository
import com.sereneflow.timer.storage.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TipJarTest {

    private fun TestScope.prefsRepo(storage: FileStorage = FakeFileStorage()) = PreferencesRepository(
        storage, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
    )

    private fun PreferencesRepository.complete(times: Int) = repeat(times) { recordRoutineCompleted() }

    @Test
    fun bannerShowsAfterFiveCompletedRoutines() = runTest {
        val repo = prefsRepo()
        repo.complete(AppPreferences.COFFEE_PROMPT_ROUTINES - 1)
        assertFalse(repo.preferences.value.showCoffeeBanner)
        repo.complete(1)
        assertTrue(repo.preferences.value.showCoffeeBanner)
    }

    @Test
    fun closedBannerReturnsAfterFiveMore() = runTest {
        val repo = prefsRepo()
        repo.complete(AppPreferences.COFFEE_PROMPT_ROUTINES + 3)
        assertEquals(AppPreferences.COFFEE_PROMPT_ROUTINES, repo.preferences.value.routinesSinceCoffeePrompt, "Stops counting at the threshold")

        repo.dismissCoffeeBanner()
        assertFalse(repo.preferences.value.showCoffeeBanner)
        repo.complete(AppPreferences.COFFEE_PROMPT_ROUTINES - 1)
        assertFalse(repo.preferences.value.showCoffeeBanner)
        repo.complete(1)
        assertTrue(repo.preferences.value.showCoffeeBanner)
    }

    @Test
    fun donatingHidesTheBannerForGood() = runTest {
        val storage = FakeFileStorage()
        val repo = prefsRepo(storage)
        repo.complete(AppPreferences.COFFEE_PROMPT_ROUTINES)
        repo.recordDonation()
        assertFalse(repo.preferences.value.showCoffeeBanner)

        val reloaded = prefsRepo(storage)
        reloaded.complete(AppPreferences.COFFEE_PROMPT_ROUTINES * 2)
        assertTrue(reloaded.preferences.value.hasDonated)
        assertFalse(reloaded.preferences.value.showCoffeeBanner)
    }

    @Test
    fun countSurvivesReload() = runTest {
        val storage = FakeFileStorage()
        prefsRepo(storage).complete(2)
        assertEquals(2, prefsRepo(storage).preferences.value.routinesSinceCoffeePrompt)
    }

    @Test
    fun preferencesFromBeforeTheTipJarDecode() = runTest {
        val storage = FakeFileStorage().apply {
            files["preferences.json"] = """{"themeMode":"SUNSET_CLAY","voiceTipShown":true}"""
        }
        val prefs = prefsRepo(storage).preferences.value
        assertEquals(0, prefs.routinesSinceCoffeePrompt)
        assertFalse(prefs.hasDonated)
        assertTrue(prefs.voiceTipShown)
    }

    @Test
    fun productsFollowTheOfferedSizesAndSkipMissingOnes() {
        val products = TipJar.products(mapOf("coffee_5" to "€9.99", "coffee_1" to "€1.99", "other" to "€1"))
        assertEquals(
            listOf(TipProduct(1, "coffee_1", "€1.99"), TipProduct(5, "coffee_5", "€9.99")),
            products
        )
        assertEquals(listOf("coffee_1", "coffee_3", "coffee_5"), TipJar.productIds)
    }
}
