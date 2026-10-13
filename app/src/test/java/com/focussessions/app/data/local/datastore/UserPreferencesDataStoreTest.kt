package com.focussessions.app.data.local.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.focussessions.app.domain.model.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class UserPreferencesDataStoreTest {

    private lateinit var context: Context
    private lateinit var dataStore: UserPreferencesDataStore

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        dataStore = UserPreferencesDataStore(context)
        dataStore.clear()
    }

    @Test
    fun `default userPreferences match expected domain defaults`() = runTest {
        dataStore.userPreferences.test {
            val prefs = awaitItem()
            assertEquals(25, prefs.focusDurationMinutes)
            assertEquals(5, prefs.shortBreakMinutes)
            assertEquals(15, prefs.longBreakMinutes)
            assertEquals(4, prefs.sessionsBeforeLongBreak)
            assertEquals(120, prefs.dailyGoalMinutes)
            assertFalse(prefs.autoStartBreaks)
            assertFalse(prefs.autoStartFocus)
            assertFalse(prefs.musicEnabled)
            assertEquals("", prefs.selectedMusicUri)
            assertTrue(prefs.notificationsEnabled)
            assertEquals(0, prefs.streakCount)
            assertEquals("", prefs.lastActiveDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every individual preference setter updates DataStore successfully`() = runTest {
        dataStore.setFocusDuration(30)
        dataStore.setShortBreak(10)
        dataStore.setLongBreak(20)
        dataStore.setSessionsBeforeLongBreak(3)
        dataStore.setDailyGoal(180)
        dataStore.setAutoStartBreaks(true)
        dataStore.setAutoStartFocus(true)
        dataStore.setMusicEnabled(true)
        dataStore.setSelectedMusicUri("content://media/external/audio/1")
        dataStore.setNotificationsEnabled(false)
        dataStore.setStreakCount(7)
        dataStore.setLastActiveDate("2025-01-15")

        dataStore.userPreferences.test {
            val prefs = awaitItem()
            assertEquals(30, prefs.focusDurationMinutes)
            assertEquals(10, prefs.shortBreakMinutes)
            assertEquals(20, prefs.longBreakMinutes)
            assertEquals(3, prefs.sessionsBeforeLongBreak)
            assertEquals(180, prefs.dailyGoalMinutes)
            assertTrue(prefs.autoStartBreaks)
            assertTrue(prefs.autoStartFocus)
            assertTrue(prefs.musicEnabled)
            assertEquals("content://media/external/audio/1", prefs.selectedMusicUri)
            assertFalse(prefs.notificationsEnabled)
            assertEquals(7, prefs.streakCount)
            assertEquals("2025-01-15", prefs.lastActiveDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateAllPreferences batch updates all fields`() = runTest {
        val customPrefs = UserPreferences(
            focusDurationMinutes = 45,
            shortBreakMinutes = 8,
            longBreakMinutes = 25,
            sessionsBeforeLongBreak = 5,
            dailyGoalMinutes = 240,
            autoStartBreaks = true,
            autoStartFocus = false,
            musicEnabled = true,
            selectedMusicUri = "file:///music/lofi.mp3",
            notificationsEnabled = true,
            streakCount = 12,
            lastActiveDate = "2025-02-01"
        )

        dataStore.updateAllPreferences(customPrefs)

        dataStore.userPreferences.test {
            val prefs = awaitItem()
            assertEquals(customPrefs, prefs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preferences persist across new DataStore instance re-creation`() = runTest {
        // Write preferences with initial instance
        dataStore.setFocusDuration(50)
        dataStore.setStreakCount(15)
        dataStore.setLastActiveDate("2025-03-10")

        // Create a new instance of UserPreferencesDataStore targeting the same context
        val newStoreInstance = UserPreferencesDataStore(context)

        newStoreInstance.userPreferences.test {
            val prefs = awaitItem()
            assertEquals(50, prefs.focusDurationMinutes)
            assertEquals(15, prefs.streakCount)
            assertEquals("2025-03-10", prefs.lastActiveDate)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
