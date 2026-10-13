package com.focussessions.app.sync

import org.junit.Assert.*
import org.junit.Test

class SyncQueueTest {

    @Test
    fun `sync queue entity creates with correct defaults and fields`() {
        val entity = SyncQueueEntity(
            collection = "focus_sessions",
            documentId = "session_123",
            operation = "UPSERT",
            payload = "{\"durationMinutes\":25}"
        )

        assertEquals(0L, entity.id)
        assertEquals("focus_sessions", entity.collection)
        assertEquals("session_123", entity.documentId)
        assertEquals("UPSERT", entity.operation)
        assertEquals("{\"durationMinutes\":25}", entity.payload)
        assertEquals(0, entity.retryCount)
        assertEquals("", entity.lastError)
        assertTrue(entity.createdAt > 0L)
    }

    @Test
    fun `sync queue entity copy with retry increment works as expected`() {
        val entity = SyncQueueEntity(
            id = 1,
            collection = "tasks",
            documentId = "task_456",
            operation = "DELETE",
            payload = ""
        )

        val updated = entity.copy(retryCount = entity.retryCount + 1, lastError = "Network error")

        assertEquals(1, updated.retryCount)
        assertEquals("Network error", updated.lastError)
    }
}
