package com.focussessions.app.testutils

import com.focussessions.app.data.local.database.dao.CalendarDao
import com.focussessions.app.data.local.database.dao.DailyProgressDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.dao.TaskDao
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.TaskEntity
import com.focussessions.app.sync.FirestoreSyncService
import io.mockk.coEvery
import io.mockk.mockk

/**
 * In-memory fake for FirestoreSyncService.
 * Guarantees tests NEVER make network calls to real Firebase Firestore.
 */
class FakeFirestoreService(
    private val historyDao: FocusHistoryDao = mockk(relaxed = true) {
        coEvery { getById(any()) } returns null
    },
    private val taskDao: TaskDao = mockk(relaxed = true),
    private val calendarDao: CalendarDao = mockk(relaxed = true),
    private val progressDao: DailyProgressDao = mockk(relaxed = true)
) : FirestoreSyncService(
    auth = mockk(relaxed = true),
    firestore = mockk(relaxed = true),
    historyDao = historyDao,
    taskDao = taskDao,
    calendarDao = calendarDao,
    progressDao = progressDao
) {

    var isUserSignedIn: Boolean = true
    var shouldFail: Boolean = false
    var failureException: Exception = Exception("Simulated Firestore Error")

    val uploadedSessions = mutableMapOf<String, FocusHistoryEntity>()
    val uploadedTasks = mutableMapOf<String, TaskEntity>()
    val deletedTaskIds = mutableSetOf<Long>()
    val uploadedSettings = mutableMapOf<String, Any>()
    val rawUploads = mutableMapOf<String, MutableMap<String, Map<String, Any>>>()
    val rawDeletions = mutableListOf<Pair<String, String>>()

    override suspend fun uploadFocusSession(entity: FocusHistoryEntity): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        uploadedSessions[entity.sessionId.toString()] = entity
        return Result.success(Unit)
    }

    override suspend fun uploadTask(entity: TaskEntity): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        uploadedTasks[entity.id.toString()] = entity
        return Result.success(Unit)
    }

    override suspend fun deleteTask(taskId: Long): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        deletedTaskIds.add(taskId)
        uploadedTasks.remove(taskId.toString())
        return Result.success(Unit)
    }

    override suspend fun uploadSettings(prefs: Map<String, Any>): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        uploadedSettings.putAll(prefs)
        return Result.success(Unit)
    }

    override suspend fun uploadRaw(collection: String, documentId: String, payload: Map<String, Any>): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)

        val mapForColl = rawUploads.getOrPut(collection) { mutableMapOf() }
        mapForColl[documentId] = payload

        if (collection == "focus_sessions") {
            val sessionId = documentId.toLongOrNull() ?: 0L
            val focusMins = (payload["actualFocusMin"] as? Number)?.toInt()
                ?: (payload["focusDuration"] as? Number)?.toInt() ?: 0
            val breakMins = (payload["actualBreakMin"] as? Number)?.toInt()
                ?: (payload["breakDuration"] as? Number)?.toInt() ?: 0

            val title = (payload["title"] as? String) ?: "Session"
            val wasCompleted = payload["wasCompleted"] as? Boolean ?: true

            val entity = FocusHistoryEntity(
                sessionId = sessionId,
                startTime = (payload["startTime"] as? Number)?.toLong() ?: (payload["startTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                endTime = (payload["endTime"] as? Number)?.toLong() ?: (payload["endTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                focusDuration = focusMins,
                breakDuration = breakMins,
                completedCycles = (payload["completedCycles"] as? Number)?.toInt() ?: 1,
                sessionTitle = title,
                dateEpochDay = (payload["dateEpochDay"] as? Number)?.toLong() ?: 100L,
                dayOfWeek = (payload["dayOfWeek"] as? Number)?.toInt() ?: 1,
                startHour = (payload["startHour"] as? Number)?.toInt() ?: 9,
                wasCompleted = wasCompleted,
                actualFocusMinutes = focusMins,
                actualBreakMinutes = breakMins
            )
            uploadedSessions[documentId] = entity
        }

        return Result.success(Unit)
    }

    override suspend fun deleteRaw(collection: String, documentId: String): Result<Unit> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)

        rawDeletions.add(collection to documentId)
        rawUploads[collection]?.remove(documentId)

        return Result.success(Unit)
    }

    override suspend fun downloadAndMergeFocusSessions(): Result<Int> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)

        var count = 0
        uploadedSessions.values.forEach { entity ->
            val local = historyDao.getById(entity.sessionId)
            if (local == null) {
                historyDao.insert(entity)
                val existing = calendarDao.getDay(entity.dateEpochDay)
                val newTotal = (existing?.totalFocusMinutes ?: 0) + entity.actualFocusMinutes
                calendarDao.upsertDay(
                    CalendarDayEntity(
                        dateEpochDay = entity.dateEpochDay,
                        totalFocusMinutes = newTotal,
                        sessionCount = (existing?.sessionCount ?: 0) + 1
                    )
                )
                count++
            }
        }
        return Result.success(count)
    }

    override suspend fun downloadAndMergeDailyProgress(): Result<Int> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        return Result.success(0)
    }

    override suspend fun downloadAndMergeTasks(): Result<Int> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("Not signed in"))
        if (shouldFail) return Result.failure(failureException)

        var count = 0
        uploadedTasks.values.forEach { entity ->
            taskDao.insertTask(entity)
            count++
        }
        return Result.success(count)
    }
}
