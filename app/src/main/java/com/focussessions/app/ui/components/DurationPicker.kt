package com.focussessions.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.ui.theme.*

val FOCUS_PRESETS = listOf(15, 25, 30, 45, 50, 60, 90)

@Composable
fun DurationPicker(
    label: String,
    valueMinutes: Int,
    minValue: Int = 1,
    maxValue: Int = 120,
    step: Int = 5,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StepButton(icon = Icons.Rounded.Remove, enabled = valueMinutes > minValue) {
                onValueChange((valueMinutes - step).coerceAtLeast(minValue))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(68.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BackgroundElevated)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text  = "$valueMinutes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color      = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 20.sp
                        )
                    )
                    Text(
                        text  = "m",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }

            StepButton(icon = Icons.Rounded.Add, enabled = valueMinutes < maxValue) {
                onValueChange((valueMinutes + step).coerceAtMost(maxValue))
            }
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) BackgroundElevated else BackgroundCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                enabled           = enabled,
                onClick           = onClick
            )
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (enabled) TextPrimary else TextMuted,
            modifier           = Modifier.size(16.dp)
        )
    }
}

// ── Preset chips for focus duration ──────────────────────────────────────────
@Composable
fun PresetChips(
    selectedMinutes: Int,
    presets: List<Int> = FOCUS_PRESETS,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.fillMaxWidth()
    ) {
        presets.forEach { preset ->
            val selected = preset == selectedMinutes
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) AccentPrimary else BackgroundElevated)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { onSelect(preset) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text  = "${preset}m",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color      = if (selected) BackgroundDeep else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

// ── Setting toggle row ────────────────────────────────────────────────────────
@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
            )
            if (subtitle != null) {
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = BackgroundDeep,
                checkedTrackColor   = AccentPrimary,   // white track when on
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BackgroundElevated
            )
        )
    }
}
