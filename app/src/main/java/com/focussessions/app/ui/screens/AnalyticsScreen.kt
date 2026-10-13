package com.focussessions.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focussessions.app.viewmodel.AnalyticsTab
import com.focussessions.app.viewmodel.AnalyticsViewModel

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .statusBarsPadding().verticalScroll(scroll)
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Analytics", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Rounded.Refresh, null, tint = Color(0xFF444444))
            }
        }

        // Tab row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsTab.entries.forEach { tab ->
                val sel = tab == state.tab
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (sel) Color.White else Color(0xFF0D0D0D))
                    .clickable { viewModel.setTab(tab) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (sel) Color.Black else Color(0xFF555555),
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        ))
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            }
        } else {
            when (state.tab) {
                AnalyticsTab.DAILY   -> DailyTab(state.daily)
                AnalyticsTab.WEEKLY  -> WeeklyTab(state.weekly)
                AnalyticsTab.MONTHLY -> MonthlyTab(state.monthly)
            }
        }

        // Insights
        if (state.insights.isNotEmpty()) {
            InsightsSection(state.insights)
        }
    }
}

@Composable
private fun DailyTab(d: com.focussessions.app.calendar.DailyAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("TODAY")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Focus Time", fmtMin(d.totalFocusMinutes), Icons.Rounded.Timer, Modifier.weight(1f))
            StatTile("Sessions",   "${d.sessionCount}",          Icons.Rounded.Repeat, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Longest",    fmtMin(d.longestSessionMinutes), Icons.Rounded.EmojiEvents, Modifier.weight(1f))
            StatTile("Break Time", fmtMin(d.totalBreakMinutes),     Icons.Rounded.Coffee,     Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeeklyTab(w: com.focussessions.app.calendar.WeeklyAnalytics) {
    val maxVal = (w.perDay.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("THIS WEEK")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Total",     fmtMin(w.totalFocusMinutes),   Icons.Rounded.Timer,      Modifier.weight(1f))
            StatTile("Best Day",  w.mostProductiveDay,            Icons.Rounded.Star,       Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Daily Avg", fmtMin(w.averageDailyMinutes), Icons.Rounded.Insights,   Modifier.weight(1f))
            StatTile("Streak",    "${w.streak}d",                 Icons.Rounded.Whatshot,  Modifier.weight(1f))
        }
        // Bar chart
        SectionLabel("DAILY BREAKDOWN")
        BarChart(w.perDay, maxVal)
    }
}

@Composable
private fun BarChart(data: Map<String, Int>, maxVal: Int) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            data.forEach { (label, mins) ->
                val frac = mins.toFloat() / maxVal
                val animFrac by animateFloatAsState(frac, tween(600, easing = EaseOutCubic), label = "bar_$label")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (mins > 0) Text("${mins}m", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF555555), fontSize = 8.sp))
                    Box(Modifier.width(28.dp).height(80.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(28.dp).fillMaxHeight(animFrac.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Color.White.copy(alpha = if (frac > 0f) 0.12f + frac * 0.7f else 0.05f)))
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
                }
            }
        }
    }
}

@Composable
private fun MonthlyTab(m: com.focussessions.app.calendar.MonthlyAnalytics) {
    val animConsistency by animateFloatAsState(m.consistencyPercent / 100f, tween(800, easing = EaseOutCubic), label = "consistency")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("THIS MONTH")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Total Focus", fmtMin(m.totalFocusMinutes), Icons.Rounded.Timer,         Modifier.weight(1f))
            StatTile("Active Days", "${m.activeDays}/${m.totalDaysInMonth}", Icons.Rounded.CalendarMonth, Modifier.weight(1f))
        }
        // Consistency ring
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val sw = 6.dp.toPx(); val r = size.minDimension / 2f - sw
                        val tl = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r)
                        val sz = androidx.compose.ui.geometry.Size(r*2f, r*2f)
                        drawArc(Color(0xFF111111), -90f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        if (animConsistency > 0f) drawArc(Color.White, -90f, 360f * animConsistency, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Text("${m.consistencyPercent}%", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                }
                Column {
                    Text("Focus Consistency", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                    Text("You focused on ${m.activeDays} of ${m.totalDaysInMonth} days this month",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444)))
                }
            }
        }
    }
}

@Composable
private fun InsightsSection(insights: List<com.focussessions.app.data.local.database.entities.ProductivityInsightEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("INSIGHTS")
        insights.forEach { insight ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 18.sp)
                    Text(insight.insightText, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF888888)))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text,
    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), letterSpacing = 1.sp, fontSize = 10.sp))

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0A0A)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = Color(0xFF333333), modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 10.sp))
    }
}

private fun fmtMin(m: Int): String { val h = m/60; val min = m%60; return when { h==0->"${m}m"; min==0->"${h}h"; else->"${h}h ${min}m" } }
