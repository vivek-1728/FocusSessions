package com.focussessions.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.data.local.database.entities.DailyProgressEntity
import com.focussessions.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun DailyProgressCard(
    progress: DailyProgressEntity?,
    dailyGoalMinutes: Int,
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    val focusMinutes  = progress?.focusMinutes ?: 0
    val sessionsCount = progress?.sessionsCount ?: 0
    // Cap goal at 1080 min (18h) for display purposes
    val cappedGoal    = dailyGoalMinutes.coerceIn(30, 1080)
    val goalProgress  = (focusMinutes.toFloat() / cappedGoal.toFloat()).coerceIn(0f, 1f)

    val animatedGoalProgress by animateFloatAsState(
        targetValue   = goalProgress,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label         = "daily_progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = BackgroundCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "Today",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                if (streakCount > 0) {
                    StreakChip(streakCount)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small circular progress ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.size(80.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 7.dp.toPx()
                        val radius  = (size.minDimension / 2f) - strokeW
                        val topLeft = Offset(center.x - radius, center.y - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)

                        // Track
                        drawArc(
                            color      = Color(0xFF1A1A1A),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter  = false,
                            topLeft    = topLeft,
                            size       = arcSize,
                            style      = Stroke(strokeW, cap = StrokeCap.Round)
                        )
                        // Progress — solid white on AMOLED
                        if (animatedGoalProgress > 0f) {
                            drawArc(
                                color      = AccentPrimary,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedGoalProgress,
                                useCenter  = false,
                                topLeft    = topLeft,
                                size       = arcSize,
                                style      = Stroke(strokeW, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text  = "${(goalProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color      = AccentPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp
                        )
                    )
                }

                Spacer(Modifier.width(20.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow(Icons.Rounded.Timer,             "Focused",  formatMinutes(focusMinutes))
                    StatRow(Icons.Rounded.CheckCircleOutline,"Sessions", "$sessionsCount")
                    StatRow(Icons.Rounded.Flag,              "Goal",     formatMinutes(cappedGoal))
                }
            }

            // Progress bar
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress   = { animatedGoalProgress },
                modifier   = Modifier.fillMaxWidth().height(4.dp),
                color      = AccentPrimary,
                trackColor = Color(0xFF1A1A1A),
                strokeCap  = StrokeCap.Round
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "${formatMinutes(focusMinutes)} done",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
                Text(
                    text  = "${formatMinutes((cappedGoal - focusMinutes).coerceAtLeast(0))} left",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = TextSecondary,
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
        Spacer(Modifier.weight(1f))
        Text(
            text  = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color      = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun StreakChip(count: Int) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier              = Modifier
            .background(
                color = BackgroundElevated,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = "🔥", fontSize = 12.sp)
        Text(
            text  = "$count day streak",
            style = MaterialTheme.typography.labelSmall.copy(
                color      = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// Helper: show as "1h 20m", "45m", "2h", etc.
fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0   -> "${m}m"
        m == 0   -> "${h}h"
        else     -> "${h}h ${m}m"
    }
}
