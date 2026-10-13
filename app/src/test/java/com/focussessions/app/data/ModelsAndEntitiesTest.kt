package com.focussessions.app.data

import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.domain.model.*
import com.focussessions.app.auth.UserProfile
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ModelsAndEntitiesTest {

    @Test
    fun `UserProfile serializes to and from Firestore map`() {
        val profile = UserProfile(
            uid = "user_123",
            displayName = "Alex Developer",
            email = "alex@example.com",
            photoUrl = "https://example.com/photo.jpg",
            totalFocusMinutes = 120,
            currentStreak = 5,
            lastSyncTime = 1600000000L
        )

        val map = profile.toFirestoreMap()
        val rehydrated = UserProfile.fromFirestoreMap(map)

        assertEquals(profile.uid, rehydrated.uid)
        assertEquals(profile.displayName, rehydrated.displayName)
        assertEquals(profile.email, rehydrated.email)
        assertEquals(profile.photoUrl, rehydrated.photoUrl)
        assertEquals(profile.totalFocusMinutes, rehydrated.totalFocusMinutes)
        assertEquals(profile.currentStreak, rehydrated.currentStreak)
    }

    @Test
    fun `UserPreferences defaults are valid`() {
        val prefs = UserPreferences()

        assertEquals(25, prefs.focusDurationMinutes)
        assertEquals(5, prefs.shortBreakMinutes)
        assertEquals(15, prefs.longBreakMinutes)
        assertEquals(4, prefs.sessionsBeforeLongBreak)
        assertEquals(120, prefs.dailyGoalMinutes)
        assertFalse(prefs.autoStartBreaks)
        assertFalse(prefs.autoStartFocus)
        assertFalse(prefs.musicEnabled)
    }

    @Test
    fun `FocusHistoryEntity holds accurate session parameters`() {
        val history = FocusHistoryEntity(
            sessionId = 10,
            startTime = 1000000L,
            endTime = 1001500L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Deep Work",
            dateEpochDay = LocalDate.now().toEpochDay(),
            dayOfWeek = 1,
            startHour = 9,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )

        assertEquals(10L, history.sessionId)
        assertEquals("Deep Work", history.sessionTitle)
        assertTrue(history.wasCompleted)
        assertEquals(25, history.actualFocusMinutes)
    }

    @Test
    fun `TaskEntity defaults to not completed`() {
        val task = TaskEntity(title = "Complete documentation")

        assertEquals(0L, task.id)
        assertEquals("Complete documentation", task.title)
        assertFalse(task.isCompleted)
        assertNull(task.sessionId)
    }
}
