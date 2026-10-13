package com.focussessions.app.sync

import android.content.Context
import androidx.work.*
import com.focussessions.app.auth.AuthManager
import com.focussessions.app.auth.SyncState
import com.focussessions.app.auth.SyncStatus
import com.focussessions.app.connectivity.NetworkMonitor
import com.focussessions.app.data.local.database.dao.CalendarDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.dao.TaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val firestoreSync: FirestoreSyncService,
    private val syncQueueDao: SyncQueueDao,
    private val historyDao: FocusHistoryDao,
    private val taskDao: TaskDao,
    private val calendarDao: CalendarDao,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    init {
        // Auto-sync when coming back online
        scope.launch {
            combine(networkMonitor.isOnline, authManager.currentUser) { online, user ->
                online to (user != null)
            }.collect { (online, loggedIn) ->
                if (online && loggedIn) {
                    flushQueue()
                } else if (!online) {
                    _syncState.update { it.copy(status = SyncStatus.OFFLINE) }
                }
            }
        }

        // Observe queue size for UI badge
        scope.launch {
            syncQueueDao.observeCount().collect { count ->
                _syncState.update { it.copy(pendingOps = count) }
            }
        }

        schedulePeriodicSync()
    }

    fun close() {
        scope.cancel()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called when a session completes — enqueue upload immediately. */
    suspend fun onSessionCompleted(sessionId: Long) {
        val entity = historyDao.getById(sessionId) ?: return
        enqueue(SyncQueueEntity(
            collection  = "focus_sessions",
            documentId  = sessionId.toString(),
            operation   = "UPSERT",
            payload     = entity.toJsonString()
        ))
        onCalendarDayChanged(entity.dateEpochDay)
        if (networkMonitor.isCurrentlyOnline() && authManager.isLoggedIn) flushQueue()
    }

    /** Called when a task is changed. */
    suspend fun onTaskChanged(taskId: Long, deleted: Boolean = false) {
        if (deleted) {
            enqueue(SyncQueueEntity(collection = "tasks", documentId = taskId.toString(),
                operation = "DELETE", payload = ""))
        } else {
            val entity = taskDao.getTaskById(taskId) ?: return
            enqueue(SyncQueueEntity(collection = "tasks", documentId = taskId.toString(),
                operation = "UPSERT", payload = entity.toJsonString()))
        }
        if (networkMonitor.isCurrentlyOnline() && authManager.isLoggedIn) flushQueue()
    }

    /** Called when settings change. */
    suspend fun onSettingsChanged(prefsMap: Map<String, Any>) {
        enqueue(SyncQueueEntity(collection = "settings", documentId = "preferences",
            operation = "UPSERT", payload = prefsMap.toJsonString()))
        if (networkMonitor.isCurrentlyOnline() && authManager.isLoggedIn) flushQueue()
    }

    /** Called minute-by-minute while a focus session is in progress. */
    suspend fun onLiveProgressChanged(sessionId: Long) {
        val entity = historyDao.getById(sessionId)
        if (entity != null) {
            enqueue(SyncQueueEntity(
                collection  = "focus_sessions",
                documentId  = sessionId.toString(),
                operation   = "UPSERT",
                payload     = entity.toJsonString()
            ))
            onCalendarDayChanged(entity.dateEpochDay)
        }
        if (networkMonitor.isCurrentlyOnline() && authManager.isLoggedIn) flushQueue()
    }

    /** Called when a calendar day's focus stats change. */
    suspend fun onCalendarDayChanged(epochDay: Long) {
        val entity = calendarDao.getDay(epochDay)
        if (entity != null) {
            enqueue(SyncQueueEntity(
                collection  = "calendar_days",
                documentId  = epochDay.toString(),
                operation   = "UPSERT",
                payload     = entity.toJsonString()
            ))
        }
        if (networkMonitor.isCurrentlyOnline() && authManager.isLoggedIn) flushQueue()
    }

    /** Full sync on first login or manual pull. */
    suspend fun fullSync() {
        if (!authManager.isLoggedIn || !networkMonitor.isCurrentlyOnline()) return
        _syncState.update { it.copy(status = SyncStatus.SYNCING) }
        try {
            // Upload pending queue first
            flushQueue()
            // Download & merge cloud data
            firestoreSync.downloadAndMergeFocusSessions()
            firestoreSync.downloadAndMergeDailyProgress()
            firestoreSync.downloadAndMergeCalendarDays()
            firestoreSync.downloadAndMergeTasks()

            _syncState.update {
                it.copy(status = SyncStatus.SYNCED, lastSyncTime = System.currentTimeMillis())
            }
        } catch (e: Exception) {
            _syncState.update { it.copy(status = SyncStatus.ERROR, errorMessage = e.message ?: "Sync failed") }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun flushQueue() {
        if (!authManager.isLoggedIn || !networkMonitor.isCurrentlyOnline()) return
        _syncState.update { it.copy(status = SyncStatus.SYNCING) }
        syncQueueDao.purgeDeadLetters()

        val ops = syncQueueDao.peek(50)
        val succeeded = mutableListOf<Long>()

        for (op in ops) {
            val result = when (op.operation) {
                "UPSERT" -> {
                    val map = op.payload.fromJsonString()
                    firestoreSync.uploadRaw(op.collection, op.documentId, map)
                }
                "DELETE" -> firestoreSync.deleteRaw(op.collection, op.documentId)
                else     -> Result.failure(IllegalArgumentException("Unknown op: ${op.operation}"))
            }
            if (result.isSuccess) {
                succeeded.add(op.id)
            } else {
                syncQueueDao.incrementRetry(op.id, result.exceptionOrNull()?.message ?: "")
            }
        }

        if (succeeded.isNotEmpty()) syncQueueDao.removeByIds(succeeded)

        val remaining = syncQueueDao.count()
        _syncState.update {
            it.copy(
                status       = if (remaining == 0) SyncStatus.SYNCED else SyncStatus.ERROR,
                lastSyncTime = System.currentTimeMillis(),
                pendingOps   = remaining
            )
        }
    }

    private suspend fun enqueue(op: SyncQueueEntity) = syncQueueDao.enqueue(op)

    private fun schedulePeriodicSync() {
        if (isUnitTestEnvironment()) return
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "focus_sync_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: Throwable) {}
    }

    private fun isUnitTestEnvironment(): Boolean {
        return try {
            Class.forName("org.robolectric.Robolectric") != null
        } catch (_: Throwable) {
            false
        }
    }
}

// ── Simple JSON helpers (avoids Gson/Moshi dependency for small payloads) ─────

fun Map<String, Any>.toJsonString(): String = buildString {
    append("{")
    entries.forEachIndexed { i, (k, v) ->
        if (i > 0) append(",")
        append("\"$k\":")
        when (v) {
            is String  -> append("\"${v.replace("\"", "\\\"")}\"")
            is Boolean -> append(v)
            is Number  -> append(v)
            else       -> append("\"$v\"")
        }
    }
    append("}")
}

