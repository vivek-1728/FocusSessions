package com.focussessions.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.focussessions.app.data.local.database.AppDatabase
import com.focussessions.app.data.local.datastore.UserPreferencesDataStore
import com.focussessions.app.domain.model.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class RepositoriesTest {

    private lateinit var database: AppDatabase
    private lateinit var dataStore: UserPreferencesDataStore

    private lateinit var taskRepository: TaskRepository
    private lateinit var focusSessionRepository: FocusSessionRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dataStore = UserPreferencesDataStore(ApplicationProvider.getApplicationContext())
        dataStore.clear()

        taskRepository = TaskRepository(database.taskDao())
        focusSessionRepository = FocusSessionRepository(
            database.focusSessionDao(),
            database.dailyProgressDao(),
            database.focusHistoryDao(),
            database.calendarDao()
        )
        userPreferencesRepository = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── TaskRepository Offline-First Tests ────────────────────────────────────

    @Test
    fun `TaskRepository writes hit Room immediately and reflect in Flow emissions`() = runTest {
        taskRepository.allTasks.test {
            assertEquals(emptyList<com.focussessions.app.domain.model.Task>(), awaitItem())

            // Add task
            val taskId = taskRepository.addTask("Task 1")
            assertTrue(taskId > 0)

            val tasks1 = awaitItem()
            assertEquals(1, tasks1.size)
            assertEquals("Task 1", tasks1[0].title)
            assertFalse(tasks1[0].isCompleted)

            // Toggle task
            taskRepository.toggleTask(tasks1[0])
            val tasks2 = awaitItem()
            assertTrue(tasks2[0].isCompleted)

            // Delete task
            taskRepository.deleteTask(taskId)
            val tasks3 = awaitItem()
            assertTrue(tasks3.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TaskRepository activeTasks emits only incomplete tasks`() = runTest {
        val id1 = taskRepository.addTask("Task A")
        val id2 = taskRepository.addTask("Task B")

        taskRepository.activeTasks.test {
            val active1 = awaitItem()
            assertEquals(2, active1.size)

            // Toggle task A
            val taskA = active1.first { it.id == id1 }
            taskRepository.toggleTask(taskA)

            val active2 = awaitItem()
            assertEquals(1, active2.size)
            assertEquals(id2, active2[0].id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── FocusSessionRepository Offline-First Tests ─────────────────────────────

    @Test
    fun `recordSession writes to FocusSessionDao and DailyProgressDao immediately`() = runTest {
        focusSessionRepository.recordSession(durationMinutes = 25, wasCompleted = true)

        focusSessionRepository.getTodayProgress().test {
            val progress = awaitItem()
            assertNotNull(progress)
            assertEquals(25, progress?.focusMinutes)
            assertEquals(1, progress?.sessionsCount)
            cancelAndIgnoreRemainingEvents()
        }

        // Add second completed session today
        focusSessionRepository.recordSession(durationMinutes = 35, wasCompleted = true)

        focusSessionRepository.getTodayProgress().test {
            val progress = awaitItem()
            assertNotNull(progress)
            assertEquals(60, progress?.focusMinutes)
            assertEquals(2, progress?.sessionsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recordFullSession writes to FocusHistoryDao and CalendarDao immediately`() = runTest {
        val now = System.currentTimeMillis()
        focusSessionRepository.recordFullSession(
            startTime = now - 1500000L,
            focusMinutes = 25,
            breakMinutes = 5,
            cycles = 1,
            wasCompleted = true,
            title = "Deep Focus"
        )

        focusSessionRepository.getHistory().test {
            val history = awaitItem()
            assertEquals(1, history.size)
            assertEquals("Deep Focus", history[0].sessionTitle)
            assertEquals(25, history[0].actualFocusMinutes)
            cancelAndIgnoreRemainingEvents()
        }

        val todayEpoch = LocalDate.now().toEpochDay()
        focusSessionRepository.getCalendarRange(todayEpoch, todayEpoch).test {
            val days = awaitItem()
            assertEquals(1, days.size)
            assertEquals(25, days[0].totalFocusMinutes)
            assertEquals(1, days[0].sessionCount)
            assertEquals(25, days[0].longestSessionMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── UserPreferencesRepository Tests ─────────────────────────────────────

    @Test
    fun `UserPreferencesRepository setters delegate to DataStore and update Flow`() = runTest {
        userPreferencesRepository.setFocusDuration(40)
        userPreferencesRepository.setShortBreak(8)
        userPreferencesRepository.setLongBreak(20)
        userPreferencesRepository.setSessionsBeforeLongBreak(3)
        userPreferencesRepository.setDailyGoal(150)
        userPreferencesRepository.setAutoStartBreaks(true)
        userPreferencesRepository.setAutoStartFocus(true)
        userPreferencesRepository.setMusicEnabled(true)
        userPreferencesRepository.setSelectedMusicUri("uri://ambient")
        userPreferencesRepository.setNotificationsEnabled(false)
        userPreferencesRepository.setStreakCount(10)
        userPreferencesRepository.setLastActiveDate("2025-01-20")

        userPreferencesRepository.preferences.test {
            val prefs = awaitItem()
            assertEquals(40, prefs.focusDurationMinutes)
            assertEquals(8, prefs.shortBreakMinutes)
            assertEquals(20, prefs.longBreakMinutes)
            assertEquals(3, prefs.sessionsBeforeLongBreak)
            assertEquals(150, prefs.dailyGoalMinutes)
            assertTrue(prefs.autoStartBreaks)
            assertTrue(prefs.autoStartFocus)
            assertTrue(prefs.musicEnabled)
            assertEquals("uri://ambient", prefs.selectedMusicUri)
            assertFalse(prefs.notificationsEnabled)
            assertEquals(10, prefs.streakCount)
            assertEquals("2025-01-20", prefs.lastActiveDate)
            cancelAndIgnoreRemainingEvents()
        }

        val updated = UserPreferences(focusDurationMinutes = 55, dailyGoalMinutes = 300)
        userPreferencesRepository.updateAll(updated)

        userPreferencesRepository.preferences.test {
            val prefs = awaitItem()
            assertEquals(55, prefs.focusDurationMinutes)
            assertEquals(300, prefs.dailyGoalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
