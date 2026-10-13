package com.focussessions.app.viewmodel

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StopwatchViewModelTest {

    @Test
    fun `initial uiState has default stopwatch configuration`() = runTest {
        val viewModel = StopwatchViewModel(ApplicationProvider.getApplicationContext())

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertEquals(25, initialState.stopwatch.minutes)
            assertEquals(0, initialState.stopwatch.hours)
            assertEquals(0, initialState.stopwatch.seconds)
            assertFalse(initialState.isMusicPlaying)
            assertFalse(initialState.showCompletionOverlay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `time picker actions update hours minutes and seconds in uiState`() = runTest {
        val viewModel = StopwatchViewModel(ApplicationProvider.getApplicationContext())

        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.setHours(2)
            val itemHours = awaitItem()
            assertEquals(2, itemHours.stopwatch.hours)

            viewModel.setMinutes(15)
            val itemMinutes = awaitItem()
            assertEquals(15, itemMinutes.stopwatch.minutes)

            viewModel.setSeconds(45)
            val itemSeconds = awaitItem()
            assertEquals(45, itemSeconds.stopwatch.seconds)

            assertEquals(8145L, itemSeconds.stopwatch.totalDurationSeconds) // 2*3600 + 15*60 + 45
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectCompletionTrack and dismissCompletion update uiState`() = runTest {
        val viewModel = StopwatchViewModel(ApplicationProvider.getApplicationContext())

        assertEquals("DEFAULT_ALARM", viewModel.uiState.value.selectedCompletionUri)

        viewModel.selectCompletionTrack("content://media/external/audio/123")
        assertEquals("content://media/external/audio/123", viewModel.uiState.value.selectedCompletionUri)

        viewModel.onStopwatchComplete()
        assertTrue(viewModel.uiState.value.showCompletionOverlay)

        viewModel.dismissCompletion()
        assertFalse(viewModel.uiState.value.showCompletionOverlay)
    }
}
