package com.focussessions.app.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.timer.CycleState
import com.focussessions.app.timer.SessionPhase
import com.focussessions.app.ui.theme.*

/**
 * Horizontal row showing 3 key cycle stats below the ring.
 * Compact — does not scroll.
 */
@Composable
fun CycleStatsRow(
    state: CycleState,
    modifier: Modifier = Modifier
) {
    if (state.isIdle) return

    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            icon     = Icons.Rounded.Timer,
            label    = "Done",
            value    = state.formatCompletedFocus(),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon     = Icons.Rounded.HourglassBottom,
            label    = "Left",
            value    = state.formatRemainingFocus(),
            modifier = Modifier.weight(1f),
            highlight = state.remainingFocusSeconds < 60 * 10  // < 10 min left
        )
        StatCard(
            icon     = Icons.Rounded.Repeat,
            label    = "Cycle",
            value    = "${state.currentCycleNumber}/${state.totalCycles}",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundCard)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (highlight) Color.White else Color(0xFF555555),
            modifier           = Modifier.size(14.dp)
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.labelLarge.copy(
                color      = if (highlight) Color.White else Color(0xFF888888),
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp
            )
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color    = Color(0xFF444444),
                fontSize = 10.sp
            )
        )
    }
}

// ── Overall progress bar shown below stats ────────────────────────────────────
@Composable
fun OverallProgressBar(
    state: CycleState,
    modifier: Modifier = Modifier
) {
    if (state.isIdle) return

    val animatedProgress by animateFloatAsState(
        targetValue   = state.liveOverallProgressFraction,
        animationSpec = tween(800, easing = EaseOutCubic),
        label         = "bar_progress"
    )

    Column(
        modifier            = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text  = "Overall progress",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = Color(0xFF444444),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text  = "${(animatedProgress * 100).toInt()}%  ·  ${state.targetFocusMinutes / 60}h target",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = Color(0xFF555555),
                    fontSize = 10.sp
                )
            )
        }

        // Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF111111))
        ) {
            // Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}
