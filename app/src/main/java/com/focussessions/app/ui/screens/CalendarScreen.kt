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
import com.focussessions.app.data.local.database.entities.CalendarDayEntity
import com.focussessions.app.viewmodel.CalendarViewMode
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Calendar",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            ViewModeChips(mode = state.viewMode, onSelect = viewModel::setViewMode)
        }

        when (state.viewMode) {
            CalendarViewMode.MONTH -> MonthView(
                state.currentYearMonth,
                state.monthGrid,
                state.selectedDate,
                viewModel::previousMonth,
                viewModel::nextMonth,
                viewModel::selectDate
            )
            CalendarViewMode.WEEK -> WeekView(state.weekDays)
            CalendarViewMode.HEATMAP -> HeatmapView(
                data = state.heatmapData,
                selectedDate = state.selectedDate,
                onSelectDate = viewModel::selectDate
            )
            else -> {}
        }

        // Selected day detail card (works for both focused and non-focused days)
        DayDetail(date = state.selectedDate, history = state.selectedDayHistory)
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
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Color.White else Color(0xFF111111))
                    .clickable { onSelect(m) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (sel) Color.Black else Color(0xFF555555),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
private fun MonthView(
    ym: java.time.YearMonth,
    grid: List<CalendarCell>,
    selectedDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Month navigation
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, null, tint = Color(0xFF555555)) }
            Text(
                "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
            )
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF555555)) }
        }
        // Day headers
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp)
                )
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
            items(grid) { cell ->
                CalendarDayCell(cell, selected = cell.date == selectedDate, onSelect = { cell.date?.let(onSelect) })
            }
        }
    }
}

@Composable
private fun CalendarDayCell(cell: CalendarCell, selected: Boolean, onSelect: () -> Unit) {
    if (cell.isEmpty) { Box(Modifier.aspectRatio(1f)) {}; return }

    val bg = when {
        selected -> Color.White
        cell.hasFocus -> Color.White.copy(alpha = 0.08f + cell.intensity * 0.55f)
        else -> Color(0xFF0A0A0A)
    }
    val border = when {
        cell.isToday && !selected -> Color(0xFF333333)
        else -> Color.Transparent
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${cell.date!!.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = when {
                        selected -> Color.Black
                        cell.hasFocus -> Color.White
                        else -> Color(0xFF444444)
                    },
                    fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            )
            if (cell.hasFocus) {
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Color.Black else Color.White.copy(.6f))
                )
            }
        }
    }
}

@Composable
private fun WeekView(days: List<CalendarDayEntity>) {
    val dow = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This Week", style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF555555), letterSpacing = 1.sp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                val maxH = (days.maxOfOrNull { it.totalFocusMinutes } ?: 1).coerceAtLeast(1)
                val frac = d.totalFocusMinutes.toFloat() / maxH
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(dow.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0A0A)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (frac > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(frac)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.15f + frac * 0.6f))
                            )
                        }
                    }
                    Text(
                        if (d.totalFocusMinutes > 0) "${d.totalFocusMinutes}m" else "-",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapView(
    data: List<CalendarDayEntity>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    val byDay = data.associateBy { it.dateEpochDay }
    val today = LocalDate.now()
    val start = today.minusDays(364)

    val activeDaysCount = data.count { it.totalFocusMinutes > 0 }
    val totalFocusMins = data.sumOf { it.totalFocusMinutes }

    val horizontalScrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        horizontalScrollState.scrollTo(horizontalScrollState.maxValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last 365 Days",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "📅 $activeDaysCount active days",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF666666), fontSize = 11.sp)
                )
            }
        }

        // Coding Platform Style 365-Day Contribution Heatmap Grid
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val cells = (0..364).map { start.plusDays(it.toLong()) }
                    val weeks = cells.chunked(7)

                    weeks.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { date ->
                                val d = byDay[date.toEpochDay()]
                                val mins = d?.totalFocusMinutes ?: 0
                                val isSelected = date == selectedDate
                                val isToday = date == today

                                val cellColor = when {
                                    mins <= 0 -> Color(0xFF161B22)
                                    mins in 1..20 -> Color(0xFF0E4429)
                                    mins in 21..45 -> Color(0xFF006D32)
                                    mins in 46..90 -> Color(0xFF26A641)
                                    else -> Color(0xFF39D353)
                                }

                                val borderColor = when {
                                    isSelected -> Color.White
                                    isToday -> Color(0xFF555555)
                                    mins <= 0 -> Color(0xFF21262D)
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(cellColor)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = borderColor,
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                        .clickable { onSelectDate(date) }
                                )
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total: ${formatMinutes(totalFocusMins)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF555555), fontSize = 10.sp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Less", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 9.sp))
                        listOf(
                            Color(0xFF161B22),
                            Color(0xFF0E4429),
                            Color(0xFF006D32),
                            Color(0xFF26A641),
                            Color(0xFF39D353)
                        ).forEach { color ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                                    .border(1.dp, Color(0xFF21262D), RoundedCornerShape(2.dp))
                            )
                        }
                        Text("More", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 9.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    history: List<com.focussessions.app.data.local.database.entities.FocusHistoryEntity>
) {
    val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(date.format(fmt), style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF888888), fontWeight = FontWeight.SemiBold))

            if (history.isEmpty()) {
                Text(
                    text = "No focus sessions recorded on this day.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444))
                )
            } else {
                history.forEach { h ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (h.sessionTitle.isNotEmpty()) h.sessionTitle else "Focus session",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCCCCCC))
                        )
                        Text("${h.actualFocusMinutes}m", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }
                HorizontalDivider(color = Color(0xFF1A1A1A))
                val total = history.sumOf { it.actualFocusMinutes }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Focus Time", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF666666)))
                    Text(formatMinutes(total), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

private fun formatMinutes(m: Int): String {
    val h = m / 60
    val min = m % 60
    return when {
        h == 0 -> "${m}m"
        min == 0 -> "${h}h"
        else -> "${h}h ${min}m"
    }
}
