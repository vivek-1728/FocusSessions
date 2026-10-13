package com.focussessions.app.data.local.database.dao

import androidx.room.*
import com.focussessions.app.data.local.database.entities.*
import kotlinx.coroutines.flow.Flow

// ── Existing DAOs ─────────────────────────────────────────────────────────────

@Dao interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTask(t: TaskEntity): Long
    @Query("UPDATE tasks SET isCompleted = :done WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, done: Boolean)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTaskById(id: Long)
}

@Dao interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY completedAt DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>
    @Query("SELECT * FROM focus_sessions WHERE dateEpochDay = :day ORDER BY completedAt DESC")
    fun getSessionsForDay(day: Long): Flow<List<FocusSessionEntity>>
    @Query("SELECT SUM(durationMinutes) FROM focus_sessions WHERE dateEpochDay = :day AND wasCompleted=1")
    suspend fun getTotalMinutesForDay(day: Long): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(s: FocusSessionEntity): Long
}

@Dao interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress WHERE dateEpochDay = :day")
    fun observeProgressForDay(day: Long): Flow<DailyProgressEntity?>
    @Query("SELECT * FROM daily_progress ORDER BY dateEpochDay DESC LIMIT 30")
    fun getRecentProgress(): Flow<List<DailyProgressEntity>>
    @Query("SELECT * FROM daily_progress WHERE dateEpochDay = :day")
    suspend fun getProgressForDay(day: Long): DailyProgressEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: DailyProgressEntity)
    @Query("UPDATE daily_progress SET focusMinutes=focusMinutes+:add, sessionsCount=sessionsCount+1 WHERE dateEpochDay=:day")
    suspend fun addFocusMinutes(day: Long, add: Int)
}

// Helper for weekly summary
data class DaySummary(val dateEpochDay: Long, val totalMinutes: Int)

// ── NEW: Focus History DAO ────────────────────────────────────────────────────

