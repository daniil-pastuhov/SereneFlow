package com.sereneflow.timer

import com.sereneflow.timer.model.AppPreferences
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
class StoreReviewTest {

    private val first = AppPreferences.REVIEW_PROMPT_ROUTINES.first()
    private val second = AppPreferences.REVIEW_PROMPT_ROUTINES[1]
    private val last = AppPreferences.REVIEW_PROMPT_ROUTINES.last()

    private fun TestScope.prefsRepo(storage: FileStorage = FakeFileStorage()) = PreferencesRepository(
        storage, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
    )

    private fun PreferencesRepository.complete(times: Int) = repeat(times) { recordRoutineCompleted() }

    private val PreferencesRepository.due get() = preferences.value.reviewDue

    @Test
    fun dueOnceTheFirstCountIsReached() = runTest {
        val repo = prefsRepo()
        assertFalse(repo.due, "A new install")
        repo.complete(first - 1)
        assertFalse(repo.due)
        repo.complete(1)
        assertTrue(repo.due)
    }

    @Test
    fun staysDueUntilAskedThenWaitsForTheNextCount() = runTest {
        val repo = prefsRepo()
        repo.complete(first + 1)
        assertTrue(repo.due, "Not asked yet: the user never came back to the list")
        repo.recordReviewAsked()
        assertFalse(repo.due)
        repo.complete(second - (first + 1) - 1)
        assertFalse(repo.due)
        repo.complete(1)
        assertTrue(repo.due)
    }

    @Test
    fun countsPassedWithoutAskingAreAskedForOnce() = runTest {
        val repo = prefsRepo()
        repo.complete(second + 1)
        repo.recordReviewAsked()
        assertFalse(repo.due, "One prompt covers the first and second counts")
    }

    @Test
    fun neverDueAgainAfterTheLastCount() = runTest {
        val repo = prefsRepo()
        repo.complete(last)
        assertTrue(repo.due)
        repo.recordReviewAsked()
        repo.complete(last * 2)
        assertEquals(last, repo.preferences.value.routinesCompleted, "Stops counting at the last count")
        assertFalse(repo.due)
    }

    @Test
    fun aDonorIsStillAsked() = runTest {
        val repo = prefsRepo()
        repo.recordDonation()
        repo.complete(first)
        assertTrue(repo.due)
        assertEquals(0, repo.preferences.value.routinesSinceCoffeePrompt, "The coffee banner still doesn't count for a donor")
    }

    @Test
    fun survivesAReload() = runTest {
        val storage = FakeFileStorage()
        val repo = prefsRepo(storage)
        repo.complete(first)
        repo.recordReviewAsked()
        val reloaded = prefsRepo(storage)
        assertFalse(reloaded.due)
        assertEquals(first, reloaded.preferences.value.reviewAskedAt)
    }

    @Test
    fun preferencesFromAnOlderVersionStartCounting() {
        val old = AppPreferences(routinesSinceCoffeePrompt = 4)
        assertEquals(0, old.routinesCompleted)
        assertFalse(old.reviewDue)
    }
}
