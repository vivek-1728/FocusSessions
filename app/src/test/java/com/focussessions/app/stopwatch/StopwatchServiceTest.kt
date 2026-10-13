package com.focussessions.app.stopwatch

import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class StopwatchServiceTest {

    @Test
    fun `service starts as foreground service and posts notification`() {
        val controller = Robolectric.buildService(StopwatchService::class.java)
        val service = controller.create().get()

        val intent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_START
            putExtra(StopwatchService.EXTRA_DURATION, 60_000L)
        }

        val startResult = service.onStartCommand(intent, 0, 1)
        assertEquals(android.app.Service.START_STICKY, startResult)

        val shadowService: ShadowService = Shadows.shadowOf(service)
        assertFalse(shadowService.isForegroundStopped)
        assertEquals(StopwatchService.NOTIF_ID, shadowService.lastForegroundNotificationId)

        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm: ShadowNotificationManager = Shadows.shadowOf(nm)
        assertNotNull(shadowNm.getNotification(StopwatchService.NOTIF_ID))

        controller.destroy()
    }

    @Test
    fun `pause and resume commands update service state`() {
        val controller = Robolectric.buildService(StopwatchService::class.java)
        val service = controller.create().get()

        // Start
        val startIntent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_START
            putExtra(StopwatchService.EXTRA_DURATION, 60_000L)
        }
        service.onStartCommand(startIntent, 0, 1)
        assertEquals(StopwatchState.RUNNING, service.state.value.state)

        // Pause
        val pauseIntent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_PAUSE
        }
        service.onStartCommand(pauseIntent, 0, 2)
        assertEquals(StopwatchState.PAUSED, service.state.value.state)

        // Resume
        val resumeIntent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_RESUME
        }
        service.onStartCommand(resumeIntent, 0, 3)
        assertEquals(StopwatchState.RUNNING, service.state.value.state)

        controller.destroy()
    }

    @Test
    fun `reset stops foreground notification and resets state to IDLE`() {
        val controller = Robolectric.buildService(StopwatchService::class.java)
        val service = controller.create().get()

        val startIntent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_START
            putExtra(StopwatchService.EXTRA_DURATION, 60_000L)
        }
        service.onStartCommand(startIntent, 0, 1)

        val resetIntent = Intent(service, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_RESET
        }
        service.onStartCommand(resetIntent, 0, 2)

        assertEquals(StopwatchState.IDLE, service.state.value.state)
        val shadowService: ShadowService = Shadows.shadowOf(service)
        assertTrue(shadowService.isForegroundStopped)

        controller.destroy()
    }
}
