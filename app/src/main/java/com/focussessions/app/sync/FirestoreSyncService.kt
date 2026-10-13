package com.focussessions.app.sync

import com.focussessions.app.data.local.database.dao.CalendarDao
import com.focussessions.app.data.local.database.dao.DailyProgressDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.dao.TaskDao
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import com.focussessions.app.data.local.database.entities.DailyProgressEntity
import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.TaskEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level Firestore read/write service.
 * All methods return Result<Unit> so callers can handle errors gracefully.
 */
@Singleton
open class FirestoreSyncService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val historyDao: FocusHistoryDao,
    private val taskDao: TaskDao,
    private val calendarDao: CalendarDao,
    private val progressDao: DailyProgressDao
) {
    private val uid get() = auth.currentUser?.uid

    // ── Collection references ─────────────────────────────────────────────────

    private fun userDoc()           = firestore.collection("users").document(uid ?: "anonymous")
    private fun sessionsColl()      = userDoc().collection("focus_sessions")
    private fun dailyProgressColl() = userDoc().collection("daily_progress")
    private fun calendarDaysColl()  = userDoc().collection("calendar_days")
    private fun tasksColl()         = userDoc().collection("tasks")
    private fun settingsColl()      = userDoc().collection("settings")
    private fun stopwatchColl()     = userDoc().collection("stopwatch_history")

    // ── Upload: local → Firestore ─────────────────────────────────────────────

    open suspend fun uploadFocusSession(entity: FocusHistoryEntity): Result<Unit> = runCatching {
        requireUid()
        val doc = mapOf(
            "sessionId"       to entity.sessionId.toString(),
            "title"           to entity.sessionTitle,
            "focusDuration"   to entity.focusDuration,
            "breakDuration"   to entity.breakDuration,
            "cyclesCompleted" to entity.completedCycles,
            "startTimestamp"  to entity.startTime,
            "endTimestamp"    to entity.endTime,
            "wasCompleted"    to entity.wasCompleted,
            "dateEpochDay"    to entity.dateEpochDay,
            "dayOfWeek"       to entity.dayOfWeek,
            "startHour"       to entity.startHour,
            "actualFocusMin"  to entity.actualFocusMinutes,
            "actualBreakMin"  to entity.actualBreakMinutes,
            "deviceId"        to android.os.Build.MODEL,
            "createdAt"       to entity.startTime,
            "updatedAt"       to System.currentTimeMillis()
        )
        sessionsColl().document(entity.sessionId.toString()).set(doc, SetOptions.merge()).await()
    }

    open suspend fun uploadTask(entity: TaskEntity): Result<Unit> = runCatching {
        requireUid()
        val doc = mapOf(
            "taskId"      to entity.id.toString(),
            "title"       to entity.title,
            "isCompleted" to entity.isCompleted,
            "createdAt"   to entity.createdAt,
            "updatedAt"   to System.currentTimeMillis()
        )
        tasksColl().document(entity.id.toString()).set(doc, SetOptions.merge()).await()
    }

    open suspend fun deleteTask(taskId: Long): Result<Unit> = runCatching {
        requireUid()
        tasksColl().document(taskId.toString()).delete().await()
    }

    open suspend fun uploadSettings(prefs: Map<String, Any>): Result<Unit> = runCatching {
        requireUid()
        val doc = prefs.toMutableMap().apply {
            put("updatedAt", System.currentTimeMillis())
        }
        settingsColl().document("preferences").set(doc, SetOptions.merge()).await()
    }

    // ── Batch upload: flush the queue ─────────────────────────────────────────

    /** Uploads a pre-built map directly (used by SyncWorker for queued ops). */
    open suspend fun uploadRaw(collection: String, documentId: String, payload: Map<String, Any>): Result<Unit> =
        runCatching {
            requireUid()
            val collRef = when (collection) {
                "focus_sessions"   -> sessionsColl()
                "daily_progress"   -> dailyProgressColl()
                "calendar_days"    -> calendarDaysColl()
                "tasks"            -> tasksColl()
                "settings"         -> settingsColl()
                "stopwatch_history" -> stopwatchColl()
                else               -> throw IllegalArgumentException("Unknown collection: $collection")
            }
            collRef.document(documentId).set(payload, SetOptions.merge()).await()
        }

    open suspend fun deleteRaw(collection: String, documentId: String): Result<Unit> = runCatching {
        requireUid()
        val collRef = when (collection) {
            "focus_sessions"   -> sessionsColl()
            "daily_progress"   -> dailyProgressColl()
            "calendar_days"    -> calendarDaysColl()
            "tasks"            -> tasksColl()
            "settings"         -> settingsColl()
            else               -> throw IllegalArgumentException("Unknown collection: $collection")
        }
        collRef.document(documentId).delete().await()
    }

    // ── Download: Firestore → local ───────────────────────────────────────────

    open suspend fun downloadAndMergeFocusSessions(): Result<Int> = runCatching {
        requireUid()
        val snapshot = sessionsColl().get().await()
        var merged   = 0

        for (doc in snapshot.documents) {
            val data       = doc.data ?: continue
            val remoteTs   = (data["updatedAt"] as? Number)?.toLong() ?: 0L

            val sessionId  = (data["sessionId"] as? Number)?.toLong()
                ?: (data["sessionId"] as? String)?.toLongOrNull()
                ?: doc.id.toLongOrNull()
                ?: continue

            val localTs = historyDao.getUpdatedAt(sessionId) ?: 0L

            // Remote wins if newer or not present locally
            if (remoteTs >= localTs) {
                val startTime = (data["startTime"] as? Number)?.toLong()
                    ?: (data["startTimestamp"] as? Number)?.toLong()
                    ?: 0L

                val endTime = (data["endTime"] as? Number)?.toLong()
                    ?: (data["endTimestamp"] as? Number)?.toLong()
                    ?: System.currentTimeMillis()

                val startDt  = LocalDateTime.ofInstant(Instant.ofEpochMilli(if (startTime > 0) startTime else System.currentTimeMillis()), ZoneId.systemDefault())
                val epochDay = (data["dateEpochDay"] as? Number)?.toLong()
                    ?: (data["dateEpochDay"] as? String)?.toLongOrNull()
                    ?: startDt.toLocalDate().toEpochDay()

                val focusMins = (data["actualFocusMin"] as? Number)?.toInt()
                    ?: (data["focusDuration"] as? Number)?.toInt()
                    ?: 0

                val breakMins = (data["actualBreakMin"] as? Number)?.toInt()
                    ?: (data["breakDuration"] as? Number)?.toInt()
                    ?: 0

                val cycles = (data["completedCycles"] as? Number)?.toInt()
                    ?: (data["cyclesCompleted"] as? Number)?.toInt()
                    ?: 1

                val title = (data["title"] as? String) ?: (data["sessionTitle"] as? String) ?: "Focus Session"
                val wasCompleted = (data["wasCompleted"] as? Boolean) ?: false
                val ym = startDt.format(DateTimeFormatter.ofPattern("yyyy-MM"))

                historyDao.insert(FocusHistoryEntity(
                    sessionId          = sessionId,
                    startTime          = startTime,
                    endTime            = endTime,
                    focusDuration      = (data["focusDuration"] as? Number)?.toInt() ?: focusMins,
                    breakDuration      = (data["breakDuration"] as? Number)?.toInt() ?: breakMins,
                    completedCycles    = cycles,
                    sessionTitle       = title,
                    dateEpochDay       = epochDay,
                    dayOfWeek          = startDt.dayOfWeek.value,
                    startHour          = startDt.hour,
                    wasCompleted       = wasCompleted,
                    actualFocusMinutes = focusMins,
                    actualBreakMinutes = breakMins
                ))

                // Update daily_progress for today/epochDay
                val existingProgress = progressDao.getProgressForDay(epochDay)
                if (existingProgress == null) {
                    progressDao.upsertProgress(DailyProgressEntity(
                        dateEpochDay  = epochDay,
                        focusMinutes  = focusMins,
                        sessionsCount = 1
                    ))
                } else {
                    progressDao.upsertProgress(existingProgress.copy(
                        focusMinutes  = maxOf(existingProgress.focusMinutes, focusMins),
                        sessionsCount = maxOf(existingProgress.sessionsCount, 1)
                    ))
                }

                // Update calendar_day cache
                val existing = calendarDao.getDay(epochDay)
                val best     = calendarDao.personalBestMinutes() ?: 1
                val newTotal = maxOf(existing?.totalFocusMinutes ?: 0, focusMins)
                calendarDao.upsertDay(CalendarDayEntity(
                    dateEpochDay          = epochDay,
                    totalFocusMinutes     = newTotal,
                    sessionCount          = maxOf(existing?.sessionCount ?: 0, 1),
                    longestSessionMinutes = maxOf(existing?.longestSessionMinutes ?: 0, focusMins),
                    totalBreakMinutes     = maxOf(existing?.totalBreakMinutes ?: 0, breakMins),
                    intensityScore        = (newTotal.toFloat() / maxOf(best, newTotal)).coerceIn(0f, 1f),
                    dayOfWeek             = startDt.dayOfWeek.value,
                    yearMonth             = ym
                ))

                merged++
            }
        }
        merged
    }

    open suspend fun downloadAndMergeDailyProgress(): Result<Int> = runCatching {
        requireUid()
        val snapshot = dailyProgressColl().get().await()
        var merged = 0
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val epochDay = (data["dateEpochDay"] as? Number)?.toLong()
                ?: (data["dateEpochDay"] as? String)?.toLongOrNull()
                ?: doc.id.toLongOrNull()
                ?: continue
            val focusMins = (data["focusMinutes"] as? Number)?.toInt() ?: 0
            val sessCount = (data["sessionsCount"] as? Number)?.toInt() ?: 0

            val existing = progressDao.getProgressForDay(epochDay)
            val newFocus = maxOf(existing?.focusMinutes ?: 0, focusMins)
            val newSess = maxOf(existing?.sessionsCount ?: 0, sessCount)

            progressDao.upsertProgress(DailyProgressEntity(
                dateEpochDay  = epochDay,
                focusMinutes  = newFocus,
                sessionsCount = newSess
            ))
            merged++
        }
        merged
    }

    open suspend fun downloadAndMergeCalendarDays(): Result<Int> = runCatching {
        requireUid()
        val snapshot = calendarDaysColl().get().await()
        var merged = 0
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val epochDay = (data["dateEpochDay"] as? Number)?.toLong()
                ?: (data["dateEpochDay"] as? String)?.toLongOrNull()
                ?: doc.id.toLongOrNull()
                ?: continue

            val totalFocus = (data["totalFocusMinutes"] as? Number)?.toInt() ?: 0
            val sessionCount = (data["sessionCount"] as? Number)?.toInt() ?: 0
            val longestSess = (data["longestSessionMinutes"] as? Number)?.toInt() ?: 0
            val totalBreak = (data["totalBreakMinutes"] as? Number)?.toInt() ?: 0
            val intensity = (data["intensityScore"] as? Number)?.toFloat() ?: 0f
            val dow = (data["dayOfWeek"] as? Number)?.toInt() ?: 1
            val ym = data["yearMonth"] as? String ?: ""

            val existing = calendarDao.getDay(epochDay)
            val newTotal = maxOf(existing?.totalFocusMinutes ?: 0, totalFocus)
            val newCount = maxOf(existing?.sessionCount ?: 0, sessionCount)
            val newLongest = maxOf(existing?.longestSessionMinutes ?: 0, longestSess)
            val newBreak = maxOf(existing?.totalBreakMinutes ?: 0, totalBreak)

            calendarDao.upsertDay(CalendarDayEntity(
                dateEpochDay = epochDay,
                totalFocusMinutes = newTotal,
                sessionCount = newCount,
                longestSessionMinutes = newLongest,
                totalBreakMinutes = newBreak,
                intensityScore = maxOf(existing?.intensityScore ?: 0f, intensity, (newTotal.toFloat() / 120f).coerceIn(0.25f, 1f)),
                dayOfWeek = dow,
                yearMonth = ym
            ))
            merged++
        }
        merged
    }

    open suspend fun downloadAndMergeTasks(): Result<Int> = runCatching {
        requireUid()
        val snapshot = tasksColl().get().await()
        var merged   = 0
        for (doc in snapshot.documents) {
            val data    = doc.data ?: continue
            val taskId  = (data["taskId"] as? Number)?.toLong()
                ?: (data["taskId"] as? String)?.toLongOrNull()
                ?: doc.id.toLongOrNull()
                ?: continue
            taskDao.insertTask(TaskEntity(
                id          = taskId,
                title       = data["title"] as? String ?: "",
                isCompleted = data["isCompleted"] as? Boolean ?: false,
                createdAt   = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            ))
            merged++
        }
        merged
    }

    private fun requireUid() {
        checkNotNull(uid) { "User is not signed in" }
    }
}
