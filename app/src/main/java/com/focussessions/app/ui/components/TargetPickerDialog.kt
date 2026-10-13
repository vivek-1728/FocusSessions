package com.focussessions.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focussessions.app.ui.theme.*

/**
 * Full-screen-ish dialog for picking a total target focus duration.
 * Shown when user taps the START button from IDLE state.
 */
@Composable
fun TargetPickerDialog(
    currentTargetMinutes: Int,
    focusDurationMinutes: Int,
    breakDurationMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(currentTargetMinutes) }

    // Preset targets: 30m up to 18h
    val presets = remember {
        listOf(
            30, 60, 90, 120, 150, 180,
            240, 300, 360, 420, 480, 540,
            600, 660, 720, 840, 960, 1080
        )
    }

    // Derived cycle count
    val cycleCount = (selectedMinutes + focusDurationMinutes - 1) / focusDurationMinutes
    val totalWithBreaks = selectedMinutes + (cycleCount - 1) * breakDurationMinutes

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape  = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundCard)
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text  = "Set Focus Target",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color      = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text  = "How long do you want to focus today?",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF555555))
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color(0xFF555555))
                    }
                }

                // Selected value display
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BackgroundElevated)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text  = formatTarget(selectedMinutes),
                            style = MaterialTheme.typography.displaySmall.copy(
                                color      = Color.White,
                                fontWeight = FontWeight.Thin,
                                letterSpacing = (-2).sp
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = "≈ $cycleCount × ${focusDurationMinutes}m focus + ${cycleCount - 1} × ${breakDurationMinutes}m break",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF444444),
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text  = "Total wall-clock time: ~${formatTarget(totalWithBreaks)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color    = Color(0xFF444444),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Preset grid
                Text(
                    text  = "QUICK SELECT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = Color(0xFF444444),
                        letterSpacing = 1.sp,
                        fontSize      = 10.sp
                    )
                )

                LazyVerticalGrid(
                    columns             = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    modifier            = Modifier.heightIn(max = 280.dp)
                ) {
                    items(presets) { preset ->
                        val isSelected = preset == selectedMinutes
                        Box(
                            modifier         = Modifier
                                .aspectRatio(2.2f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color.White else BackgroundElevated)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { selectedMinutes = preset },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text      = formatTarget(preset),
                                style     = MaterialTheme.typography.labelMedium.copy(
                                    color      = if (isSelected) Color.Black else Color(0xFF666666),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Fine-tune stepper (+/- 30 min)
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text  = "Fine-tune:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444))
                    )
                    Spacer(Modifier.weight(1f))
                    listOf(-60, -30, +30, +60).forEach { delta ->
                        val label = (if (delta > 0) "+" else "") + formatTarget(delta)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BackgroundElevated)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) {
                                    selectedMinutes = (selectedMinutes + delta).coerceIn(30, 1080)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = label,
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF777777))
                            )
                        }
                    }
                }

                // Confirm button
                Button(
                    onClick  = { onConfirm(selectedMinutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor   = Color.Black
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = "Start  ${formatTarget(selectedMinutes)} Focus Cycle",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

private fun formatTarget(minutes: Int): String {
    val abs = kotlin.math.abs(minutes)
    val h   = abs / 60
    val m   = abs % 60
    val sign = if (minutes < 0) "-" else ""
    return when {
        h == 0   -> "${sign}${m}m"
        m == 0   -> "${sign}${h}h"
        else     -> "${sign}${h}h ${m}m"
    }
}