@Dao
interface FocusHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusHistoryEntity): Long

    @Query("SELECT * FROM focus_history WHERE sessionId = :id")
    suspend fun getById(id: Long): FocusHistoryEntity?

    @Query("SELECT * FROM focus_history ORDER BY startTime DESC")
    fun getAll(): Flow<List<FocusHistoryEntity>>

    @Query("SELECT * FROM focus_history WHERE dateEpochDay = :day ORDER BY startTime DESC")
    fun getForDay(day: Long): Flow<List<FocusHistoryEntity>>

    @Query("SELECT * FROM focus_history WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY startTime DESC")
    fun getForRange(fromDay: Long, toDay: Long): Flow<List<FocusHistoryEntity>>

    @Query("SELECT SUM(actualFocusMinutes) FROM focus_history WHERE dateEpochDay = :day")
    suspend fun totalFocusMinutesForDay(day: Long): Int?

    @Query("SELECT SUM(actualFocusMinutes) FROM focus_history WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun totalFocusMinutesForRange(from: Long, to: Long): Int?

    @Query("SELECT MAX(actualFocusMinutes) FROM focus_history WHERE dateEpochDay = :day")
    suspend fun longestSessionForDay(day: Long): Int?

    @Query("""
        SELECT dayOfWeek, SUM(actualFocusMinutes) as totalMinutes
        FROM focus_history
        WHERE dateEpochDay >= :fromDay
        GROUP BY dayOfWeek
        ORDER BY totalMinutes DESC
        LIMIT 1
    """)
    suspend fun mostProductiveDayOfWeek(fromDay: Long): DayOfWeekSummary?

    @Query("""
        SELECT startHour, COUNT(*) as sessionCount, AVG(actualFocusMinutes) as avgMinutes
        FROM focus_history
        WHERE dateEpochDay >= :fromDay AND wasCompleted = 1
        GROUP BY startHour
        ORDER BY sessionCount DESC
        LIMIT 1
    """)
    suspend fun mostProductiveHour(fromDay: Long): HourSummary?

    @Query("SELECT AVG(actualFocusMinutes) FROM focus_history WHERE wasCompleted = 1")
    suspend fun averageSessionMinutes(): Float?

    @Query("SELECT COUNT(DISTINCT dateEpochDay) FROM focus_history WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun activeDaysInRange(from: Long, to: Long): Int

    @Query("SELECT * FROM focus_history WHERE dateEpochDay = :day ORDER BY actualFocusMinutes DESC LIMIT 1")
    suspend fun longestSessionEntityForDay(day: Long): FocusHistoryEntity?

    @Query("DELETE FROM focus_history WHERE sessionId = :id")
    suspend fun delete(id: Long)

    /** Returns the endTime of a session (used as updatedAt proxy for conflict resolution). */
    @Query("SELECT endTime FROM focus_history WHERE sessionId = :id")
    suspend fun getUpdatedAt(id: Long): Long?

    @Query("SELECT * FROM focus_history ORDER BY startTime DESC")
    suspend fun getAllSuspend(): List<FocusHistoryEntity>

    @Query("SELECT * FROM focus_history WHERE wasCompleted = 1 ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentCompleted(limit: Int = 100): List<FocusHistoryEntity>
}

data class DayOfWeekSummary(val dayOfWeek: Int, val totalMinutes: Int)
data class HourSummary(val startHour: Int, val sessionCount: Int, val avgMinutes: Float)

// ── NEW: Calendar DAO ─────────────────────────────────────────────────────────

@Dao
interface CalendarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: CalendarDayEntity)

    @Query("SELECT * FROM calendar_day WHERE yearMonth = :ym ORDER BY dateEpochDay ASC")
    fun getMonthDays(ym: String): Flow<List<CalendarDayEntity>>

    @Query("SELECT * FROM calendar_day WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay ASC")
    fun getDaysInRange(from: Long, to: Long): Flow<List<CalendarDayEntity>>

    @Query("SELECT * FROM calendar_day WHERE dateEpochDay = :day")
    suspend fun getDay(day: Long): CalendarDayEntity?

    @Query("SELECT MAX(totalFocusMinutes) FROM calendar_day")
    suspend fun personalBestMinutes(): Int?

    @Query("SELECT * FROM calendar_day ORDER BY totalFocusMinutes DESC LIMIT 1")
    suspend fun bestDay(): CalendarDayEntity?

    @Query("SELECT SUM(totalFocusMinutes) FROM calendar_day WHERE yearMonth = :ym")
    suspend fun monthlyTotal(ym: String): Int?

    @Query("SELECT COUNT(*) FROM calendar_day WHERE totalFocusMinutes > 0 AND yearMonth = :ym")
    suspend fun activeDaysInMonth(ym: String): Int

    @Query("SELECT COUNT(*) FROM calendar_day WHERE yearMonth = :ym")
    suspend fun totalDaysInMonth(ym: String): Int

    // Last N days for heatmap
    @Query("SELECT * FROM calendar_day WHERE dateEpochDay >= :fromDay ORDER BY dateEpochDay ASC")
    fun getHeatmapData(fromDay: Long): Flow<List<CalendarDayEntity>>
}

// ── NEW: Analytics / Insights DAO ────────────────────────────────────────────

@Dao
interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInsight(i: ProductivityInsightEntity)

    @Query("SELECT * FROM productivity_insights ORDER BY generatedAt DESC")
    fun getAllInsights(): Flow<List<ProductivityInsightEntity>>

    @Query("SELECT * FROM productivity_insights WHERE insightKey = :key LIMIT 1")
    suspend fun getInsight(key: String): ProductivityInsightEntity?

    @Query("DELETE FROM productivity_insights")
    suspend fun clearAll()

    // Weekly streak query — counts consecutive days with focus > 0
    @Query("""
        SELECT dateEpochDay FROM daily_progress
        WHERE focusMinutes > 0
        ORDER BY dateEpochDay DESC
    """)
    suspend fun allActiveDays(): List<Long>
}
