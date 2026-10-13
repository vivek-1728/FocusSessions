package com.focussessions.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focussessions.app.calendar.*
import com.focussessions.app.data.local.database.entities.ProductivityInsightEntity
import com.focussessions.app.data.local.database.dao.AnalyticsDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AnalyticsUiState(
    val tab: AnalyticsTab               = AnalyticsTab.WEEKLY,
    val daily: DailyAnalytics           = DailyAnalytics(),
    val weekly: WeeklyAnalytics         = WeeklyAnalytics(),
    val monthly: MonthlyAnalytics       = MonthlyAnalytics(),
    val insights: List<ProductivityInsightEntity> = emptyList(),
    val isLoading: Boolean              = true
)

enum class AnalyticsTab { DAILY, WEEKLY, MONTHLY }

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsEngine: AnalyticsEngine,
    private val calculator: ProductivityCalculator,
    private val analyticsDao: AnalyticsDao
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setTab(tab: AnalyticsTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            calculator.generateAndCacheInsights()
            val daily   = analyticsEngine.getDailyAnalytics(java.time.LocalDate.now().toEpochDay())
            val weekly  = analyticsEngine.getWeeklyAnalytics()
            val monthly = analyticsEngine.getMonthlyAnalytics(
                YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            )
            _state.update { it.copy(daily = daily, weekly = weekly, monthly = monthly, isLoading = false) }
        }
        viewModelScope.launch {
            analyticsDao.getAllInsights().collect { insights ->
                _state.update { it.copy(insights = insights) }
            }
        }
    }
}
