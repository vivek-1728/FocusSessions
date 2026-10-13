package com.focussessions.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.timer.CycleState
import com.focussessions.app.ui.theme.*

/**
 * Full-screen completion overlay shown when the user finishes their target.
 * Pure black background — every pixel that can be off is off.
 */
@Composable
fun CompletionScreen(
    state: CycleState,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Pulsing scale for the checkmark
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 0.97f,
        targetValue   = 1.03f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600)) + scaleIn(tween(600, easing = EaseOutBack))
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // Big checkmark
                Box(
                    modifier         = Modifier
                        .size(100.dp)
                        .scale(pulse)
                        .background(Color.White, shape = RoundedCornerShape(50.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Check,
                        contentDescription = null,
                        tint               = Color.Black,
                        modifier           = Modifier.size(56.dp)
                    )
                }

                // Headline
                Text(
                    text      = "Target\nComplete",
                    style     = MaterialTheme.typography.displaySmall.copy(
                        color         = Color.White,
                        fontWeight    = FontWeight.Thin,
                        letterSpacing = (-1.5).sp,
                        lineHeight    = 44.sp
                    ),
                    textAlign = TextAlign.Center
                )

                // Sub-label
                Text(
                    text  = "You reached your focus goal.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF555555)
                    )
                )

                Spacer(Modifier.height(8.dp))

                // ── Stats cards ───────────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompletionStatCard(
                        label    = "Focused",
                        value    = state.formatCompletedFocus(),
                        modifier = Modifier.weight(1f)
                    )
                    CompletionStatCard(
                        label    = "Cycles",
                        value    = "${state.currentCycleNumber}",
                        modifier = Modifier.weight(1f)
                    )
                    CompletionStatCard(
                        label    = "Breaks",
                        value    = formatBreaks(state.totalBreakMinutes),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Motivational message based on duration
                val message = remember(state.completedFocusMinutesDisplay) {
                    when {
                        state.completedFocusMinutesDisplay >= 480 -> "8+ hours. Legendary. 🏆"
                        state.completedFocusMinutesDisplay >= 300 -> "5+ hours of deep work. Outstanding!"
                        state.completedFocusMinutesDisplay >= 180 -> "3+ hours focused. Excellent work."
                        state.completedFocusMinutesDisplay >= 120 -> "2 hours of focus. Great session!"
                        else                                       -> "Good session. Keep building the habit."
                    }
                }
                Text(
                    text      = message,
                    style     = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444)),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                // Dismiss button
                Button(
                    onClick  = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color.Black
                    )
                ) {
                    Text(
                        text  = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Start new cycle
                TextButton(onClick = onDismiss) {
                    Text(
                        text  = "Start another cycle",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444))
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .background(Color(0xFF0A0A0A), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text      = value,
            style     = MaterialTheme.typography.titleMedium.copy(
                color      = Color.White,
                fontWeight = FontWeight.SemiBold
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444)),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatBreaks(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h${m}m" }
}
