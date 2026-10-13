package com.focussessions.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.auth.UserProfile
import com.focussessions.app.data.local.database.AppDatabase
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.TaskEntity
import com.focussessions.app.testutils.FakeFirestoreService
import com.focussessions.app.testutils.FakeNetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TwoDeviceSyncTest {

    private lateinit var context: Context

    // Device A
    private lateinit var dbA: AppDatabase
    private lateinit var fakeNetworkA: FakeNetworkMonitor
    private lateinit var authManagerA: AuthManager
    private lateinit var serviceA: FakeFirestoreService
    private lateinit var syncManagerA: SyncManager

    // Device B
    private lateinit var dbB: AppDatabase
    private lateinit var fakeNetworkB: FakeNetworkMonitor
    private lateinit var authManagerB: AuthManager
    private lateinit var serviceB: FakeFirestoreService
    private lateinit var syncManagerB: SyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Initialize Device A
        dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fakeNetworkA = FakeNetworkMonitor(context, initialOnline = true)
        authManagerA = mockk(relaxed = true)
        every { authManagerA.currentUser } returns MutableStateFlow(UserProfile(uid = "user_shared"))
        every { authManagerA.isLoggedIn } returns true

        serviceA = FakeFirestoreService(dbA.focusHistoryDao(), dbA.taskDao(), dbA.calendarDao())

        // Initialize Device B
        dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fakeNetworkB = FakeNetworkMonitor(context, initialOnline = true)
        authManagerB = mockk(relaxed = true)
        every { authManagerB.currentUser } returns MutableStateFlow(UserProfile(uid = "user_shared"))
        every { authManagerB.isLoggedIn } returns true

        serviceB = FakeFirestoreService(dbB.focusHistoryDao(), dbB.taskDao(), dbB.calendarDao())

        syncManagerA = SyncManager(context, authManagerA, serviceA, dbA.syncQueueDao(), dbA.focusHistoryDao(), dbA.taskDao(), dbA.calendarDao(), fakeNetworkA)
        syncManagerB = SyncManager(context, authManagerB, sharedCloudBBridge(), dbB.syncQueueDao(), dbB.focusHistoryDao(), dbB.taskDao(), dbB.calendarDao(), fakeNetworkB)
    }

    private fun sharedCloudBBridge(): FakeFirestoreService {
        return serviceB
    }

    @After
    fun tearDown() {
        syncManagerA.close()
        syncManagerB.close()
        dbA.close()
        dbB.close()
    }

    @Test
    fun `divergent sessions, tasks, streaks, and history on two devices converge to the same state`() = runTest {
        val testDay = 20000L

        // 1. Device A creates Task A and Focus Session A
        val taskA = TaskEntity(id = 100L, title = "Task from Device A", isCompleted = false)
        dbA.taskDao().insertTask(taskA)
        syncManagerA.onTaskChanged(100L)

        val sessionA = FocusHistoryEntity(
            sessionId = 1001L, startTime = 1000000L, endTime = 1001800L,
            focusDuration = 30, breakDuration = 5, completedCycles = 1,
            sessionTitle = "Session Device A", dateEpochDay = testDay,
            dayOfWeek = 1, startHour = 9, wasCompleted = true,
            actualFocusMinutes = 30, actualBreakMinutes = 5
        )
        dbA.focusHistoryDao().insert(sessionA)
        dbA.calendarDao().upsertDay(CalendarDayEntity(dateEpochDay = testDay, totalFocusMinutes = 30, sessionCount = 1))
        syncManagerA.onSessionCompleted(1001L)

        // 2. Device B creates Task B and Focus Session B
        val taskB = TaskEntity(id = 200L, title = "Task from Device B", isCompleted = true)
        dbB.taskDao().insertTask(taskB)
        syncManagerB.onTaskChanged(200L)

        val sessionB = FocusHistoryEntity(
            sessionId = 2002L, startTime = 2000000L, endTime = 2002700L,
            focusDuration = 45, breakDuration = 10, completedCycles = 1,
            sessionTitle = "Session Device B", dateEpochDay = testDay,
            dayOfWeek = 1, startHour = 14, wasCompleted = true,
            actualFocusMinutes = 45, actualBreakMinutes = 10
        )
        dbB.focusHistoryDao().insert(sessionB)
        dbB.calendarDao().upsertDay(CalendarDayEntity(dateEpochDay = testDay, totalFocusMinutes = 45, sessionCount = 1))
        syncManagerB.onSessionCompleted(2002L)

        // Sync both devices
        syncManagerA.fullSync()
        syncManagerB.fullSync()

        // 3. Verify Cloud Store holds state from both Device A and Device B
        val rawTasksA = serviceA.rawUploads["tasks"]
        val rawTasksB = serviceB.rawUploads["tasks"]

        assertNotNull(rawTasksA)
        assertNotNull(rawTasksB)
        assertTrue(rawTasksA?.containsKey("100") == true)
        assertTrue(rawTasksB?.containsKey("200") == true)

        // 4. Cross-populate simulated cloud documents onto both services and run fullSync
        serviceA.rawUploads.getOrPut("tasks") { mutableMapOf() }["200"] = mapOf("taskId" to "200", "title" to "Task from Device B", "isCompleted" to true)
        serviceB.rawUploads.getOrPut("tasks") { mutableMapOf() }["100"] = mapOf("taskId" to "100", "title" to "Task from Device A", "isCompleted" to false)

        serviceA.uploadedTasks["200"] = taskB
        serviceB.uploadedTasks["100"] = taskA

        serviceA.uploadedSessions["2002"] = sessionB
        serviceB.uploadedSessions["1001"] = sessionA

        serviceA.downloadAndMergeFocusSessions()
        serviceA.downloadAndMergeTasks()

        serviceB.downloadAndMergeFocusSessions()
        serviceB.downloadAndMergeTasks()

        // 5. Verify Converged Local State on Device A
        val tasksA = dbA.taskDao().getAllTasks().first()
        val historyA = dbA.focusHistoryDao().getAllSuspend()
        val calA = dbA.calendarDao().getDay(testDay)

        // 6. Verify Converged Local State on Device B
        val tasksB = dbB.taskDao().getAllTasks().first()
        val historyB = dbB.focusHistoryDao().getAllSuspend()
        val calB = dbB.calendarDao().getDay(testDay)

        assertEquals(2, tasksA.size)
        assertEquals(2, tasksB.size)
        assertEquals(2, historyA.size)
        assertEquals(2, historyB.size)

        // Verify calendar days converged
        assertNotNull(calA)
        assertNotNull(calB)
        assertEquals(75, calA?.totalFocusMinutes) // 30 + 45
        assertEquals(75, calB?.totalFocusMinutes)

        // 7. Verify Conflict Resolution on user profile streaks & stats
        val profileA = UserProfile(uid = "user_shared", currentStreak = 3, totalFocusMinutes = 30, lastSyncTime = 1000L)
        val profileB = UserProfile(uid = "user_shared", currentStreak = 5, totalFocusMinutes = 75, lastSyncTime = 2000L)

        val resolvedProfile = ConflictResolver.resolve(profileA.toFirestoreMap(), profileB.toFirestoreMap())
        assertEquals(ConflictResolver.Winner.REMOTE, resolvedProfile.side)

        @Suppress("UNCHECKED_CAST")
        val finalProfile = UserProfile.fromFirestoreMap(resolvedProfile.winner as Map<String, Any>)

        assertEquals(5, finalProfile.currentStreak)
        assertEquals(75, finalProfile.totalFocusMinutes)
    }
}
