package com.focussessions.app.timer

/**
 * Complete snapshot of the continuous focus-cycle at any given moment.
 * Emitted by [SessionManager] and consumed by the ViewModel → UI.
 *
 * Design notes
 * ─────────────
 * • [completedFocusSeconds] = focus seconds from fully COMPLETED sessions only.
 * • [liveCompletedFocusSeconds] adds elapsed time from the CURRENT focus session,
 *   so the overall progress bar fills smoothly in real time.
 * • Break time is tracked separately and never counts toward the target.
 */
data class CycleState(
    val phase: SessionPhase           = SessionPhase.IDLE,
    val isPaused: Boolean             = false,

    // ── Current session countdown ─────────────────────────────────────────────
    val currentSessionRemainingMs: Long = 0L,
    val currentSessionTotalMs: Long     = 0L,

    // ── Cycle progress ────────────────────────────────────────────────────────
    val targetFocusMinutes: Int   = 120,
    val focusDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,

    /** Focus seconds from already-COMPLETED focus sessions. */
    val completedFocusSeconds: Long = 0L,

    /** Break seconds from already-COMPLETED break sessions (for stats). */
    val totalBreakSeconds: Long = 0L,

    val currentCycleNumber: Int = 0,
    val totalCycles: Int        = 0
) {

    // ── Derived: current session ──────────────────────────────────────────────

    val currentSessionProgressFraction: Float
        get() = if (currentSessionTotalMs <= 0L) 0f
                else ((currentSessionTotalMs - currentSessionRemainingMs).toFloat() /
                       currentSessionTotalMs.toFloat()).coerceIn(0f, 1f)

    val displayTime: String
        get() {
            val totalSec = (currentSessionRemainingMs / 1000L).coerceAtLeast(0L)
            return "%02d:%02d".format(totalSec / 60L, totalSec % 60L)
        }

    // ── Derived: overall cycle progress ──────────────────────────────────────

    /**
     * Includes elapsed time from the current (in-progress) focus session so the
     * overall bar moves smoothly without waiting for session completion.
     */
    val liveCompletedFocusSeconds: Long
        get() = when (phase) {
            SessionPhase.FOCUS ->
                completedFocusSeconds +
                ((currentSessionTotalMs - currentSessionRemainingMs) / 1000L)
                    .coerceAtLeast(0L)
            else -> completedFocusSeconds
        }

    val liveOverallProgressFraction: Float
        get() = if (targetFocusMinutes <= 0) 0f
                else (liveCompletedFocusSeconds.toFloat() /
                      (targetFocusMinutes * 60f)).coerceIn(0f, 1f)

    val remainingFocusSeconds: Long
        get() = ((targetFocusMinutes * 60L) - liveCompletedFocusSeconds).coerceAtLeast(0L)

    val completedFocusMinutesDisplay: Int get() = (liveCompletedFocusSeconds / 60L).toInt()
    val remainingFocusMinutesDisplay: Int get() = (remainingFocusSeconds / 60L).toInt()
    val totalBreakMinutes: Int            get() = (totalBreakSeconds / 60L).toInt()

    // ── Convenience flags ─────────────────────────────────────────────────────

    val isActive: Boolean   get() = phase == SessionPhase.FOCUS || phase == SessionPhase.BREAK
    val isRunning: Boolean  get() = isActive && !isPaused
    val isIdle: Boolean     get() = phase == SessionPhase.IDLE
    val isCompleted: Boolean get() = phase == SessionPhase.COMPLETED

    fun formatRemainingFocus(): String {
        val s = remainingFocusSeconds
        val h = s / 3600L; val m = (s % 3600L) / 60L; val sec = s % 60L
        return when {
            h > 0L  -> "%dh %02dm".format(h, m)
            m > 0L  -> "%dm %02ds".format(m, sec)
            else    -> "${sec}s"
        }
    }

    fun formatCompletedFocus(): String {
        val s = liveCompletedFocusSeconds
        val h = s / 3600L; val m = (s % 3600L) / 60L
        return when {
            h > 0L  -> "%dh %02dm".format(h, m)
            else    -> "${m}m"
        }
    }
}
