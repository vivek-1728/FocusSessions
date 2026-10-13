package com.focussessions.app.stopwatch

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.focussessions.app.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class StopwatchService : Service() {

    companion object {
        const val CHANNEL_ID          = "stopwatch_channel"
        const val COMPLETE_CHANNEL_ID = "stopwatch_complete_channel"
        const val NOTIF_ID            = 2001
        const val ACTION_START        = "sw.START"
        const val ACTION_PAUSE        = "sw.PAUSE"
        const val ACTION_RESUME       = "sw.RESUME"
        const val ACTION_RESET        = "sw.RESET"
        const val EXTRA_DURATION      = "sw.duration_ms"
    }

    inner class SwBinder : Binder() { fun get(): StopwatchService = this@StopwatchService }
    private val binder = SwBinder()
    override fun onBind(i: Intent): IBinder = binder

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val engine = StopwatchEngine(scope)

    private val _state = MutableStateFlow(StopwatchUiState())
    val state: StateFlow<StopwatchUiState> = _state.asStateFlow()

    var onCompleted: (() -> Unit)? = null
    private var totalMs = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> {
                totalMs = intent.getLongExtra(EXTRA_DURATION, 0L)
                startEngine(totalMs)
            }
            ACTION_PAUSE  -> pause()
            ACTION_RESUME -> resume()
            ACTION_RESET  -> reset()
        }
        return START_STICKY
    }

    fun startEngine(durationMs: Long) {
        totalMs = durationMs
        _state.value = StopwatchUiState(
            state           = StopwatchState.RUNNING,
            totalDurationMs = durationMs,
            remainingMs     = durationMs
        )
        startForeground(NOTIF_ID, buildNotif("Stopwatch running", durationMs))
        engine.start(durationMs, ::onTick, ::onFinish)
    }

    fun pause() {
        engine.pause()
        _state.value = _state.value.copy(state = StopwatchState.PAUSED)
        updateNotif("Stopwatch paused", _state.value.remainingMs)
    }

    fun resume() {
        engine.resume(::onTick, ::onFinish)
        _state.value = _state.value.copy(state = StopwatchState.RUNNING)
    }

    fun reset() {
        engine.cancel()
        _state.value = StopwatchUiState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.cancel()
        scope.cancel()
    }

    private fun onTick(rem: Long) {
        if (_state.value.state != StopwatchState.RUNNING) return
        _state.value = _state.value.copy(remainingMs = rem)
        if (rem % 1000L < 50L) updateNotif("Stopwatch: ${formatMs(rem)}", rem)
    }

    private fun onFinish() {
        _state.value = _state.value.copy(
            state       = StopwatchState.COMPLETED,
            remainingMs = 0L,
            musicStartedOnComplete = true
        )
        nm().notify(NOTIF_ID, buildCompletionNotif())
        onCompleted?.invoke()
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    private fun createChannel() {
        NotificationChannel(CHANNEL_ID, "Stopwatch Running", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            nm().createNotificationChannel(this)
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        NotificationChannel(COMPLETE_CHANNEL_ID, "Stopwatch Completed", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(soundUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            nm().createNotificationChannel(this)
        }
    }

    private fun buildNotif(title: String, remainingMs: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("tab", "stopwatch"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StopwatchService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(formatMs(remainingMs))
            .setContentIntent(open)
            .setSilent(true).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(totalMs.toInt(), (totalMs - remainingMs).toInt(), false)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .build()
    }

    private fun buildCompletionNotif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("tab", "stopwatch"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        return NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏱ Time completed")
            .setContentText("Your stopwatch countdown finished — ringing music started")
            .setContentIntent(open)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    private fun updateNotif(title: String, rem: Long) =
        nm().notify(NOTIF_ID, buildNotif(title, rem))

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val h = s / 3600L; val m = (s % 3600) / 60L; val sec = s % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, sec)
               else        "%02d:%02d".format(m, sec)
    }
}
