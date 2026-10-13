package com.focussessions.app.stopwatch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
class StopwatchEngineTest {

    @Test
    fun `start runs countdown with 16ms ticks`() = runTest {
        val engine = StopwatchEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        val ticks = mutableListOf<Long>()

        try {
            engine.start(
                durationMs = 100L,
                onTick = { rem -> ticks.add(rem) },
                onFinish = {}
            )
            testScheduler.advanceUntilIdle()

            assertTrue(ticks.isNotEmpty())
            assertEquals(100L, ticks.first())
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `drift-free over long runs using SystemClock elapsedRealtime`() = runTest {
        val engine = StopwatchEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))

        try {
            engine.start(
                durationMs = 3_600_000L, // 1 hour
                onTick = {},
                onFinish = {}
            )
            testScheduler.advanceUntilIdle()

            // Jump clock by 30 minutes (1,800,000 ms)
            ShadowSystemClock.advanceBy(1_800_000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceUntilIdle()

            assertEquals(1_800_000L, engine.currentMs())
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `pause freezes remaining time and resume continues from paused time`() = runTest {
        val engine = StopwatchEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))

        try {
            engine.start(
                durationMs = 10_000L,
                onTick = {},
                onFinish = {}
            )
            testScheduler.advanceUntilIdle()

            // Advance 3 seconds
            ShadowSystemClock.advanceBy(3000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceUntilIdle()

            engine.pause()
            assertEquals(7000L, engine.currentMs())

            // Time elapses while paused
            ShadowSystemClock.advanceBy(5000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceUntilIdle()

            assertEquals(7000L, engine.currentMs())

            // Resume and advance 2 seconds
            engine.resume(onTick = {}, onFinish = {})
            ShadowSystemClock.advanceBy(2000L, TimeUnit.MILLISECONDS)
            testScheduler.advanceUntilIdle()

            assertEquals(5000L, engine.currentMs())
        } finally {
            engine.cancel()
        }
    }

    @Test
    fun `cancel stops loop and clears active state`() = runTest {
        val engine = StopwatchEngine(backgroundScope, UnconfinedTestDispatcher(testScheduler))

        engine.start(durationMs = 10_000L, onTick = {}, onFinish = {})
        testScheduler.advanceUntilIdle()

        engine.cancel()
        ShadowSystemClock.advanceBy(5000L, TimeUnit.MILLISECONDS)
        testScheduler.advanceUntilIdle()

        // Remainder should stay at initial value
        assertEquals(10_000L, engine.remainingMs.value)
    }
}
