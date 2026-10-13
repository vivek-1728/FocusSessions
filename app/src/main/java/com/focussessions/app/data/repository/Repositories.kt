package com.focussessions.app.data.repository

import com.focussessions.app.data.local.database.dao.*
import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.data.local.datastore.UserPreferencesDataStore
import com.focussessions.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// ── Task Repository ───────────────────────────────────────────────────────────
@Singleton
class TaskRepository @Inject constructor(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>>    = taskDao.getAllTasks().map { it.map(::taskToDomain) }
    val activeTasks: Flow<List<Task>> = taskDao.getActiveTasks().map { it.map(::taskToDomain) }
    suspend fun addTask(title: String): Long = taskDao.insertTask(TaskEntity(title = title))
    suspend fun toggleTask(task: Task) = taskDao.setTaskCompleted(task.id, !task.isCompleted)
    suspend fun deleteTask(id: Long)   = taskDao.deleteTaskById(id)
    private fun taskToDomain(e: TaskEntity) = Task(e.id, e.title, e.isCompleted, e.sessionId,
        LocalDateTime.ofInstant(Instant.ofEpochMilli(e.createdAt), ZoneId.systemDefault()))
}

// ── Focus Session Repository ──────────────────────────────────────────────────
@Singleton
class FocusSessionRepository @Inject constructor(
    private val sessionDao: FocusSessionDao,
    private val progressDao: DailyProgressDao,
    private val historyDao: FocusHistoryDao,
    private val calendarDao: CalendarDao
) {
    fun getTodayProgress(): Flow<DailyProgressEntity?> =
        progressDao.observeProgressForDay(LocalDate.now().toEpochDay())

    fun getRecentProgress(): Flow<List<DailyProgressEntity>> = progressDao.getRecentProgress()

    suspend fun recordSession(durationMinutes: Int, wasCompleted: Boolean) {
        val today    = LocalDate.now()
        val epochDay = today.toEpochDay()
        val now      = System.currentTimeMillis()

        sessionDao.insertSession(FocusSessionEntity(
            durationMinutes = durationMinutes,
            wasCompleted    = wasCompleted,
            dateEpochDay    = epochDay
        ))

        val existing = progressDao.getProgressForDay(epochDay)
        if (existing == null) {
            progressDao.upsertProgress(DailyProgressEntity(
                dateEpochDay  = epochDay,
                focusMinutes  = if (wasCompleted) durationMinutes else 0,
                sessionsCount = if (wasCompleted) 1 else 0
            ))
        } else if (wasCompleted) {
            progressDao.addFocusMinutes(epochDay, durationMinutes)
        }
    }

    /** Full history record written after a complete cycle. */
    suspend fun recordFullSession(
        startTime: Long,
        focusMinutes: Int,
        breakMinutes: Int,
        cycles: Int,
        wasCompleted: Boolean,
        title: String = ""
    ) {
        val now        = System.currentTimeMillis()
        val startDt    = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault())
        val epochDay   = startDt.toLocalDate().toEpochDay()
        val ym         = startDt.format(DateTimeFormatter.ofPattern("yyyy-MM"))

        historyDao.insert(FocusHistoryEntity(
            startTime          = startTime,
            endTime            = now,
            focusDuration      = focusMinutes,
            breakDuration      = breakMinutes,
            completedCycles    = cycles,
            sessionTitle       = title,
            dateEpochDay       = epochDay,
            dayOfWeek          = startDt.dayOfWeek.value,
            startHour          = startDt.hour,
            wasCompleted       = wasCompleted,
            actualFocusMinutes = focusMinutes,
            actualBreakMinutes = breakMinutes
        ))

        // Update calendar day cache
        val existing = calendarDao.getDay(epochDay)
        val best     = calendarDao.personalBestMinutes() ?: 1
        val newTotal = (existing?.totalFocusMinutes ?: 0) + focusMinutes
        calendarDao.upsertDay(CalendarDayEntity(
            dateEpochDay          = epochDay,
            totalFocusMinutes     = newTotal,
            sessionCount          = (existing?.sessionCount ?: 0) + 1,
            longestSessionMinutes = maxOf(existing?.longestSessionMinutes ?: 0, focusMinutes),
            totalBreakMinutes     = (existing?.totalBreakMinutes ?: 0) + breakMinutes,
            intensityScore        = (newTotal.toFloat() / maxOf(best, newTotal)).coerceIn(0f, 1f),
            dayOfWeek             = startDt.dayOfWeek.value,
            yearMonth             = ym
        ))
    }

    fun getHistory(): Flow<List<FocusHistoryEntity>> = historyDao.getAll()
    fun getHistoryForDay(day: Long): Flow<List<FocusHistoryEntity>> = historyDao.getForDay(day)
    fun getCalendarMonth(ym: String): Flow<List<CalendarDayEntity>> = calendarDao.getMonthDays(ym)
    fun getHeatmap(fromDay: Long): Flow<List<CalendarDayEntity>>    = calendarDao.getHeatmapData(fromDay)
    fun getCalendarRange(from: Long, to: Long): Flow<List<CalendarDayEntity>> =
        calendarDao.getDaysInRange(from, to)
}

// ── User Preferences Repository ───────────────────────────────────────────────
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: UserPreferencesDataStore
) {
    val preferences: Flow<UserPreferences> = dataStore.userPreferences
    suspend fun setFocusDuration(m: Int)        = dataStore.setFocusDuration(m)
    suspend fun setShortBreak(m: Int)           = dataStore.setShortBreak(m)
    suspend fun setLongBreak(m: Int)            = dataStore.setLongBreak(m)
    suspend fun setSessionsBeforeLongBreak(n: Int) = dataStore.setSessionsBeforeLongBreak(n)
    suspend fun setDailyGoal(m: Int)            = dataStore.setDailyGoal(m)
    suspend fun setAutoStartBreaks(e: Boolean)  = dataStore.setAutoStartBreaks(e)
    suspend fun setAutoStartFocus(e: Boolean)   = dataStore.setAutoStartFocus(e)
    suspend fun setMusicEnabled(e: Boolean)     = dataStore.setMusicEnabled(e)
    suspend fun setSelectedMusicUri(uri: String)= dataStore.setSelectedMusicUri(uri)
    suspend fun setNotificationsEnabled(e: Boolean) = dataStore.setNotificationsEnabled(e)
    suspend fun setStreakCount(n: Int)          = dataStore.setStreakCount(n)
    suspend fun setLastActiveDate(d: String)    = dataStore.setLastActiveDate(d)
    suspend fun updateAll(p: UserPreferences)   = dataStore.updateAllPreferences(p)
    suspend fun clearPreferences()              = dataStore.clear()
}
