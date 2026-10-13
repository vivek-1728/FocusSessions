package com.focussessions.app.sync

import org.junit.Assert.*
import org.junit.Test

class ConflictResolverTest {

    @Test
    fun `resolve prefers local when local timestamp is newer`() {
        val local = mapOf<String, Any?>("updatedAt" to 1000L, "data" to "local_value")
        val remote = mapOf<String, Any?>("updatedAt" to 500L, "data" to "remote_value")

        val result = ConflictResolver.resolve(local, remote)

        assertEquals(ConflictResolver.Winner.LOCAL, result.side)
        assertEquals("local_value", result.winner["data"])
    }

    @Test
    fun `resolve prefers remote when remote timestamp is newer`() {
        val local = mapOf<String, Any?>("updatedAt" to 500L, "data" to "local_value")
        val remote = mapOf<String, Any?>("updatedAt" to 1000L, "data" to "remote_value")

        val result = ConflictResolver.resolve(local, remote)

        assertEquals(ConflictResolver.Winner.REMOTE, result.side)
        assertEquals("remote_value", result.winner["data"])
    }

    @Test
    fun `resolve prefers remote when timestamps are equal`() {
        val local = mapOf<String, Any?>("updatedAt" to 1000L, "data" to "local_value")
        val remote = mapOf<String, Any?>("updatedAt" to 1000L, "data" to "remote_value")

        val result = ConflictResolver.resolve(local, remote)

        assertEquals(ConflictResolver.Winner.REMOTE, result.side)
        assertEquals("remote_value", result.winner["data"])
    }

    @Test
    fun `resolve handles missing timestamp fields gracefully`() {
        // Missing updatedAt on local (defaults to 0L)
        val localNoTs = mapOf<String, Any?>("title" to "Local Task")
        val remoteTs = mapOf<String, Any?>("updatedAt" to 100L, "title" to "Remote Task")

        val result1 = ConflictResolver.resolve(localNoTs, remoteTs)
        assertEquals(ConflictResolver.Winner.REMOTE, result1.side)
        assertEquals("Remote Task", result1.winner["title"])

        // Missing updatedAt on remote (defaults to 0L)
        val localTs = mapOf<String, Any?>("updatedAt" to 100L, "title" to "Local Task")
        val remoteNoTs = mapOf<String, Any?>("title" to "Remote Task")

        val result2 = ConflictResolver.resolve(localTs, remoteNoTs)
        assertEquals(ConflictResolver.Winner.LOCAL, result2.side)
        assertEquals("Local Task", result2.winner["title"])

        // Missing updatedAt on both (both 0L, tie goes to remote)
        val result3 = ConflictResolver.resolve(localNoTs, remoteNoTs)
        assertEquals(ConflictResolver.Winner.REMOTE, result3.side)
    }

    @Test
    fun `resolve handles deletions correctly via timestamp and deletion flags`() {
        // Newer local deletion wins over older remote active record
        val localDeleted = mapOf<String, Any?>("updatedAt" to 2000L, "deleted" to true, "taskId" to "123")
        val remoteActive = mapOf<String, Any?>("updatedAt" to 1000L, "deleted" to false, "title" to "Task", "taskId" to "123")

        val result1 = ConflictResolver.resolve(localDeleted, remoteActive)
        assertEquals(ConflictResolver.Winner.LOCAL, result1.side)
        assertEquals(true, result1.winner["deleted"])

        // Newer remote modification wins over older local deletion
        val localOldDeleted = mapOf<String, Any?>("updatedAt" to 1000L, "deleted" to true, "taskId" to "123")
        val remoteNewActive = mapOf<String, Any?>("updatedAt" to 2000L, "deleted" to false, "title" to "Re-created Task", "taskId" to "123")

        val result2 = ConflictResolver.resolve(localOldDeleted, remoteNewActive)
        assertEquals(ConflictResolver.Winner.REMOTE, result2.side)
        assertEquals(false, result2.winner["deleted"])
        assertEquals("Re-created Task", result2.winner["title"])
    }

    @Test
    fun `merge overrides base map keys with override map keys without losing non-overlapping base keys`() {
        val base = mapOf<String, Any?>("key1" to "v1", "key2" to "v2", "baseOnly" to "stay")
        val override = mapOf<String, Any?>("key2" to "new_v2", "overrideOnly" to "new")

        val merged = ConflictResolver.merge(base, override)

        assertEquals("v1", merged["key1"])
        assertEquals("new_v2", merged["key2"])
        assertEquals("stay", merged["baseOnly"])
        assertEquals("new", merged["overrideOnly"])
    }
}
