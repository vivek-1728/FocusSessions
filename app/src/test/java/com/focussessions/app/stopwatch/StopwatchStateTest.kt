package com.focussessions.app.stopwatch

import org.junit.Assert.*
import org.junit.Test

class StopwatchStateTest {

    @Test
    fun `default stopwatch ui state is idle`() {
        val state = StopwatchUiState()

        assertEquals(StopwatchState.IDLE, state.state)
        assertTrue(state.isIdle)
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertFalse(state.isCompleted)
    }

    @Test
    fun `totalDurationSeconds combines hours minutes and seconds`() {
        val state = StopwatchUiState(hours = 1, minutes = 30, seconds = 15)

        assertEquals(5415L, state.totalDurationSeconds) // 3600 + 1800 + 15
    }

    @Test
    fun `progressFraction calculates accurate ratio`() {
        val state = StopwatchUiState(
            totalDurationMs = 60_000L,
            remainingMs = 15_000L
        )

        assertEquals(0.75f, state.progressFraction, 0.001f)
    }

    @Test
    fun `progressFraction handles edge cases gracefully`() {
        // Zero total duration
        val zeroTotal = StopwatchUiState(totalDurationMs = 0L, remainingMs = 0L)
        assertEquals(0f, zeroTotal.progressFraction, 0.001f)

        // Overflow / remaining negative
        val overflow = StopwatchUiState(totalDurationMs = 1000L, remainingMs = -500L)
        assertEquals(1.0f, overflow.progressFraction, 0.001f)
    }

    @Test
    fun `displayTime formats correctly without hours`() {
        val state = StopwatchUiState(remainingMs = 65_430L) // 1m 5s 430ms -> 01:05.43

        assertEquals("01:05.43", state.displayTime)
    }

    @Test
    fun `displayTime formats correctly at 59 point 999s boundary`() {
        val state = StopwatchUiState(remainingMs = 59_999L) // 59s 999ms -> 00:59.99

        assertEquals("00:59.99", state.displayTime)
    }

    @Test
    fun `displayTime formats correctly at 1h boundary`() {
        val state = StopwatchUiState(remainingMs = 3_600_000L) // 1 hour exact -> 1:00:00

        assertEquals("1:00:00", state.displayTime)
    }

    @Test
    fun `displayTime formats correctly with hours and minutes`() {
        val state = StopwatchUiState(remainingMs = 3_665_000L) // 1h 1m 5s

        assertEquals("1:01:05", state.displayTime)
    }

    @Test
    fun `displayTime formats correctly at 99h plus boundary`() {
        val state99h = StopwatchUiState(remainingMs = 99 * 3600 * 1000L) // 99 hours
        assertEquals("99:00:00", state99h.displayTime)

        val state100h = StopwatchUiState(remainingMs = 100 * 3600 * 1000L) // 100 hours
        assertEquals("100:00:00", state100h.displayTime)
    }
}
