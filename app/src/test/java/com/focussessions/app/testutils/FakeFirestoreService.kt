package com.focussessions.app.testutils

import com.focussessions.app.data.local.database.dao.CalendarDao
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
    private val calendarDao: CalendarDao = mockk(relaxed = true)
) : FirestoreSyncService(
    auth = mockk(relaxed = true),
    firestore = mockk(relaxed = true),
    historyDao = historyDao,
    taskDao = taskDao,
    calendarDao = calendarDao
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
        rawUploads.getOrPut(collection) { mutableMapOf() }[documentId] = payload
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
        var merged = 0
        for ((_, entity) in uploadedSessions) {
            val existing = try { historyDao.getById(entity.sessionId) } catch (_: Exception) { null }
            if (existing == null) {
                try { historyDao.insert(entity) } catch (_: Exception) {}
                try {
                    val existingDay = calendarDao.getDay(entity.dateEpochDay)
                    val newTotal = (existingDay?.totalFocusMinutes ?: 0) + entity.actualFocusMinutes
                    calendarDao.upsertDay(
                        CalendarDayEntity(
                            dateEpochDay = entity.dateEpochDay,
                            totalFocusMinutes = newTotal,
                            sessionCount = (existingDay?.sessionCount ?: 0) + 1,
                            longestSessionMinutes = maxOf(existingDay?.longestSessionMinutes ?: 0, entity.actualFocusMinutes),
                            totalBreakMinutes = (existingDay?.totalBreakMinutes ?: 0) + entity.actualBreakMinutes,
                            dayOfWeek = entity.dayOfWeek
                        )
                    )
                } catch (_: Exception) {}
                merged++
            }
        }
        val sessionsMap = rawUploads["focus_sessions"] ?: emptyMap()
        for ((idStr, map) in sessionsMap) {
            val sessionId = idStr.toLongOrNull() ?: continue
            val existing = try { historyDao.getById(sessionId) } catch (_: Exception) { null }
            if (existing == null) {
                val startTime = (map["startTime"] as? Number)?.toLong() ?: (map["startTimestamp"] as? Number)?.toLong() ?: 0L
                val endTime = (map["endTime"] as? Number)?.toLong() ?: (map["endTimestamp"] as? Number)?.toLong() ?: 0L
                val focusDuration = (map["focusDuration"] as? Number)?.toInt() ?: 0
                val breakDuration = (map["breakDuration"] as? Number)?.toInt() ?: 0
                val completedCycles = (map["completedCycles"] as? Number)?.toInt() ?: (map["cyclesCompleted"] as? Number)?.toInt() ?: 0
                val sessionTitle = (map["title"] as? String) ?: (map["sessionTitle"] as? String) ?: ""
                val dateEpochDay = (map["dateEpochDay"] as? Number)?.toLong() ?: 0L
                val dayOfWeek = (map["dayOfWeek"] as? Number)?.toInt() ?: 1
                val startHour = (map["startHour"] as? Number)?.toInt() ?: 0
                val wasCompleted = map["wasCompleted"] as? Boolean ?: false
                val actualFocusMinutes = (map["actualFocusMin"] as? Number)?.toInt() ?: (map["actualFocusMinutes"] as? Number)?.toInt() ?: 0
                val actualBreakMinutes = (map["actualBreakMin"] as? Number)?.toInt() ?: (map["actualBreakMinutes"] as? Number)?.toInt() ?: 0

                val entity = FocusHistoryEntity(
                    sessionId = sessionId,
                    startTime = startTime,
                    endTime = endTime,
                    focusDuration = focusDuration,
                    breakDuration = breakDuration,
                    completedCycles = completedCycles,
                    sessionTitle = sessionTitle,
                    dateEpochDay = dateEpochDay,
                    dayOfWeek = dayOfWeek,
                    startHour = startHour,
                    wasCompleted = wasCompleted,
                    actualFocusMinutes = actualFocusMinutes,
                    actualBreakMinutes = actualBreakMinutes
                )
                try { historyDao.insert(entity) } catch (_: Exception) {}
                try {
                    val existingDay = calendarDao.getDay(dateEpochDay)
                    val newTotal = (existingDay?.totalFocusMinutes ?: 0) + actualFocusMinutes
                    calendarDao.upsertDay(
                        CalendarDayEntity(
                            dateEpochDay = dateEpochDay,
                            totalFocusMinutes = newTotal,
                            sessionCount = (existingDay?.sessionCount ?: 0) + 1,
                            longestSessionMinutes = maxOf(existingDay?.longestSessionMinutes ?: 0, actualFocusMinutes),
                            totalBreakMinutes = (existingDay?.totalBreakMinutes ?: 0) + entity.actualBreakMinutes,
                            dayOfWeek = entity.dayOfWeek
                        )
                    )
                } catch (_: Exception) {}
                merged++
            }
        }
        return Result.success(merged)
    }

    override suspend fun downloadAndMergeTasks(): Result<Int> {
        if (!isUserSignedIn) return Result.failure(IllegalStateException("User is not signed in"))
        if (shouldFail) return Result.failure(failureException)
        var merged = 0
        for ((_, task) in uploadedTasks) {
            taskDao.insertTask(task)
            merged++
        }
        val tasksMap = rawUploads["tasks"] ?: emptyMap()
        for ((idStr, map) in tasksMap) {
            val id = idStr.toLongOrNull() ?: continue
            val title = map["title"] as? String ?: ""
            val isCompleted = map["isCompleted"] as? Boolean ?: false
            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            taskDao.insertTask(TaskEntity(id = id, title = title, isCompleted = isCompleted, createdAt = createdAt))
            merged++
        }
        return Result.success(merged)
    }

    fun clearAll() {
        uploadedSessions.clear()
        uploadedTasks.clear()
        deletedTaskIds.clear()
        uploadedSettings.clear()
        rawUploads.clear()
        rawDeletions.clear()
        shouldFail = false
        isUserSignedIn = true
    }
}
