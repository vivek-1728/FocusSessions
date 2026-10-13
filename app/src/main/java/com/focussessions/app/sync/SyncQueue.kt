package com.focussessions.app.sync

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Firestore sub-collection name: "focus_sessions", "tasks", "settings", etc. */
    val collection: String,
    /** Firestore document ID (usually matches local primary key as string). */
    val documentId: String,
    /** "UPSERT" or "DELETE" */
    val operation: String,
    /** JSON-serialised payload for UPSERT operations. */
    val payload: String,
    val createdAt: Long  = System.currentTimeMillis(),
    val retryCount: Int  = 0,
    val lastError: String = ""
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC LIMIT :limit")
    suspend fun peek(limit: Int = 50): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun count(): Int

    @Delete
    suspend fun remove(op: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun removeByIds(ids: List<Long>)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :err WHERE id = :id")
    suspend fun incrementRetry(id: Long, err: String)

    @Query("DELETE FROM sync_queue WHERE retryCount >= 5")
    suspend fun purgeDeadLetters()

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
