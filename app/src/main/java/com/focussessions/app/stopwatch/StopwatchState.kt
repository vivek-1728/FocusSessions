package com.focussessions.app.stopwatch

enum class StopwatchState { IDLE, RUNNING, PAUSED, COMPLETED }

data class StopwatchUiState(
    val state: StopwatchState         = StopwatchState.IDLE,
    val totalDurationMs: Long         = 0L,
    val remainingMs: Long             = 0L,
    val hours: Int                    = 0,
    val minutes: Int                  = 25,
    val seconds: Int                  = 0,
    val musicStartedOnComplete: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalDurationMs <= 0L) 0f
                else ((totalDurationMs - remainingMs).toFloat() / totalDurationMs).coerceIn(0f, 1f)

    val displayTime: String
        get() {
            val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
            val ms       = (remainingMs % 1000L) / 10L
            val h = totalSec / 3600L
            val m = (totalSec % 3600L) / 60L
            val s = totalSec % 60L
            return if (h > 0L)
                "%d:%02d:%02d".format(h, m, s)
            else
                "%02d:%02d.%02d".format(m, s, ms)
        }

    val totalDurationSeconds: Long get() = hours * 3600L + minutes * 60L + seconds.toLong()

    val isIdle: Boolean      get() = state == StopwatchState.IDLE
    val isRunning: Boolean   get() = state == StopwatchState.RUNNING
    val isPaused: Boolean    get() = state == StopwatchState.PAUSED
    val isCompleted: Boolean get() = state == StopwatchState.COMPLETED
}
