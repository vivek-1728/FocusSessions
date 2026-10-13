package com.focussessions.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FocusSessionsApp : Application(), Configuration.Provider {

    /** Injected by Hilt — needed for @HiltWorker in SyncWorker. */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationChannel("focus_timer_channel", "Focus Timer",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows focus session progress"
            setSound(null, null)
            nm.createNotificationChannel(this)
        }

        NotificationChannel("focus_completion_channel", "Session Complete",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a focus session finishes"
            nm.createNotificationChannel(this)
        }

        NotificationChannel("stopwatch_channel", "Stopwatch",
            NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            nm.createNotificationChannel(this)
        }

        NotificationChannel("sync_channel", "Sync",
            NotificationManager.IMPORTANCE_MIN).apply {
            description = "Background sync notifications"
            nm.createNotificationChannel(this)
        }
    }
}
