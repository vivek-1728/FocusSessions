package com.focussessions.app.calendar

import com.focussessions.app.data.local.database.dao.AnalyticsDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.entities.ProductivityInsightEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Generates human-readable productivity insights from local data. */
@Singleton
class ProductivityCalculator @Inject constructor(
    private val historyDao: FocusHistoryDao,
    private val analyticsDao: AnalyticsDao
) {
    suspend fun generateAndCacheInsights() {
        analyticsDao.clearAll()
        val insights = mutableListOf<ProductivityInsightEntity>()
        val thirtyDaysAgo = LocalDate.now().minusDays(30).toEpochDay()

        // 1. Best day of week
        historyDao.mostProductiveDayOfWeek(thirtyDaysAgo)?.let { summary ->
            val dayName = java.time.DayOfWeek.of(summary.dayOfWeek)
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
            insights += ProductivityInsightEntity(
                insightKey  = "best_day",
                insightText = "Your most productive day is $dayName",
                value       = summary.totalMinutes.toFloat()
            )
        }

        // 2. Best time of day
        historyDao.mostProductiveHour(thirtyDaysAgo)?.let { summary ->
            val label = when (summary.startHour) {
                in 5..11  -> "mornings"
                in 12..16 -> "afternoons"
                in 17..21 -> "evenings"
                else      -> "late nights"
            }
            insights += ProductivityInsightEntity(
                insightKey  = "best_time",
                insightText = "You focus longer during $label",
                value       = summary.avgMinutes
            )
        }

        // 3. Average session length
        historyDao.averageSessionMinutes()?.let { avg ->
            if (avg > 0f) {
                insights += ProductivityInsightEntity(
                    insightKey  = "avg_session",
                    insightText = "Average uninterrupted session: ${avg.toInt()} minutes",
                    value       = avg
                )
            }
        }

        // 4. Current streak
        val streak = computeStreak()
        if (streak > 1) {
            insights += ProductivityInsightEntity(
                insightKey  = "streak",
                insightText = "You're on a $streak day focus streak 🔥",
                value       = streak.toFloat()
            )
        }

        // 5. Consistency
        val activeDays = historyDao.activeDaysInRange(thirtyDaysAgo, LocalDate.now().toEpochDay())
        val pct = ((activeDays.toFloat() / 30f) * 100).toInt()
        if (pct > 0) {
            insights += ProductivityInsightEntity(
                insightKey  = "consistency",
                insightText = "You focused on $pct% of days in the last 30 days",
                value       = pct.toFloat()
            )
        }

        insights.forEach { analyticsDao.upsertInsight(it) }
    }

    private suspend fun computeStreak(): Int {
        val days = analyticsDao.allActiveDays().sortedDescending()
        if (days.isEmpty()) return 0
        var streak = 0; var expected = LocalDate.now().toEpochDay()
        for (day in days) {
            if (day == expected || (streak == 0 && day == expected - 1)) {
                streak++; expected = day - 1
            } else break
        }
        return streak
    }
}