fun String.fromJsonString(): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    val content = trim().removePrefix("{").removeSuffix("}")
    if (content.isBlank()) return result
    val pairs = content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
    for (pair in pairs) {
        val colon = pair.indexOf(':')
        if (colon < 0) continue
        val key = pair.substring(0, colon).trim().removeSurrounding("\"")
        val raw = pair.substring(colon + 1).trim()
        result[key] = when {
            raw == "true"         -> true
            raw == "false"        -> false
            raw.startsWith("\"") -> raw.removeSurrounding("\"")
            raw.contains(".")    -> raw.toDoubleOrNull() ?: raw
            else                 -> raw.toLongOrNull() ?: raw
        }
    }
    return result
}

fun com.focussessions.app.data.local.database.entities.FocusHistoryEntity.toJsonString() =
    mapOf<String, Any>(
        "sessionId"     to sessionId, "startTime" to startTime, "endTime" to endTime,
        "focusDuration" to focusDuration, "breakDuration" to breakDuration,
        "completedCycles" to completedCycles, "title" to sessionTitle,
        "dateEpochDay"  to dateEpochDay, "dayOfWeek" to dayOfWeek, "startHour" to startHour,
        "wasCompleted"  to wasCompleted, "actualFocusMin" to actualFocusMinutes,
        "actualBreakMin" to actualBreakMinutes, "updatedAt" to endTime
    ).toJsonString()

fun com.focussessions.app.data.local.database.entities.TaskEntity.toJsonString() =
    mapOf<String, Any>(
        "taskId" to id, "title" to title, "isCompleted" to isCompleted,
        "createdAt" to createdAt, "updatedAt" to System.currentTimeMillis()
    ).toJsonString()

fun com.focussessions.app.data.local.database.entities.DailyProgressEntity.toJsonString() =
    mapOf<String, Any>(
        "dateEpochDay"  to dateEpochDay,
        "focusMinutes"  to focusMinutes,
        "sessionsCount" to sessionsCount,
        "updatedAt"     to System.currentTimeMillis()
    ).toJsonString()

fun com.focussessions.app.data.local.database.entities.CalendarDayEntity.toJsonString() =
    mapOf<String, Any>(
        "dateEpochDay"          to dateEpochDay,
        "totalFocusMinutes"     to totalFocusMinutes,
        "sessionCount"          to sessionCount,
        "longestSessionMinutes" to longestSessionMinutes,
        "totalBreakMinutes"     to totalBreakMinutes,
        "intensityScore"        to intensityScore,
        "dayOfWeek"             to dayOfWeek,
        "yearMonth"             to yearMonth,
        "updatedAt"             to System.currentTimeMillis()
    ).toJsonString()
