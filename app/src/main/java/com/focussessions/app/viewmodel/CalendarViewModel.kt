package com.focussessions.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.calendar.*
import com.focussessions.app.data.local.database.entities.*
import com.focussessions.app.data.repository.FocusSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalendarUiState(
    val viewMode: CalendarViewMode         = CalendarViewMode.MONTH,
    val selectedDate: LocalDate            = LocalDate.now(),
    val currentYearMonth: YearMonth        = YearMonth.now(),
    val monthGrid: List<CalendarCell>      = emptyList(),
    val weekDays: List<CalendarDayEntity>  = emptyList(),
    val heatmapData: List<CalendarDayEntity> = emptyList(),
    val selectedDayData: CalendarDayEntity? = null,
    val selectedDayHistory: List<FocusHistoryEntity> = emptyList(),
    val isLoading: Boolean                 = false
)

enum class CalendarViewMode { MONTH, WEEK, HEATMAP }

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repo: FocusSessionRepository,
    private val calendarManager: CalendarManager
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var monthJob: Job? = null
    private var weekJob: Job? = null
    private var heatmapJob: Job? = null
    private var historyJob: Job? = null

    init {
        loadCurrentMonth()
        loadHeatmap()
        loadCurrentWeek()
        selectDate(LocalDate.now())
    }

    fun setViewMode(mode: CalendarViewMode) {
        _state.update { it.copy(viewMode = mode) }
        when (mode) {
            CalendarViewMode.MONTH   -> loadCurrentMonth()
            CalendarViewMode.WEEK    -> loadCurrentWeek()
            CalendarViewMode.HEATMAP -> loadHeatmap()
        }
    }

    fun previousMonth() {
        val ym = _state.value.currentYearMonth.minusMonths(1)
        _state.update { it.copy(currentYearMonth = ym) }
        loadMonth(ym)
    }

    fun nextMonth() {
        val ym = _state.value.currentYearMonth.plusMonths(1)
        _state.update { it.copy(currentYearMonth = ym) }
        loadMonth(ym)
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repo.getHistoryForDay(date.toEpochDay())
                .collect { history ->
                    _state.update { it.copy(selectedDayHistory = history) }
                }
        }
    }

    private fun loadCurrentMonth() = loadMonth(_state.value.currentYearMonth)

    private fun loadMonth(ym: YearMonth) {
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            calendarManager.getMonthData(ym.year, ym.monthValue).collect { data ->
                val grid = calendarManager.buildMonthGrid(ym.year, ym.monthValue, data)
                _state.update { it.copy(monthGrid = grid, currentYearMonth = ym) }
            }
        }
    }

    private fun loadCurrentWeek() {
        weekJob?.cancel()
        weekJob = viewModelScope.launch {
            calendarManager.getWeekData(_state.value.selectedDate).collect { data ->
                _state.update { it.copy(weekDays = data) }
            }
        }
    }

    private fun loadHeatmap() {
        heatmapJob?.cancel()
        heatmapJob = viewModelScope.launch {
            calendarManager.getYearHeatmap().collect { data ->
                _state.update { it.copy(heatmapData = data) }
            }
        }
    }
}
