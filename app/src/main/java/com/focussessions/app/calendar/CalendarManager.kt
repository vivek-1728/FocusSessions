package com.focussessions.app.calendar

import com.focussessions.app.data.local.database.dao.CalendarDao
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarManager @Inject constructor(private val calendarDao: CalendarDao) {

    private val ymFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /** All days for a given month as a Flow. */
    fun getMonthData(year: Int, month: Int): Flow<List<CalendarDayEntity>> =
        calendarDao.getMonthDays(YearMonth.of(year, month).format(ymFormatter))

    /** Last 365 days for the heatmap view. */
    fun getYearHeatmap(): Flow<List<CalendarDayEntity>> {
        val from = LocalDate.now().minusDays(364).toEpochDay()
        return calendarDao.getHeatmapData(from)
    }

    /** 7-day week containing [date]. */
    fun getWeekData(date: LocalDate): Flow<List<CalendarDayEntity>> {
        val monday = date.with(java.time.DayOfWeek.MONDAY)
        return calendarDao.getDaysInRange(monday.toEpochDay(), monday.plusDays(6).toEpochDay())
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
