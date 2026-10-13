package com.focussessions.app.timer

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pure-Kotlin countdown engine that uses [SystemClock.elapsedRealtime] for
 * drift-free accuracy.  No Android context required — fully unit-testable.
 *
 * Usage
 * ──────
 *   val engine = TimerEngine(scope)
 *   engine.start(durationMs = 25 * 60 * 1000L, onTick = { remaining -> … }, onFinish = { … })
 *   engine.pause()
 *   engine.resume()
 *   engine.cancel()
 */
class TimerEngine(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private var job: Job? = null

    /** Absolute realtime-clock deadline (ms).  Recalculated on resume. */
    private var deadlineRealtime: Long = 0L

    /** How many ms were left when we last paused. */
    private var pausedRemainingMs: Long = 0L

    private var isPaused = false
    private var totalMs  = 0L

    // ── Public surface ────────────────────────────────────────────────────────

    fun start(
        durationMs: Long,
        onTick: (remainingMs: Long) -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        cancel()
        totalMs          = durationMs
        isPaused         = false
        deadlineRealtime = SystemClock.elapsedRealtime() + durationMs
        _remainingMs.value = durationMs.coerceAtLeast(0L)

        if (durationMs <= 0L) {
            onFinish()
            return
        }

        job = scope.launch(dispatcher) {
            runLoop(onTick, onFinish)
        }
    }

    fun pause() {
        if (isPaused) return
        isPaused           = true
        pausedRemainingMs  = (deadlineRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _remainingMs.value = pausedRemainingMs
        job?.cancel()
        job = null
    }

    fun resume(
        onTick: (remainingMs: Long) -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        if (!isPaused || pausedRemainingMs <= 0L) return
        isPaused         = false
        deadlineRealtime = SystemClock.elapsedRealtime() + pausedRemainingMs
        job = scope.launch(dispatcher) {
            runLoop(onTick, onFinish)
        }
    }

    fun cancel() {
        job?.cancel()
        job    = null
        isPaused = false
    }

    fun currentRemainingMs(): Long =
        if (isPaused) pausedRemainingMs
        else (deadlineRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun isRunning() = job?.isActive == true && !isPaused

    // ── Internal loop ─────────────────────────────────────────────────────────

    private suspend fun CoroutineScope.runLoop(
        onTick: (Long) -> Unit,
        onFinish: () -> Unit
    ) {
        while (isActive) {
            val remaining = (deadlineRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            _remainingMs.value = remaining

            onTick(remaining)

            if (remaining == 0L) {
                onFinish()
                break
            }

            // Sleep until the next whole-second boundary (drift-resistant)
            val nextSecondMs = (remaining % 1000L).let { mod ->
                if (mod == 0L) 1000L else mod
            }
            delay(nextSecondMs.coerceIn(16L, 1000L))
        }
    }
}
