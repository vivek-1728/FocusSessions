package com.focussessions.app.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.focussessions.app.data.local.database.dao.*
import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.sync.SyncQueueEntity
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
class RoomDaosTest {

    private lateinit var database: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var sessionDao: FocusSessionDao
    private lateinit var dailyProgressDao: DailyProgressDao
    private lateinit var historyDao: FocusHistoryDao
    private lateinit var calendarDao: CalendarDao
    private lateinit var analyticsDao: AnalyticsDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        taskDao = database.taskDao()
        sessionDao = database.focusSessionDao()
        dailyProgressDao = database.dailyProgressDao()
        historyDao = database.focusHistoryDao()
        calendarDao = database.calendarDao()
        analyticsDao = database.analyticsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── AppDatabase Creation & Migration / Schema Integrity ─────────────────

    @Test
    fun `AppDatabase creates successfully with expected schema version`() {
        assertNotNull(database)
        assertEquals(3, database.openHelper.readableDatabase.version)
    }

    // ── Entity Defaults Verification ─────────────────────────────────────────

    @Test
    fun `Entity defaults are initialized with correct default values`() {
        val task = TaskEntity(title = "Default Task")
        assertEquals(0L, task.id)
        assertEquals("Default Task", task.title)
        assertFalse(task.isCompleted)
        assertNull(task.sessionId)
        assertTrue(task.createdAt > 0)

        val session = FocusSessionEntity(durationMinutes = 25)
        assertEquals(0L, session.id)
        assertEquals(25, session.durationMinutes)
        assertTrue(session.wasCompleted)
        assertEquals(LocalDate.now().toEpochDay(), session.dateEpochDay)

        val progress = DailyProgressEntity(dateEpochDay = 100L)
        assertEquals(100L, progress.dateEpochDay)
        assertEquals(0, progress.focusMinutes)
        assertEquals(0, progress.sessionsCount)

        val history = FocusHistoryEntity(
            startTime = 1000L,
            endTime = 2000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            dateEpochDay = 100L,
            dayOfWeek = 1,
            startHour = 10,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )
        assertEquals(0L, history.sessionId)
        assertEquals("", history.sessionTitle)

        val calDay = CalendarDayEntity(dateEpochDay = 100L)
        assertEquals(0, calDay.totalFocusMinutes)
        assertEquals(0, calDay.sessionCount)
        assertEquals(0, calDay.longestSessionMinutes)
        assertEquals(0, calDay.totalBreakMinutes)
        assertEquals(0f, calDay.intensityScore, 0.001f)
        assertEquals(1, calDay.dayOfWeek)
        assertEquals("", calDay.yearMonth)

        val insight = ProductivityInsightEntity(insightKey = "k", insightText = "v")
        assertEquals(0L, insight.id)
        assertEquals("k", insight.insightKey)
        assertEquals("v", insight.insightText)
        assertEquals(0f, insight.value, 0.001f)
        assertTrue(insight.generatedAt > 0)

        val syncOp = SyncQueueEntity(collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}")
        assertEquals(0L, syncOp.id)
        assertEquals(0, syncOp.retryCount)
        assertEquals("", syncOp.lastError)
        assertTrue(syncOp.createdAt > 0)
    }

    // ── TaskDao Tests ────────────────────────────────────────────────────────

