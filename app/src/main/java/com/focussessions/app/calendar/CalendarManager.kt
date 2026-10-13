package com.focussessions.app.calendar

import com.focussessions.app.data.local.database.dao.CalendarDao
import com.focussessions.app.data.local.database.dao.DailyProgressDao
import com.focussessions.app.data.local.database.dao.FocusHistoryDao
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarManager @Inject constructor(
    private val calendarDao: CalendarDao,
    private val historyDao: FocusHistoryDao,
    private val progressDao: DailyProgressDao
) {

    private val ymFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /** All days for a given month as a Flow. */
    fun getMonthData(year: Int, month: Int): Flow<List<CalendarDayEntity>> {
        val ymStr = YearMonth.of(year, month).format(ymFormatter)
        return combine(
            calendarDao.getMonthDays(ymStr),
            historyDao.getAll(),
            progressDao.getRecentProgress()
        ) { calDays, historyList, progressList ->
            mergeCalendarData(calDays, historyList, progressList)
        }
    }

    /** Last 365 days for the heatmap view. */
    fun getYearHeatmap(): Flow<List<CalendarDayEntity>> {
        val from = LocalDate.now().minusDays(364).toEpochDay()
        return combine(
            calendarDao.getHeatmapData(from),
            historyDao.getAll(),
            progressDao.getRecentProgress()
        ) { calDays, historyList, progressList ->
            mergeCalendarData(calDays, historyList, progressList).filter { it.dateEpochDay >= from }
        }
    }

    /** 7-day week containing [date]. */
    fun getWeekData(date: LocalDate): Flow<List<CalendarDayEntity>> {
        val monday = date.with(java.time.DayOfWeek.MONDAY)
        val from = monday.toEpochDay()
        val to = monday.plusDays(6).toEpochDay()
        return combine(
            calendarDao.getDaysInRange(from, to),
            historyDao.getAll(),
            progressDao.getRecentProgress()
        ) { calDays, historyList, progressList ->
            mergeCalendarData(calDays, historyList, progressList).filter { it.dateEpochDay in from..to }
        }
    }

    private fun mergeCalendarData(
        calDays: List<CalendarDayEntity>,
        historyList: List<com.focussessions.app.data.local.database.entities.FocusHistoryEntity>,
        progressList: List<com.focussessions.app.data.local.database.entities.DailyProgressEntity>
    ): List<CalendarDayEntity> {
        val map = calDays.associateBy { it.dateEpochDay }.toMutableMap()

        // Merge focus_history records
        val historyByDay = historyList.groupBy { it.dateEpochDay }
        historyByDay.forEach { (day, sessions) ->
            val totalMins = sessions.sumOf { it.actualFocusMinutes }
            if (totalMins > 0) {
                val longest = sessions.maxOfOrNull { it.actualFocusMinutes } ?: 0
                val totalBreak = sessions.sumOf { it.actualBreakMinutes }
                val existing = map[day]
                val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(sessions.first().startTime), ZoneId.systemDefault())
                val ym = startDt.format(ymFormatter)
                val finalMins = maxOf(existing?.totalFocusMinutes ?: 0, totalMins)

                map[day] = CalendarDayEntity(
                    dateEpochDay = day,
                    totalFocusMinutes = finalMins,
                    sessionCount = maxOf(existing?.sessionCount ?: 0, sessions.size),
                    longestSessionMinutes = maxOf(existing?.longestSessionMinutes ?: 0, longest),
                    totalBreakMinutes = maxOf(existing?.totalBreakMinutes ?: 0, totalBreak),
                    intensityScore = (finalMins.toFloat() / 120f).coerceIn(0.2f, 1f),
                    dayOfWeek = startDt.dayOfWeek.value,
                    yearMonth = ym
                )
            }
        }

        // Merge daily_progress records
        progressList.forEach { p ->
            if (p.focusMinutes > 0) {
                val existing = map[p.dateEpochDay]
                val finalMins = maxOf(existing?.totalFocusMinutes ?: 0, p.focusMinutes)
                val date = LocalDate.ofEpochDay(p.dateEpochDay)
                map[p.dateEpochDay] = CalendarDayEntity(
                    dateEpochDay = p.dateEpochDay,
                    totalFocusMinutes = finalMins,
                    sessionCount = maxOf(existing?.sessionCount ?: 0, p.sessionsCount),
                    longestSessionMinutes = existing?.longestSessionMinutes ?: finalMins,
                    totalBreakMinutes = existing?.totalBreakMinutes ?: 0,
                    intensityScore = (finalMins.toFloat() / 120f).coerceIn(0.2f, 1f),
                    dayOfWeek = date.dayOfWeek.value,
                    yearMonth = date.format(ymFormatter)
                )
            }
        }

        return map.values.toList().sortedBy { it.dateEpochDay }
    }

    /** Build a full month grid (6×7 = 42 cells) for calendar UI. */
    fun buildMonthGrid(year: Int, month: Int, data: List<CalendarDayEntity>): List<CalendarCell> {
        val ym          = YearMonth.of(year, month)
        val firstOfMonth = ym.atDay(1)
        val startOffset  = firstOfMonth.dayOfWeek.value - 1   // 0=Monday

        val dataMap = data.associateBy { it.dateEpochDay }
        val cells   = mutableListOf<CalendarCell>()

        // Leading empty cells
        repeat(startOffset) { cells.add(CalendarCell(null, null)) }

        for (day in 1..ym.lengthOfMonth()) {
            val date     = ym.atDay(day)
            val epochDay = date.toEpochDay()
            cells.add(CalendarCell(date, dataMap[epochDay]))
        }

        // Trailing empty cells to complete the grid
        while (cells.size % 7 != 0) cells.add(CalendarCell(null, null))

        return cells
    }
}

data class CalendarCell(
    val date: LocalDate?,
    val data: CalendarDayEntity?,
    val isToday: Boolean = date == LocalDate.now()
) {
    val isEmpty: Boolean get() = date == null
    val hasFocus: Boolean get() = (data?.totalFocusMinutes ?: 0) > 0
    val intensity: Float  get() = data?.intensityScore ?: 0f
}
