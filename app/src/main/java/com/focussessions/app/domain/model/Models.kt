package com.focussessions.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

// ── Task ──────────────────────────────────────────────────────────────────────
data class Task(
    val id: Long                 = 0,
    val title: String            = "",
    val isCompleted: Boolean     = false,
    val sessionId: Long?         = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

// ── Focus Session (DB record) ─────────────────────────────────────────────────
data class FocusSession(
    val id: Long                   = 0,
    val durationMinutes: Int       = 25,
    val completedAt: LocalDateTime = LocalDateTime.now(),
    val wasCompleted: Boolean      = true,
    val date: LocalDate            = LocalDate.now()
)

// ── Daily Progress ────────────────────────────────────────────────────────────
data class DailyProgress(
    val id: Long           = 0,
    val date: LocalDate    = LocalDate.now(),
    val focusMinutes: Int  = 0,
    val sessionsCount: Int = 0
)

// ── User Preferences ─────────────────────────────────────────────────────────
data class UserPreferences(
    val focusDurationMinutes: Int    = 25,
    val shortBreakMinutes: Int       = 5,
    val longBreakMinutes: Int        = 15,
    val sessionsBeforeLongBreak: Int = 4,
    /** Max 1080 min (18 hours) */
    val dailyGoalMinutes: Int        = 120,
    val autoStartBreaks: Boolean     = false,
    val autoStartFocus: Boolean      = false,
    val musicEnabled: Boolean        = false,
    val selectedMusicUri: String     = "",
    val notificationsEnabled: Boolean = true,
    val streakCount: Int             = 0,
    val lastActiveDate: String       = ""
)

// ── Music track ───────────────────────────────────────────────────────────────
data class MusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: String
)
