package com.focussessions.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.focussessions.app.MainActivity
import com.focussessions.app.timer.CycleState
import com.focussessions.app.timer.SessionManager
import com.focussessions.app.timer.SessionPhase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

@AndroidEntryPoint
class TimerService : Service() {

    companion object {
        const val CHANNEL_ID       = "focus_timer_channel"
        const val NOTIFICATION_ID  = 1001

        // Intent actions
        const val ACTION_START_CYCLE = "com.focussessions.ACTION_START_CYCLE"
        const val ACTION_PAUSE       = "com.focussessions.ACTION_PAUSE"
        const val ACTION_RESUME      = "com.focussessions.ACTION_RESUME"
        const val ACTION_SKIP        = "com.focussessions.ACTION_SKIP"
        const val ACTION_RESET       = "com.focussessions.ACTION_RESET"
        const val ACTION_STOP        = "com.focussessions.ACTION_STOP"

        // Extras
        const val EXTRA_TARGET_MIN  = "extra_target_minutes"
        const val EXTRA_FOCUS_MIN   = "extra_focus_minutes"
        const val EXTRA_BREAK_MIN   = "extra_break_minutes"
    }

    // ── Binder ────────────────────────────────────────────────────────────────

    inner class TimerBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    private val binder = TimerBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ── Coroutine scope ───────────────────────────────────────────────────────

    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    // ── Session manager ───────────────────────────────────────────────────────

    private val sessionManager = SessionManager(
        scope                = serviceScope,
        onPhaseChanged       = { phase -> handlePhaseChanged(phase) },
        onCycleCompleted     = { state -> handleCycleCompleted(state) },
        onNotificationUpdate = { state -> updateNotification(state) },
        onProgressTick       = { id, fMin, sec, bMin -> onProgressTick?.invoke(id, fMin, sec, bMin) }
    )

    val cycleState: StateFlow<CycleState> get() = sessionManager.state

    // ── Callbacks (set by ViewModel after binding) ────────────────────────────
    var onMusicStartBreak: (() -> Unit)?   = null
    var onMusicStopFocus:  (() -> Unit)?   = null
    var onCycleComplete:   ((CycleState) -> Unit)? = null
    var onProgressTick:    ((sessionId: Long, liveFocusMinutes: Int, liveFocusSeconds: Long, liveBreakMinutes: Int) -> Unit)? = null

    // ── Wake lock ─────────────────────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CYCLE -> {
                val target = intent.getIntExtra(EXTRA_TARGET_MIN, 120)
                val focus  = intent.getIntExtra(EXTRA_FOCUS_MIN,  25)
                val brk    = intent.getIntExtra(EXTRA_BREAK_MIN,  5)
                startCycle(target, focus, brk)
            }
            ACTION_PAUSE  -> pause()
            ACTION_RESUME -> resume()
            ACTION_SKIP   -> skip()
            ACTION_RESET  -> reset()
            ACTION_STOP   -> { reset(); stopSelf() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.cancelEngine()
        serviceScope.cancel()
        wakeLock?.release()
    }

    // ── Public control API (called from ViewModel via binder) ─────────────────

    fun startCycle(targetMin: Int, focusMin: Int, breakMin: Int) {
        sessionManager.startCycle(targetMin, focusMin, breakMin)
        val state = cycleState.value
        startForeground(NOTIFICATION_ID, buildNotification(state))
    }

    fun pause()  = sessionManager.pause()
    fun resume() = sessionManager.resume()
    fun skip()   = sessionManager.skip()
    fun reset() {
        sessionManager.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun configure(focusMin: Int, breakMin: Int, targetMin: Int) {
        sessionManager.focusDurationMinutes = focusMin
        sessionManager.breakDurationMinutes = breakMin
        sessionManager.targetFocusMinutes   = targetMin
    }

    // ── Phase transition handler ──────────────────────────────────────────────

    private fun handlePhaseChanged(phase: SessionPhase) {
        when (phase) {
            SessionPhase.FOCUS -> {
                // Focus = silent
                onMusicStopFocus?.invoke()
            }
            SessionPhase.BREAK -> {
                // Break = music on
                onMusicStartBreak?.invoke()
            }
            SessionPhase.COMPLETED -> {
                onMusicStopFocus?.invoke()
            }
            else -> {}
        }
    }

    private fun handleCycleCompleted(state: CycleState) {
        onCycleComplete?.invoke(state)
        updateNotification(state)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Focus Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live focus session countdown"
            setSound(null, null)
            enableVibration(false)
        }
        nm().createNotificationChannel(channel)
    }

    private fun buildNotification(state: CycleState): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).setAction(
                if (state.isPaused) ACTION_RESUME else ACTION_PAUSE
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TimerService::class.java).setAction(ACTION_SKIP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = PendingIntent.getService(
            this, 3,
            Intent(this, TimerService::class.java).setAction(ACTION_RESET),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = when {
            state.isCompleted -> Pair(
                "🎉 Target reached!",
                "${state.formatCompletedFocus()} completed · ${state.currentCycleNumber} cycles"
            )
            state.isPaused -> Pair(
                "⏸ Paused",
                "${if (state.phase == SessionPhase.FOCUS) "Focus" else "Break"}: ${state.displayTime}"
            )
            state.phase == SessionPhase.FOCUS -> Pair(
                "Focus · Cycle ${state.currentCycleNumber} of ${state.totalCycles}",
                "${state.displayTime} · ${state.formatRemainingFocus()} total left"
            )
            state.phase == SessionPhase.BREAK -> Pair(
                "Break · Relax",
                "${state.displayTime} remaining"
            )
            else -> Pair("Focus Sessions", "Ready to start")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openIntent)
            .setOngoing(state.isActive && !state.isCompleted)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                (state.targetFocusMinutes * 60).coerceAtLeast(1),
                state.liveCompletedFocusSeconds.toInt(),
                false
            )
            .apply {
                if (!state.isCompleted) {
                    addAction(
                        android.R.drawable.ic_media_pause,
                        if (state.isPaused) "Resume" else "Pause",
                        pauseResumeIntent
                    )
                    addAction(android.R.drawable.ic_media_next, "Skip",  skipIntent)
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reset", resetIntent)
                }
            }
            .build()
    }

    private fun updateNotification(state: CycleState) {
        if (state.isActive || state.isCompleted) {
            nm().notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FocusSessions:CycleWakeLock"
        ).also { it.acquire(20 * 60 * 60 * 1000L) } // up to 20 hours
    }
}
