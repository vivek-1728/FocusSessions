package com.focussessions.app.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.auth.SyncStatus
import com.focussessions.app.auth.UserProfile
import com.focussessions.app.data.local.database.AppDatabase
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.dao.TaskDao
import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.TaskEntity
import com.focussessions.app.testutils.FakeFirestoreService
import com.focussessions.app.testutils.FakeNetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var historyDao: FocusHistoryDao
    private lateinit var taskDao: TaskDao

    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var fakeFirestoreService: FakeFirestoreService
    private lateinit var authManager: AuthManager

    private val userProfileFlow = MutableStateFlow<UserProfile?>(UserProfile(uid = "user_123", displayName = "Test User"))

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        syncQueueDao = database.syncQueueDao()
        historyDao = database.focusHistoryDao()
        taskDao = database.taskDao()

        fakeNetworkMonitor = FakeNetworkMonitor(context, initialOnline = true)
        fakeFirestoreService = FakeFirestoreService(historyDao, taskDao, database.calendarDao())

        authManager = mockk(relaxed = true)
        every { authManager.currentUser } returns userProfileFlow
        every { authManager.isLoggedIn } returns true

        syncManager = SyncManager(
            context = context,
            authManager = authManager,
            firestoreSync = fakeFirestoreService,
            syncQueueDao = syncQueueDao,
            historyDao = historyDao,
            taskDao = taskDao,
            calendarDao = database.calendarDao(),
            networkMonitor = fakeNetworkMonitor
        )
    }

    @After
    fun tearDown() {
        syncManager.close()
        database.close()
    }

    @Test
    fun `offline changes queue up in SyncQueue and do not upload immediately`() = runTest {
        // Set network offline
        fakeNetworkMonitor.setOnline(false)

        val task = TaskEntity(id = 1L, title = "Offline Task")
        taskDao.insertTask(task)

        syncManager.onTaskChanged(taskId = 1L, deleted = false)

        // Verify item is enqueued in SyncQueue
        assertEquals(1, syncQueueDao.count())
        val queueItem = syncQueueDao.peek(1)[0]
        assertEquals("tasks", queueItem.collection)
        assertEquals("1", queueItem.documentId)

        // Verify it was NOT uploaded to Firestore yet
        assertNull(fakeFirestoreService.rawUploads["tasks"]?.get("1"))
    }

    @Test
    fun `queue drains when network returns online`() = runTest {
        // Start offline
        fakeNetworkMonitor.setOnline(false)

        val task = TaskEntity(id = 2L, title = "Deferred Task")
        taskDao.insertTask(task)
        syncManager.onTaskChanged(taskId = 2L, deleted = false)

        assertEquals(1, syncQueueDao.count())

        // Bring network online and run fullSync
        fakeNetworkMonitor.setOnline(true)
        syncManager.fullSync()

        // Queue should be drained
        assertEquals(0, syncQueueDao.count())

        // Raw upload should now exist in fake Firestore
        val uploaded = fakeFirestoreService.rawUploads["tasks"]?.get("2")
        assertNotNull(uploaded)
        assertEquals("Deferred Task", uploaded?.get("title"))
    }

    @Test
    fun `fullSync uploads pending ops and merges remote data`() = runTest {
        // Enqueue local session offline
        fakeNetworkMonitor.setOnline(false)

        val session = FocusHistoryEntity(
            sessionId = 10L,
            startTime = 1000L,
            endTime = 2000L,
            focusDuration = 25,
            breakDuration = 5,
            completedCycles = 1,
            sessionTitle = "Local Session",
            dateEpochDay = 100L,
            dayOfWeek = 1,
            startHour = 9,
            wasCompleted = true,
            actualFocusMinutes = 25,
            actualBreakMinutes = 5
        )
        historyDao.insert(session)
        syncManager.onSessionCompleted(10L)

        assertEquals(1, syncQueueDao.count())

        // Trigger full sync online
        fakeNetworkMonitor.setOnline(true)
        syncManager.fullSync()

        // Queue is drained
        assertEquals(0, syncQueueDao.count())

        // Sync state should be SYNCED
        assertEquals(SyncStatus.SYNCED, syncManager.syncState.value.status)
        assertEquals(0, syncManager.syncState.value.pendingOps)
    }

    @Test
    fun `partial failure increments retry count and retains un-uploaded item in queue`() = runTest {
        fakeNetworkMonitor.setOnline(true)

        val task1 = TaskEntity(id = 101L, title = "Task 1")
        taskDao.insertTask(task1)

        // Turn on fake failure for upload
        fakeFirestoreService.shouldFail = true
        fakeFirestoreService.failureException = RuntimeException("500 Server Error")

        syncManager.onTaskChanged(101L)

        // Queue should still contain 1 item with retryCount = 1
        assertEquals(1, syncQueueDao.count())
        val queueItem = syncQueueDao.peek(1)[0]
        assertEquals(1, queueItem.retryCount)
        assertTrue(queueItem.lastError.contains("500 Server Error"))

        // Now fix the error and re-sync
        fakeFirestoreService.shouldFail = false
        syncManager.fullSync()

        // Queue drains cleanly
        assertEquals(0, syncQueueDao.count())
    }

    @Test
    fun `no duplicate uploads when flushing queue multiple times`() = runTest {
        fakeNetworkMonitor.setOnline(true)

        val task = TaskEntity(id = 500L, title = "Unique Upload Task")
        taskDao.insertTask(task)

        syncManager.onTaskChanged(500L)

        // First flush succeeded and cleared queue
        assertEquals(0, syncQueueDao.count())

        // Run full sync again
        syncManager.fullSync()

        // Raw uploads count remains 1 for document 500
        assertEquals(1, fakeFirestoreService.rawUploads["tasks"]?.size ?: 0)
    }
}
