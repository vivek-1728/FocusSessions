package com.focussessions.app.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestrates the continuous focus→break→focus loop.
 *
 * Rules
 * ──────
 * • Only FOCUS time counts toward [targetFocusMinutes].
 * • The final focus session is trimmed to exactly the remaining target time.
 * • Break time is tracked for stats but never counts toward completion.
 * • Music callbacks are fired here so the Service stays as thin as possible.
 *
 * Thread-safety: all public functions are called from a single coroutine scope
 * (the one owned by TimerService).  [_state] updates are via StateFlow which is
 * safe for cross-thread observation.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val onPhaseChanged: (SessionPhase) -> Unit    = {},
    private val onCycleCompleted: (CycleState) -> Unit    = {},
    private val onNotificationUpdate: (CycleState) -> Unit = {},
    private val onProgressTick: (sessionId: Long, liveFocusMinutes: Int, liveFocusSeconds: Long, liveBreakMinutes: Int) -> Unit = { _, _, _, _ -> }
) {

    private val engine = TimerEngine(scope)

    private val _state = MutableStateFlow(CycleState())
    val state: StateFlow<CycleState> = _state.asStateFlow()

    // ── Configuration (set before starting) ──────────────────────────────────

    var targetFocusMinutes: Int   = 120
    var focusDurationMinutes: Int = 25
    var breakDurationMinutes: Int = 5

    // ── Internal bookkeeping ──────────────────────────────────────────────────

    var activeSessionId: Long = System.currentTimeMillis()
        private set
    private var lastRecordedFocusMinute: Int = 0

    /** Focus seconds accumulated from fully-completed sessions. */
    private var completedFocusSeconds: Long = 0L
    private var totalBreakSeconds: Long     = 0L
    private var currentCycleNumber: Int     = 0

    /** Total cycles = ceil(targetFocusMinutes / focusDurationMinutes) */
    private val totalCycles: Int
        get() {
            val mins = focusDurationMinutes.coerceAtLeast(1)
            return (targetFocusMinutes + mins - 1) / mins
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Begin a fresh continuous focus cycle.
     * Resets all accumulated progress.
     */
    fun startCycle(
        targetMinutes: Int,
        focusMinutes: Int,
        breakMinutes: Int
    ) {
        targetFocusMinutes   = targetMinutes
        focusDurationMinutes = focusMinutes
        breakDurationMinutes = breakMinutes
        completedFocusSeconds = 0L
        totalBreakSeconds     = 0L
        currentCycleNumber    = 0
        lastRecordedFocusMinute = 0
        activeSessionId       = System.currentTimeMillis()
        startNextFocusSession()
    }

    /** Pause whichever phase is currently running. */
    fun pause() {
        if (_state.value.isPaused || _state.value.isCompleted) return
        engine.pause()
        _state.value = _state.value.copy(isPaused = true)
    }

    /** Resume from pause, preserving exact remaining ms. */
    fun resume() {
        if (!_state.value.isPaused || _state.value.isCompleted) return
        val phase = _state.value.phase
        engine.resume(
            onTick   = { remaining -> tickUpdate(remaining) },
            onFinish = {
                when (phase) {
                    SessionPhase.FOCUS -> onFocusSessionComplete()
                    SessionPhase.BREAK -> onBreakSessionComplete()
                    else               -> {}
                }
            }
        )
        _state.value = _state.value.copy(isPaused = false)
    }

    /**
     * Skip the current session.
     * FOCUS → immediately credit elapsed focus, start BREAK.
     * BREAK → immediately start next FOCUS.
     */
    fun skip() {
        val current = _state.value
        engine.cancel()

        when (current.phase) {
            SessionPhase.FOCUS -> {
                // Credit elapsed focus time from this session
                val elapsedFocusSec =
                    ((current.currentSessionTotalMs - engine.currentRemainingMs()) / 1000L)
                        .coerceAtLeast(0L)
                completedFocusSeconds += elapsedFocusSec

                if (isTargetReached()) {
                    enterCompleted()
                } else {
                    onPhaseChanged(SessionPhase.BREAK)
                    startBreakSession()
                }
            }
            SessionPhase.BREAK -> {
                // Credit elapsed break time for stats
                val elapsedBreakSec =
                    ((current.currentSessionTotalMs - engine.currentRemainingMs()) / 1000L)
                        .coerceAtLeast(0L)
                totalBreakSeconds += elapsedBreakSec

                onPhaseChanged(SessionPhase.FOCUS)
                startNextFocusSession()
            }
            else -> {}
        }
    }

    /** Hard reset — returns to IDLE, clears all progress. */
    fun reset() {
        engine.cancel()
        completedFocusSeconds = 0L
        totalBreakSeconds     = 0L
        currentCycleNumber    = 0
        lastRecordedFocusMinute = 0
        _state.value = CycleState(
            targetFocusMinutes   = targetFocusMinutes,
            focusDurationMinutes = focusDurationMinutes,
            breakDurationMinutes = breakDurationMinutes,
            totalCycles          = totalCycles
        )
    }

    fun cancelEngine() = engine.cancel()

    // ── Private: phase transitions ────────────────────────────────────────────

    private fun startNextFocusSession() {
        currentCycleNumber++
        activeSessionId = System.currentTimeMillis()
        lastRecordedFocusMinute = 0

        // Trim final session to exact remaining target time
        val remainingTargetSec = ((targetFocusMinutes * 60L) - completedFocusSeconds)
            .coerceAtLeast(0L)
        val sessionSec = remainingTargetSec.coerceAtMost(focusDurationMinutes * 60L)
        val sessionMs  = sessionSec * 1000L

        val newState = buildState(SessionPhase.FOCUS, sessionMs)
        _state.value = newState
        onPhaseChanged(SessionPhase.FOCUS)

        engine.start(
            durationMs = sessionMs,
            onTick     = { remaining -> tickUpdate(remaining) },
            onFinish   = { onFocusSessionComplete() }
        )
    }

    private fun startBreakSession() {
        val sessionMs = breakDurationMinutes * 60L * 1000L
        val newState  = buildState(SessionPhase.BREAK, sessionMs)
        _state.value  = newState
        onPhaseChanged(SessionPhase.BREAK)

        engine.start(
            durationMs = sessionMs,
            onTick     = { remaining -> tickUpdate(remaining) },
            onFinish   = { onBreakSessionComplete() }
        )
    }

    private fun onFocusSessionComplete() {
        // Credit the full session (trimmed duration already accounted for)
        val sessionSec = (_state.value.currentSessionTotalMs / 1000L).coerceAtLeast(0L)
        completedFocusSeconds += sessionSec

        if (isTargetReached()) {
            enterCompleted()
        } else {
            onPhaseChanged(SessionPhase.BREAK)
            startBreakSession()
        }
    }

    private fun onBreakSessionComplete() {
        val sessionSec = (_state.value.currentSessionTotalMs / 1000L).coerceAtLeast(0L)
        totalBreakSeconds += sessionSec
        onPhaseChanged(SessionPhase.FOCUS)
        startNextFocusSession()
    }

    private fun enterCompleted() {
        val finalState = _state.value.copy(
            phase                       = SessionPhase.COMPLETED,
            isPaused                    = false,
            currentSessionRemainingMs   = 0L,
            completedFocusSeconds       = completedFocusSeconds,
            totalBreakSeconds           = totalBreakSeconds
        )
        _state.value = finalState
        onPhaseChanged(SessionPhase.COMPLETED)
        onCycleCompleted(finalState)
    }

    // ── Private: helpers ──────────────────────────────────────────────────────

    private fun tickUpdate(remainingMs: Long) {
        val updated = _state.value.copy(
            currentSessionRemainingMs = remainingMs,
            completedFocusSeconds     = completedFocusSeconds,
            totalBreakSeconds         = totalBreakSeconds
        )
        _state.value = updated
        onNotificationUpdate(updated)

        if (updated.phase == SessionPhase.FOCUS) {
            val liveSec = updated.liveCompletedFocusSeconds
            val currentLiveMin = (liveSec / 60L).toInt()
            val breakMin = (updated.totalBreakSeconds / 60L).toInt()
            if (currentLiveMin > lastRecordedFocusMinute && currentLiveMin > 0) {
                lastRecordedFocusMinute = currentLiveMin
                onProgressTick(activeSessionId, currentLiveMin, liveSec, breakMin)
            }
        } else if (updated.phase == SessionPhase.BREAK) {
            val liveBreakSec = updated.totalBreakSeconds
            val currentBreakMin = (liveBreakSec / 60L).toInt()
            val focusMin = (updated.liveCompletedFocusSeconds / 60L).toInt()
            if (currentBreakMin > lastRecordedFocusMinute && currentBreakMin > 0) {
                lastRecordedFocusMinute = currentBreakMin
                onProgressTick(activeSessionId, focusMin, updated.liveCompletedFocusSeconds, currentBreakMin)
            }
        }
    }

    private fun isTargetReached(): Boolean =
        completedFocusSeconds >= targetFocusMinutes * 60L

    private fun buildState(phase: SessionPhase, sessionMs: Long): CycleState =
        CycleState(
            phase                     = phase,
            isPaused                  = false,
            currentSessionRemainingMs = sessionMs,
            currentSessionTotalMs     = sessionMs,
            targetFocusMinutes        = targetFocusMinutes,
            focusDurationMinutes      = focusDurationMinutes,
            breakDurationMinutes      = breakDurationMinutes,
            completedFocusSeconds     = completedFocusSeconds,
            totalBreakSeconds         = totalBreakSeconds,
            currentCycleNumber        = currentCycleNumber,
            totalCycles               = totalCycles
        )
}
