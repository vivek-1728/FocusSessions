package com.focussessions.app.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.focussessions.app.data.local.database.AppDatabase
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
class SyncQueueDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var syncQueueDao: SyncQueueDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        syncQueueDao = database.syncQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enqueue inserts items and observeCount emits updated size`() = runTest {
        syncQueueDao.observeCount().test {
            assertEquals(0, awaitItem())

            val op1 = SyncQueueEntity(collection = "tasks", documentId = "101", operation = "UPSERT", payload = "{}")
            val id1 = syncQueueDao.enqueue(op1)
            assertTrue(id1 > 0)
            assertEquals(1, awaitItem())

            val op2 = SyncQueueEntity(collection = "focus_sessions", documentId = "201", operation = "UPSERT", payload = "{}")
            val id2 = syncQueueDao.enqueue(op2)
            assertTrue(id2 > 0)
            assertEquals(2, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `peek returns items ordered by createdAt ASC`() = runTest {
        val op1 = SyncQueueEntity(id = 1, collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}", createdAt = 1000L)
        val op2 = SyncQueueEntity(id = 2, collection = "tasks", documentId = "2", operation = "DELETE", payload = "", createdAt = 500L)
        val op3 = SyncQueueEntity(id = 3, collection = "settings", documentId = "preferences", operation = "UPSERT", payload = "{}", createdAt = 1500L)

        syncQueueDao.enqueue(op1)
        syncQueueDao.enqueue(op2)
        syncQueueDao.enqueue(op3)

        val peeked = syncQueueDao.peek(50)
        assertEquals(3, peeked.size)
        // op2 has createdAt = 500L, op1 has 1000L, op3 has 1500L
        assertEquals("2", peeked[0].documentId)
        assertEquals("1", peeked[1].documentId)
        assertEquals("preferences", peeked[2].documentId)
    }

    @Test
    fun `removeByIds dequeues specified items`() = runTest {
        val op1 = SyncQueueEntity(collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}")
        val op2 = SyncQueueEntity(collection = "tasks", documentId = "2", operation = "UPSERT", payload = "{}")
        val id1 = syncQueueDao.enqueue(op1)
        val id2 = syncQueueDao.enqueue(op2)

        assertEquals(2, syncQueueDao.count())

        syncQueueDao.removeByIds(listOf(id1))

        assertEquals(1, syncQueueDao.count())
        val remaining = syncQueueDao.peek(50)
        assertEquals(id2, remaining[0].id)
    }

    @Test
    fun `incrementRetry increases retry count and records last error`() = runTest {
        val op = SyncQueueEntity(collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}")
        val id = syncQueueDao.enqueue(op)

        syncQueueDao.incrementRetry(id, "Timeout 5000ms")

        val item = syncQueueDao.peek(1)[0]
        assertEquals(1, item.retryCount)
        assertEquals("Timeout 5000ms", item.lastError)
    }

    @Test
    fun `purgeDeadLetters removes ops with retryCount 5 or greater`() = runTest {
        val opNormal = SyncQueueEntity(collection = "tasks", documentId = "1", operation = "UPSERT", payload = "{}")
        val opFailed = SyncQueueEntity(collection = "tasks", documentId = "2", operation = "UPSERT", payload = "{}", retryCount = 5)

        syncQueueDao.enqueue(opNormal)
        val idFailed = syncQueueDao.enqueue(opFailed)

        // Ensure opFailed has retryCount >= 5
        syncQueueDao.incrementRetry(idFailed, "Max retries reached") // retryCount becomes 6

        assertEquals(2, syncQueueDao.count())

        syncQueueDao.purgeDeadLetters()

        assertEquals(1, syncQueueDao.count())
        val remaining = syncQueueDao.peek(50)
        assertEquals("1", remaining[0].documentId)
    }
}
