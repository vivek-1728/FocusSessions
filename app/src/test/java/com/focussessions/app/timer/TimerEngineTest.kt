package com.focussessions.app.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TimerEngineTest {

    @Test
    fun `start countdown calculates remaining time drift-free`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var lastTickRemaining = -1L

        try {
            engine.start(
                durationMs = 10_000L,
                onTick = { remaining -> lastTickRemaining = remaining }
            )

            assertTrue(engine.isRunning())
            assertEquals(10_000L, engine.remainingMs.value)

            // Advance elapsed realtime by 3 seconds
            ShadowSystemClock.advanceBy(3000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(3000L)

            assertEquals(7000L, engine.currentRemainingMs())
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `pause freezes remaining time and resume continues from exact paused time`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var lastTickRemaining = -1L

        try {
            engine.start(
                durationMs = 10_000L,
                onTick = { remaining -> lastTickRemaining = remaining }
            )

            // Advance 2 seconds
            ShadowSystemClock.advanceBy(2000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(2000L)

            engine.pause()
            assertFalse(engine.isRunning())
            val pausedTime = engine.currentRemainingMs()
            assertEquals(8000L, pausedTime)

            // Advance time while paused — remaining time should NOT decrease
            ShadowSystemClock.advanceBy(5000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(5000L)
            assertEquals(8000L, engine.currentRemainingMs())

            // Resume and advance 3 seconds
            engine.resume(
                onTick = { remaining -> lastTickRemaining = remaining }
            )
            assertTrue(engine.isRunning())

            ShadowSystemClock.advanceBy(3000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(3000L)
            assertEquals(5000L, engine.currentRemainingMs())
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `cancel stops running job and resets state`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))

        engine.start(durationMs = 10_000L)
        assertTrue(engine.isRunning())

        engine.cancel()
        assertFalse(engine.isRunning())
    }

    @Test
    fun `simulated device sleep calculates correct remaining time after jump`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var finishCalled = false

        try {
            engine.start(
                durationMs = 60_000L,
                onFinish = { finishCalled = true }
            )

            // Simulate 70-second deep sleep jump in elapsedRealtime
            ShadowSystemClock.advanceBy(70_000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(70_000L)

            assertEquals(0L, engine.currentRemainingMs())
            assertTrue(finishCalled)
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `zero duration fires onFinish once immediately`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var finishCount = 0

        try {
            engine.start(
                durationMs = 0L,
                onFinish = { finishCount++ }
            )

            assertEquals(0L, engine.remainingMs.value)
            assertEquals(1, finishCount)
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `negative duration fires onFinish once immediately`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var finishCount = 0

        try {
            engine.start(
                durationMs = -5000L,
                onFinish = { finishCount++ }
            )

            assertEquals(1, finishCount)
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `completion callback fires exactly once at 0ms`() = runTest {
        val engine = TimerEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        var finishCount = 0

        try {
            engine.start(
                durationMs = 2000L,
                onFinish = { finishCount++ }
            )

            ShadowSystemClock.advanceBy(2000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(2000L)

            // Further clock advancement should not re-trigger completion
            ShadowSystemClock.advanceBy(5000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceTimeBy(5000L)

            assertEquals(1, finishCount)
        } finally {
            engine.cancel()
        }
    }
}