    @Test
    fun `TaskDao insert, update, delete, ordering and Flow emissions`() = runTest {
        val t1 = TaskEntity(id = 1, title = "First", isCompleted = false, createdAt = 1000L)
        val t2 = TaskEntity(id = 2, title = "Second", isCompleted = false, createdAt = 2000L)

        taskDao.getAllTasks().test {
            assertEquals(emptyList<TaskEntity>(), awaitItem())

            // Insert t1
            taskDao.insertTask(t1)
            val list1 = awaitItem()
            assertEquals(1, list1.size)
            assertEquals("First", list1[0].title)

            // Insert t2 (createdAt = 2000 > 1000, so t2 comes first due to ORDER BY createdAt DESC)
            taskDao.insertTask(t2)
            val list2 = awaitItem()
            assertEquals(2, list2.size)
            assertEquals(2L, list2[0].id)
            assertEquals(1L, list2[1].id)

            // Update completion status
            taskDao.setTaskCompleted(2L, true)
            val list3 = awaitItem()
            assertTrue(list3.first { it.id == 2L }.isCompleted)

            // Delete task 1
            taskDao.deleteTaskById(1L)
            val list4 = awaitItem()
            assertEquals(1, list4.size)
            assertEquals(2L, list4[0].id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `TaskDao getActiveTasks filters completed tasks`() = runTest {
        val t1 = TaskEntity(id = 1, title = "Active", isCompleted = false, createdAt = 1000L)
        val t2 = TaskEntity(id = 2, title = "Completed", isCompleted = true, createdAt = 2000L)

        taskDao.insertTask(t1)
        taskDao.insertTask(t2)

        taskDao.getActiveTasks().test {
            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals(1L, active[0].id)
            assertEquals("Active", active[0].title)

            // Mark t1 completed
            taskDao.setTaskCompleted(1L, true)
            val updatedActive = awaitItem()
            assertTrue(updatedActive.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── FocusSessionDao Tests ────────────────────────────────────────────────

    @Test
    fun `FocusSessionDao insert, ordering, day filtering, total calculation, and Flow emissions`() = runTest {
        val day1 = 19000L
        val day2 = 19001L

        val s1 = FocusSessionEntity(id = 1, durationMinutes = 25, completedAt = 1000L, wasCompleted = true, dateEpochDay = day1)
        val s2 = FocusSessionEntity(id = 2, durationMinutes = 50, completedAt = 2000L, wasCompleted = true, dateEpochDay = day1)
        val s3 = FocusSessionEntity(id = 3, durationMinutes = 30, completedAt = 1500L, wasCompleted = false, dateEpochDay = day1)
        val s4 = FocusSessionEntity(id = 4, durationMinutes = 20, completedAt = 3000L, wasCompleted = true, dateEpochDay = day2)

        sessionDao.insertSession(s1)
        sessionDao.insertSession(s2)
        sessionDao.insertSession(s3)
        sessionDao.insertSession(s4)

        // Test ordering by completedAt DESC
        sessionDao.getAllSessions().test {
            val all = awaitItem()
            assertEquals(4, all.size)
            assertEquals(4L, all[0].id) // 3000L
            assertEquals(2L, all[1].id) // 2000L
            assertEquals(3L, all[2].id) // 1500L
            assertEquals(1L, all[3].id) // 1000L
            cancelAndIgnoreRemainingEvents()
        }

        // Test day filtering
        sessionDao.getSessionsForDay(day1).test {
            val day1Sessions = awaitItem()
            assertEquals(3, day1Sessions.size)
            assertTrue(day1Sessions.all { it.dateEpochDay == day1 })
            assertEquals(2L, day1Sessions[0].id) // 2000L
            cancelAndIgnoreRemainingEvents()
        }

        // Test total minutes for day (wasCompleted = 1 only: 25 + 50 = 75)
        val totalDay1 = sessionDao.getTotalMinutesForDay(day1)
        assertEquals(75, totalDay1)

        val totalEmptyDay = sessionDao.getTotalMinutesForDay(99999L)
        assertNull(totalEmptyDay)
    }

    // ── FocusHistoryDao Tests ────────────────────────────────────────────────

    @Test
    fun `FocusHistoryDao insert, delete, date-range, aggregations and Flow emissions`() = runTest {
        val h1 = FocusHistoryEntity(
            sessionId = 1,
            startTime = 10000L,
            endTime = 11000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Morning Focus",
            dateEpochDay = 100L,
            dayOfWeek = 1, // Monday
            startHour = 9,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )
        val h2 = FocusHistoryEntity(
            sessionId = 2,
            startTime = 20000L,
            endTime = 22000L,
            focusDuration = 50,
            breakDuration = 10,
            completedCycles = 2,
            sessionTitle = "Afternoon Focus",
            dateEpochDay = 100L,
            dayOfWeek = 1, // Monday
            startHour = 14,
            wasCompleted = true,
            actualFocusMinutes = 50,
            actualBreakMinutes = 10
        )
        val h3 = FocusHistoryEntity(
            sessionId = 3,
            startTime = 30000L,
            endTime = 31000L,
            focusDuration = 30,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Night Focus",
            dateEpochDay = 101L,
            dayOfWeek = 2, // Tuesday
            startHour = 20,
            wasCompleted = false,
            actualFocusMinutes = 15,
            actualBreakMinutes = 2
        )

        historyDao.insert(h1)
        historyDao.insert(h2)
        historyDao.insert(h3)

        // Test getAll Flow emission and ordering (startTime DESC)
        historyDao.getAll().test {
            val historyList = awaitItem()
            assertEquals(3, historyList.size)
            assertEquals(3L, historyList[0].sessionId)
            assertEquals(2L, historyList[1].sessionId)
            assertEquals(1L, historyList[2].sessionId)
            cancelAndIgnoreRemainingEvents()
        }

        // Test getForDay
        historyDao.getForDay(100L).test {
            val day100 = awaitItem()
            assertEquals(2, day100.size)
            assertEquals(2L, day100[0].sessionId)
            cancelAndIgnoreRemainingEvents()
        }

        // Test getForRange
        historyDao.getForRange(100L, 101L).test {
            val rangeList = awaitItem()
            assertEquals(3, rangeList.size)
            cancelAndIgnoreRemainingEvents()
        }

        // Aggregations
        assertEquals(75, historyDao.totalFocusMinutesForDay(100L))
        assertEquals(90, historyDao.totalFocusMinutesForRange(100L, 101L))
        assertEquals(50, historyDao.longestSessionForDay(100L))

        val longestEntity = historyDao.longestSessionEntityForDay(100L)
        assertNotNull(longestEntity)
        assertEquals(2L, longestEntity?.sessionId)

        // Analytics helpers
        val mostProdDay = historyDao.mostProductiveDayOfWeek(100L)
        assertNotNull(mostProdDay)
        assertEquals(1, mostProdDay?.dayOfWeek)
        assertEquals(75, mostProdDay?.totalMinutes)

        val mostProdHour = historyDao.mostProductiveHour(100L)
        assertNotNull(mostProdHour)

        assertEquals(37.5f, historyDao.averageSessionMinutes() ?: 0f, 0.01f) // only completed: (25 + 50) / 2 = 37.5
        assertEquals(2, historyDao.activeDaysInRange(100L, 101L))

        assertEquals(22000L, historyDao.getUpdatedAt(2L))
        assertEquals(3, historyDao.getAllSuspend().size)
        assertEquals(2, historyDao.getRecentCompleted().size)

        // Delete
        historyDao.delete(3L)
        assertEquals(2, historyDao.getAllSuspend().size)
    }

    // ── AnalyticsDao Tests ──────────────────────────────────────────────────

    @Test
    fun `AnalyticsDao upsert, query, clearAll and Flow emissions`() = runTest {
        val i1 = ProductivityInsightEntity(id = 1, insightKey = "streak", insightText = "5 days", value = 5f, generatedAt = 1000L)
        val i2 = ProductivityInsightEntity(id = 2, insightKey = "best_day", insightText = "Monday", value = 120f, generatedAt = 2000L)

        analyticsDao.upsertInsight(i1)
        analyticsDao.upsertInsight(i2)

        analyticsDao.getAllInsights().test {
            val insights = awaitItem()
            assertEquals(2, insights.size)
            assertEquals("best_day", insights[0].insightKey) // generatedAt = 2000L > 1000L
            cancelAndIgnoreRemainingEvents()
        }

        val singleInsight = analyticsDao.getInsight("streak")
        assertNotNull(singleInsight)
        assertEquals("5 days", singleInsight?.insightText)

        // Test daily_progress active days query in analyticsDao
        dailyProgressDao.upsertProgress(DailyProgressEntity(dateEpochDay = 100L, focusMinutes = 30))
        dailyProgressDao.upsertProgress(DailyProgressEntity(dateEpochDay = 101L, focusMinutes = 0))
        dailyProgressDao.upsertProgress(DailyProgressEntity(dateEpochDay = 102L, focusMinutes = 45))

        val activeDays = analyticsDao.allActiveDays()
        assertEquals(2, activeDays.size)
        assertEquals(listOf(102L, 100L), activeDays)

        analyticsDao.clearAll()
        assertNull(analyticsDao.getInsight("streak"))
    }

    // ── CalendarDao Tests ────────────────────────────────────────────────────

    @Test
    fun `CalendarDao upsert, month range queries, heatmap and personal bests`() = runTest {
        val cd1 = CalendarDayEntity(dateEpochDay = 100L, totalFocusMinutes = 60, sessionCount = 2, longestSessionMinutes = 40, dayOfWeek = 1, yearMonth = "2025-01")
        val cd2 = CalendarDayEntity(dateEpochDay = 101L, totalFocusMinutes = 120, sessionCount = 4, longestSessionMinutes = 50, dayOfWeek = 2, yearMonth = "2025-01")
        val cd3 = CalendarDayEntity(dateEpochDay = 132L, totalFocusMinutes = 90, sessionCount = 3, longestSessionMinutes = 45, dayOfWeek = 1, yearMonth = "2025-02")

        calendarDao.upsertDay(cd1)
        calendarDao.upsertDay(cd2)
        calendarDao.upsertDay(cd3)

        calendarDao.getMonthDays("2025-01").test {
            val monthDays = awaitItem()
            assertEquals(2, monthDays.size)
            assertEquals(100L, monthDays[0].dateEpochDay)
            assertEquals(101L, monthDays[1].dateEpochDay)
            cancelAndIgnoreRemainingEvents()
        }

        calendarDao.getDaysInRange(100L, 101L).test {
            val range = awaitItem()
            assertEquals(2, range.size)
            cancelAndIgnoreRemainingEvents()
        }

        calendarDao.getHeatmapData(101L).test {
            val heatmap = awaitItem()
            assertEquals(2, heatmap.size) // 101L and 132L
            assertEquals(101L, heatmap[0].dateEpochDay)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(120, calendarDao.personalBestMinutes())
        val bestDayEntity = calendarDao.bestDay()
        assertEquals(101L, bestDayEntity?.dateEpochDay)

        assertEquals(180, calendarDao.monthlyTotal("2025-01"))
        assertEquals(2, calendarDao.activeDaysInMonth("2025-01"))
        assertEquals(2, calendarDao.totalDaysInMonth("2025-01"))
    }
}
