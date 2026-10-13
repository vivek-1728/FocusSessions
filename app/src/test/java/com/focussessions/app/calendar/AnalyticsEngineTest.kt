package com.focussessions.app.calendar

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class AnalyticsEngineTest {

    @Test
    fun `DailyAnalytics model initialization and calculations`() {
        val daily = DailyAnalytics(
            totalFocusMinutes = 120,
            sessionCount = 4,
            longestSessionMinutes = 50,
            totalBreakMinutes = 20
        )

        assertEquals(120, daily.totalFocusMinutes)
        assertEquals(4, daily.sessionCount)
        assertEquals(50, daily.longestSessionMinutes)
        assertEquals(20, daily.totalBreakMinutes)
    }

    @Test
    fun `WeeklyAnalytics model averages calculation`() {
        val weekly = WeeklyAnalytics(
            totalFocusMinutes = 350,
            mostProductiveDay = "Wednesday",
            averageDailyMinutes = 70,
            activeDays = 5,
            streak = 3,
            perDay = mapOf("Mon" to 60, "Tue" to 50, "Wed" to 120, "Thu" to 60, "Fri" to 60)
        )

        assertEquals(350, weekly.totalFocusMinutes)
        assertEquals("Wednesday", weekly.mostProductiveDay)
        assertEquals(70, weekly.averageDailyMinutes)
        assertEquals(5, weekly.activeDays)
        assertEquals(3, weekly.streak)
    }

    @Test
    fun `MonthlyAnalytics consistency calculation`() {
        val ym = YearMonth.of(2026, 2)
        val totalDays = ym.lengthOfMonth() // 28 days
        val activeDays = 21
        val consistency = ((activeDays.toFloat() / totalDays) * 100).toInt()

        val monthly = MonthlyAnalytics(
            totalFocusMinutes = 1050,
            totalSessions = 30,
            activeDays = activeDays,
            totalDaysInMonth = totalDays,
            consistencyPercent = consistency
        )

        assertEquals(28, monthly.totalDaysInMonth)
        assertEquals(21, monthly.activeDays)
        assertEquals(75, monthly.consistencyPercent)
    }
}
