package com.focussessions.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focussessions.app.calendar.CalendarCell
import com.focussessions.app.viewmodel.CalendarViewMode
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import com.focussessions.app.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + view switcher
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            ViewModeChips(mode = state.viewMode, onSelect = viewModel::setViewMode)
        }

        when (state.viewMode) {
            CalendarViewMode.MONTH   -> MonthView(state.currentYearMonth, state.monthGrid,
                                            state.selectedDate, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDate)
            CalendarViewMode.WEEK    -> WeekView(state.weekDays)
            CalendarViewMode.HEATMAP -> HeatmapView(state.heatmapData)
            else -> {}
        }

        // Selected day detail
        if (state.selectedDayHistory.isNotEmpty()) {
            DayDetail(state.selectedDate, state.selectedDayHistory)
        }
    }
}

@Composable
private fun ViewModeChips(mode: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CalendarViewMode.entries.forEach { m ->
            val label = when (m) {
                CalendarViewMode.MONTH -> "Month"
                CalendarViewMode.WEEK -> "Week"
                CalendarViewMode.HEATMAP -> "Heatmap"
                else -> ""
            }
            val sel = m == mode
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) Color.White else Color(0xFF111111))
                .clickable { onSelect(m) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (sel) Color.Black else Color(0xFF555555), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal))
            }
        }
    }
}

@Composable
private fun MonthView(
    ym: java.time.YearMonth, grid: List<CalendarCell>,
    selectedDate: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onSelect: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Month navigation
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, null, tint = Color(0xFF555555)) }
            Text("${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF555555)) }
        }
        // Day headers
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEach { d ->
                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp))
            }
        }
        // Calendar grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(grid) { cell -> CalendarDayCell(cell, selected = cell.date == selectedDate, onSelect = { cell.date?.let(onSelect) }) }
        }
    }
}

@Composable
private fun CalendarDayCell(cell: CalendarCell, selected: Boolean, onSelect: () -> Unit) {
    if (cell.isEmpty) { Box(Modifier.aspectRatio(1f)) { }; return }

    val bg = when {
        selected           -> Color.White
        cell.hasFocus      -> Color.White.copy(alpha = 0.08f + cell.intensity * 0.55f)
        else               -> Color(0xFF0A0A0A)
    }
    val border = when {
        cell.isToday && !selected -> Color(0xFF333333)
        else -> Color.Transparent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
            .background(bg).border(1.dp, border, RoundedCornerShape(8.dp)).clickable(onClick = onSelect)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "${cell.date!!.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = when { selected -> Color.Black; cell.hasFocus -> Color.White; else -> Color(0xFF444444) },
                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            )
            if (cell.hasFocus) {
                Box(Modifier.size(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Color.Black else Color.White.copy(.6f)))
            }
        }
    }
}

@Composable
private fun WeekView(days: List<CalendarDayEntity>) {
    val dow = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This Week", style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF555555), letterSpacing = 1.sp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                val maxH = (days.maxOfOrNull { it.totalFocusMinutes } ?: 1).coerceAtLeast(1)
                val frac = d.totalFocusMinutes.toFloat() / maxH
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dow.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
                    Box(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0A0A0A)), contentAlignment = Alignment.BottomCenter) {
                        if (frac > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(frac).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.15f + frac * 0.6f)))
                    }
                    Text(if (d.totalFocusMinutes > 0) "${d.totalFocusMinutes}m" else "-",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 9.sp))
                }
            }
        }
    }
}

@Composable
private fun HeatmapView(data: List<CalendarDayEntity>) {
    val byDay = data.associateBy { it.dateEpochDay }
    val today = LocalDate.now()
    val end   = today
    val start = today.minusDays(364)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Last 365 Days", style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF555555), letterSpacing = 1.sp))

        // Contribution-style grid: 53 cols × 7 rows
        val cells = (0..364).map { start.plusDays(it.toLong()) }
        val weeks = cells.chunked(7)

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { date ->
                        val d    = byDay[date.toEpochDay()]
                        val intensity = d?.intensityScore ?: 0f
                        Box(
                            Modifier.size(11.dp).clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (intensity > 0f) Color.White.copy(alpha = 0.1f + intensity * 0.7f)
                                    else Color(0xFF0D0D0D)
                                )
                        )
                    }
                }
            }
        }

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
            listOf(0.05f, 0.2f, 0.4f, 0.65f, 0.9f).forEach { alpha ->
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = alpha)))
            }
            Text("More", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
        }
    }
}

@Composable
private fun DayDetail(date: LocalDate, history: List<com.focussessions.app.data.local.database.entities.FocusHistoryEntity>) {
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(date.format(fmt), style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF555555)))
            history.forEach { h ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (h.sessionTitle.isNotEmpty()) h.sessionTitle else "Focus session",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888888)))
                    Text("${h.actualFocusMinutes}m", style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                }
            }
            HorizontalDivider(color = Color(0xFF111111))
            val total = history.sumOf { it.actualFocusMinutes }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF555555)))
                Text("${total}m", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
