package com.focussessions.app.data.local.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Existing entities (unchanged) ─────────────────────────────────────────────

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val sessionId: Long?     = null,
    val createdAt: Long      = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val durationMinutes: Int,
    val completedAt: Long    = System.currentTimeMillis(),
    val wasCompleted: Boolean = true,
    val dateEpochDay: Long   = java.time.LocalDate.now().toEpochDay()
)

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val dateEpochDay: Long,
    val focusMinutes: Int  = 0,
    val sessionsCount: Int = 0
)

// ── NEW: Full focus history record ────────────────────────────────────────────

@Entity(
    tableName = "focus_history",
    indices   = [Index("dateEpochDay"), Index("dayOfWeek"), Index("startHour")]
)
data class FocusHistoryEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,

    /** Epoch millis when focus actually started. */
    val startTime: Long,

    /** Epoch millis when session ended (completed or abandoned). */
    val endTime: Long,

    /** Planned focus duration in minutes. */
    val focusDuration: Int,

    /** Break duration in minutes used for this cycle. */
    val breakDuration: Int,

    /** Number of focus-break cycles completed in this run. */
    val completedCycles: Int,

    /** Optional label set by user (e.g. "Deep work", "Reading"). */
    val sessionTitle: String = "",

    /** LocalDate.toEpochDay() — for fast day-level queries. */
    val dateEpochDay: Long,

    /** 1=Monday … 7=Sunday (ISO). */
    val dayOfWeek: Int,

    /** Hour of day [0-23] when session started — for time-of-day analysis. */
    val startHour: Int,

    /** true if user completed full target, false if reset early. */
    val wasCompleted: Boolean,

    /** Total focus minutes actually accumulated (excl. break time). */
    val actualFocusMinutes: Int,

    /** Total break minutes during the cycle. */
    val actualBreakMinutes: Int
)

// ── NEW: Aggregated per-day stats (cache for fast calendar render) ─────────────

@Entity(tableName = "calendar_day")
data class CalendarDayEntity(
    @PrimaryKey val dateEpochDay: Long,

    /** Total focus minutes this day (all sessions combined). */
    val totalFocusMinutes: Int   = 0,

    /** Number of focus sessions this day. */
    val sessionCount: Int        = 0,

    /** Duration of the single longest session (minutes). */
    val longestSessionMinutes: Int = 0,

    /** Total break minutes this day. */
    val totalBreakMinutes: Int   = 0,

    /** Productivity intensity 0.0–1.0 (normalized against personal best). */
    val intensityScore: Float    = 0f,

    /** ISO day-of-week 1–7. */
    val dayOfWeek: Int           = 1,

    /** Year-month string "YYYY-MM" for month grouping. */
    val yearMonth: String        = ""
)

// ── NEW: Cached productivity insights ─────────────────────────────────────────

@Entity(tableName = "productivity_insights")
data class ProductivityInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Short tag: "best_day", "best_time", "avg_session", "streak", etc. */
    val insightKey: String,

    /** Human-readable insight text. */
    val insightText: String,

    /** Supporting numeric value (e.g. 43 for "avg 43 min"). */
    val value: Float = 0f,

    /** When this insight was generated (epoch millis). */
    val generatedAt: Long = System.currentTimeMillis()
)
