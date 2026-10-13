package com.focussessions.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.timer.CycleState
import com.focussessions.app.timer.SessionPhase
import com.focussessions.app.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dual-ring circular timer:
 *   Outer ring — current session countdown (focus or break)
 *   Inner ring — overall cycle progress toward target
 */
@Composable
fun CycleProgressRing(
    state: CycleState,
    size: Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    // Animate session arc
    val sessionProgress by animateFloatAsState(
        targetValue   = state.currentSessionProgressFraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label         = "session_arc"
    )

    // Animate overall arc
    val overallProgress by animateFloatAsState(
        targetValue   = state.liveOverallProgressFraction,
        animationSpec = tween(900, easing = EaseOutCubic),
        label         = "overall_arc"
    )

    // Session ring colour: white=focus, grey=break, dim=paused
    val sessionColor by animateColorAsState(
        targetValue = when {
            state.isCompleted              -> Color.White
            state.isPaused                 -> Color(0xFF555555)
            state.phase == SessionPhase.FOCUS  -> Color.White
            state.phase == SessionPhase.BREAK  -> Color(0xFF888888)
            else                           -> Color(0xFF444444)
        },
        animationSpec = tween(500),
        label = "session_color"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerStroke = 11.dp.toPx()
            val innerStroke = 7.dp.toPx()
            val gap         = 20.dp.toPx()

            val outerRadius = this.size.minDimension / 2f - outerStroke
            val innerRadius = outerRadius - outerStroke - gap - innerStroke

            // ── Inner ring: overall cycle progress ────────────────────────────
            drawProgressArc(
                center     = center,
                radius     = innerRadius,
                stroke     = innerStroke,
                trackColor = Color(0xFF1A1A1A),
                arcColor   = Color(0xFF666666),
                progress   = overallProgress,
                hasGlow    = false
            )

            // ── Outer ring: current session countdown ─────────────────────────
            drawProgressArc(
                center     = center,
                radius     = outerRadius,
                stroke     = outerStroke,
                trackColor = Color(0xFF111111),
                arcColor   = sessionColor,
                progress   = sessionProgress,
                hasGlow    = state.isRunning
            )

            // Tick marks on outer ring
            drawTickMarks(center, outerRadius, outerStroke, sessionColor)

            // Black fill inside inner ring
            drawCircle(
                color  = Color.Black,
                radius = innerRadius - innerStroke / 2f - 4.dp.toPx(),
                center = center
            )
        }

        // ── Text content ──────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            // Phase + cycle label
            Text(
                text  = when (state.phase) {
                    SessionPhase.IDLE      -> "READY"
                    SessionPhase.FOCUS     -> "FOCUS"
                    SessionPhase.BREAK     -> "BREAK"
                    SessionPhase.COMPLETED -> "DONE"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color         = if (state.phase == SessionPhase.BREAK) Color(0xFF888888) else Color(0xFF666666),
                    letterSpacing = 2.sp
                )
            )

            Spacer(Modifier.height(2.dp))

            // Main countdown
            Text(
                text  = if (state.isIdle) "--:--" else state.displayTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight    = FontWeight.Thin,
                    color         = when {
                        state.isCompleted -> Color.White
                        state.isPaused    -> Color(0xFF666666)
                        else              -> Color.White
                    },
                    letterSpacing = (-4).sp,
                    fontSize      = if (size >= 280.dp) 54.sp else 40.sp
                )
            )

            Spacer(Modifier.height(4.dp))

            // Cycle count
            if (state.currentCycleNumber > 0 && !state.isCompleted) {
                Text(
                    text  = "Cycle ${state.currentCycleNumber} of ${state.totalCycles}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color    = Color(0xFF555555),
                        fontSize = 11.sp
                    )
                )
            }

            // Status
            Text(
                text  = when {
                    state.isCompleted  -> "Target reached!"
                    state.isPaused     -> "Paused"
                    state.phase == SessionPhase.FOCUS  -> "No music · stay focused"
                    state.phase == SessionPhase.BREAK  -> "Rest · music on"
                    else               -> "Tap ▶ to begin"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = Color(0xFF444444),
                    fontSize = 10.sp
                )
            )
        }
    }
}

// ── Canvas helpers ────────────────────────────────────────────────────────────

private fun DrawScope.drawProgressArc(
    center: Offset,
    radius: Float,
    stroke: Float,
    trackColor: Color,
    arcColor: Color,
    progress: Float,
    hasGlow: Boolean
) {
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    // Subtle glow (minimal — saves GPU on AMOLED)
    if (hasGlow && progress > 0f) {
        drawCircle(
            color  = arcColor.copy(alpha = 0.06f),
            radius = radius + stroke,
            center = center
        )
    }

    // Track
    drawArc(
        color      = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter  = false,
        topLeft    = topLeft,
        size       = arcSize,
        style      = Stroke(stroke, cap = StrokeCap.Round)
    )

    // Progress
    if (progress > 0f) {
        drawArc(
            color      = arcColor,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter  = false,
            topLeft    = topLeft,
            size       = arcSize,
            style      = Stroke(stroke, cap = StrokeCap.Round)
        )

        // Tip dot
        val tipAngle = Math.toRadians((-90.0 + 360.0 * progress))
        val tx = center.x + radius * cos(tipAngle).toFloat()
        val ty = center.y + radius * sin(tipAngle).toFloat()
        drawCircle(color = arcColor,             radius = stroke / 2f,   center = Offset(tx, ty))
        drawCircle(color = Color.White.copy(.4f), radius = stroke / 3.5f, center = Offset(tx, ty))
    }
}

private fun DrawScope.drawTickMarks(
    center: Offset,
    radius: Float,
    strokeWidth: Float,
    color: Color
) {
    val inner = radius - strokeWidth - 8.dp.toPx()
    val outer = radius - strokeWidth - 2.dp.toPx()
    for (i in 0 until 60) {
        val a       = Math.toRadians(i * 6.0 - 90.0)
        val isMajor = i % 5 == 0
        val startR  = if (isMajor) inner - 3.dp.toPx() else inner
        drawLine(
            color       = color.copy(alpha = if (isMajor) 0.18f else 0.07f),
            start       = Offset(center.x + startR * cos(a).toFloat(), center.y + startR * sin(a).toFloat()),
            end         = Offset(center.x + outer * cos(a).toFloat(),  center.y + outer * sin(a).toFloat()),
            strokeWidth = if (isMajor) 1.4.dp.toPx() else 0.5.dp.toPx()
        )
    }
}
