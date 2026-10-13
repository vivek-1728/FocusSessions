package com.focussessions.app.timer

import org.junit.Assert.*
import org.junit.Test

class CycleStateTest {

    @Test
    fun `default cycle state has correct initial values`() {
        val state = CycleState()

        assertEquals(SessionPhase.IDLE, state.phase)
        assertFalse(state.isPaused)
        assertTrue(state.isIdle)
        assertFalse(state.isActive)
        assertFalse(state.isRunning)
        assertFalse(state.isCompleted)
    }

    @Test
    fun `displayTime formats remaining milliseconds as MM SS`() {
        val state = CycleState(currentSessionRemainingMs = 125_000L) // 2 min 5 sec

        assertEquals("02:05", state.displayTime)
    }

    @Test
    fun `currentSessionProgressFraction calculates correct ratio`() {
        val state = CycleState(
            currentSessionTotalMs = 100_000L,
            currentSessionRemainingMs = 25_000L
        )

        assertEquals(0.75f, state.currentSessionProgressFraction, 0.001f)
    }

    @Test
    fun `liveCompletedFocusSeconds includes elapsed time during FOCUS phase`() {
        val state = CycleState(
            phase = SessionPhase.FOCUS,
            completedFocusSeconds = 300L, // 5 min
            currentSessionTotalMs = 1_500_000L, // 25 min
            currentSessionRemainingMs = 1_200_000L // 20 min remaining -> 5 min elapsed
        )

        assertEquals(600L, state.liveCompletedFocusSeconds) // 5 min + 5 min = 10 min
    }

    @Test
    fun `liveOverallProgressFraction clamps to 1 when target is exceeded`() {
        val state = CycleState(
            phase = SessionPhase.IDLE,
            targetFocusMinutes = 10,
            completedFocusSeconds = 900L // 15 minutes
        )

        assertEquals(1.0f, state.liveOverallProgressFraction, 0.001f)
    }

    @Test
    fun `formatRemainingFocus handles hours and minutes formatting`() {
        val state1 = CycleState(targetFocusMinutes = 120, completedFocusSeconds = 0L)
        val state2 = CycleState(targetFocusMinutes = 45, completedFocusSeconds = 0L)

        assertEquals("2h 00m", state1.formatRemainingFocus())
        assertEquals("45m 00s", state2.formatRemainingFocus())
    }

    @Test
    fun `formatCompletedFocus formats completed time correctly`() {
        val state1 = CycleState(completedFocusSeconds = 3600L) // 1h
        val state2 = CycleState(completedFocusSeconds = 1200L) // 20m

        assertEquals("1h 00m", state1.formatCompletedFocus())
        assertEquals("20m", state2.formatCompletedFocus())
    }
}
