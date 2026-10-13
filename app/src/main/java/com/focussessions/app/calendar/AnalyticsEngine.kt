package com.focussessions.app.calendar

import com.focussessions.app.data.local.database.dao.AnalyticsDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.entities.FocusHistoryEntity
import com.focussessions.app.data.local.database.entities.ProductivityInsightEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DailyAnalytics(
    val totalFocusMinutes: Int    = 0,
    val sessionCount: Int         = 0,
    val longestSessionMinutes: Int = 0,
    val totalBreakMinutes: Int    = 0,
    val sessions: List<FocusHistoryEntity> = emptyList()
)

data class WeeklyAnalytics(
    val totalFocusMinutes: Int = 0,
    val mostProductiveDay: String = "",
    val averageDailyMinutes: Int  = 0,
    val activeDays: Int           = 0,
    val streak: Int               = 0,
    val perDay: Map<String, Int>  = emptyMap()   // dayName → minutes
)

data class MonthlyAnalytics(
    val totalFocusMinutes: Int    = 0,
    val totalSessions: Int        = 0,
    val activeDays: Int           = 0,
    val totalDaysInMonth: Int     = 0,
    val consistencyPercent: Int   = 0,
    val bestDayMinutes: Int       = 0,
    val bestDayLabel: String      = ""
)

@Singleton
class AnalyticsEngine @Inject constructor(
    private val historyDao: FocusHistoryDao,
    private val analyticsDao: AnalyticsDao
) {
    // ── Daily ─────────────────────────────────────────────────────────────────
    suspend fun getDailyAnalytics(epochDay: Long): DailyAnalytics {
        val total   = historyDao.totalFocusMinutesForDay(epochDay) ?: 0
        val longest = historyDao.longestSessionForDay(epochDay) ?: 0
        return DailyAnalytics(
            totalFocusMinutes     = total,
            longestSessionMinutes = longest
        )
    }

    // ── Weekly ────────────────────────────────────────────────────────────────
    suspend fun getWeeklyAnalytics(): WeeklyAnalytics {
        val today    = LocalDate.now()
        val monday   = today.with(java.time.DayOfWeek.MONDAY)
        val fromDay  = monday.toEpochDay()
        val toDay    = today.toEpochDay()

        val total   = historyDao.totalFocusMinutesForRange(fromDay, toDay) ?: 0
        val active  = historyDao.activeDaysInRange(fromDay, toDay)
        val streak  = computeCurrentStreak()

        // Per-day breakdown
        val perDay = mutableMapOf<String, Int>()
        for (i in 0..6) {
            val day    = monday.plusDays(i.toLong())
            val label  = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val mins   = historyDao.totalFocusMinutesForRange(day.toEpochDay(), day.toEpochDay()) ?: 0
            perDay[label] = mins
        }

        val bestEntry   = perDay.maxByOrNull { it.value }
        val mostProdDay = bestEntry?.key ?: ""

        return WeeklyAnalytics(
            totalFocusMinutes    = total,
            mostProductiveDay    = mostProdDay,
            averageDailyMinutes  = if (active > 0) total / active else 0,
            activeDays           = active,
            streak               = streak,
            perDay               = perDay
        )
    }

    // ── Monthly ───────────────────────────────────────────────────────────────
    suspend fun getMonthlyAnalytics(yearMonth: String): MonthlyAnalytics {
        val parts  = yearMonth.split("-")
        val year   = parts[0].toInt(); val month = parts[1].toInt()
        val ym     = java.time.YearMonth.of(year, month)
        val days   = ym.lengthOfMonth()
        val from   = ym.atDay(1).toEpochDay()
        val to     = ym.atEndOfMonth().toEpochDay()

        val total   = historyDao.totalFocusMinutesForRange(from, to) ?: 0
        val active  = historyDao.activeDaysInRange(from, to)
        val consistency = ((active.toFloat() / days) * 100).toInt()

        return MonthlyAnalytics(
            totalFocusMinutes  = total,
            activeDays         = active,
            totalDaysInMonth   = days,
            consistencyPercent = consistency
        )
    }

    // ── Streak ────────────────────────────────────────────────────────────────
    suspend fun computeCurrentStreak(): Int {
        val activeDays = analyticsDao.allActiveDays().sortedDescending()
        if (activeDays.isEmpty()) return 0
        val today = LocalDate.now().toEpochDay()
        var streak = 0
        var expected = today
        for (day in activeDays) {
            if (day == expected || (streak == 0 && day == today - 1)) {
                streak++
                expected = day - 1
            } else break
        }
        return streak
    }
}
