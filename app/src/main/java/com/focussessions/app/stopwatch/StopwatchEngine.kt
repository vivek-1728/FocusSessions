package com.focussessions.app.stopwatch

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drift-free countdown engine for the custom stopwatch.
 * Uses [SystemClock.elapsedRealtime] — immune to delay() jitter.
 * Ticks every 16 ms for smooth ms-precision display.
 */
class StopwatchEngine(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    private var job: Job?   = null
    private var deadline: Long = 0L
    private var pausedMs: Long = 0L
    private var paused = false

    fun start(durationMs: Long, onTick: (Long) -> Unit, onFinish: () -> Unit) {
        cancel()
        paused   = false
        pausedMs = durationMs
        deadline = SystemClock.elapsedRealtime() + durationMs
        _remainingMs.value = durationMs
        job = scope.launch(dispatcher) { runLoop(onTick, onFinish) }
    }

    fun pause() {
        if (paused) return
        paused   = true
        pausedMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _remainingMs.value = pausedMs
        job?.cancel(); job = null
    }

    fun resume(onTick: (Long) -> Unit, onFinish: () -> Unit) {
        if (!paused || pausedMs <= 0L) return
        paused   = false
        deadline = SystemClock.elapsedRealtime() + pausedMs
        job = scope.launch(dispatcher) { runLoop(onTick, onFinish) }
    }

    fun cancel() { job?.cancel(); job = null; paused = false }

    fun currentMs(): Long = if (paused) pausedMs
                            else (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private suspend fun CoroutineScope.runLoop(onTick: (Long) -> Unit, onFinish: () -> Unit) {
        while (isActive) {
            val rem = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            _remainingMs.value = rem
            onTick(rem)
            if (rem == 0L) { onFinish(); break }
            delay(16L)   // ~60 fps for smooth ms display
        }
    }
}
