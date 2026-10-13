package com.focussessions.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focussessions.app.domain.model.UserPreferences
import com.focussessions.app.ui.components.*
import com.focussessions.app.ui.theme.*
import com.focussessions.app.viewmodel.FocusViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    preferences: UserPreferences,
    viewModel: FocusViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = BackgroundCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BackgroundOverlay)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "Settings",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color      = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // ── Timer ─────────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Timer, title = "Timer") {
                Text(
                    text  = "Focus duration",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextMuted)
                )
                Spacer(Modifier.height(6.dp))
                PresetChips(
                    selectedMinutes = preferences.focusDurationMinutes,
                    onSelect        = viewModel::setFocusDuration
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DurationPicker(
                        label         = "Focus",
                        valueMinutes  = preferences.focusDurationMinutes,
                        minValue      = 5,
                        maxValue      = 120,
                        step          = 5,
                        onValueChange = viewModel::setFocusDuration,
                        modifier      = Modifier.weight(1f)
                    )
                    DurationPicker(
                        label         = "Short break",
                        valueMinutes  = preferences.shortBreakMinutes,
                        minValue      = 1,
                        maxValue      = 30,
                        step          = 1,
                        onValueChange = viewModel::setShortBreak,
                        modifier      = Modifier.weight(1f)
                    )
                    DurationPicker(
                        label         = "Long break",
                        valueMinutes  = preferences.longBreakMinutes,
                        minValue      = 5,
                        maxValue      = 60,
                        step          = 5,
                        onValueChange = viewModel::setLongBreak,
                        modifier      = Modifier.weight(1f)
                    )
                }
            }

            // ── Automation ────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.AutoMode, title = "Automation") {
                SettingToggleRow(
                    title           = "Auto-start breaks",
                    subtitle        = "Begin break + music when focus ends",
                    checked         = preferences.autoStartBreaks,
                    onCheckedChange = viewModel::setAutoStartBreaks
                )
                HorizontalDivider(color = BackgroundOverlay, modifier = Modifier.padding(vertical = 8.dp))
                SettingToggleRow(
                    title           = "Auto-start focus",
                    subtitle        = "Resume focus session when break ends",
                    checked         = preferences.autoStartFocus,
                    onCheckedChange = { /* viewModel.setAutoStartFocus(it) */ }
                )
            }

            // ── Daily Goal (1h – 18h) ─────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Flag, title = "Daily Goal  ·  max 18h") {
                Text(
                    text  = "Current: ${formatMinutes(preferences.dailyGoalMinutes)}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(Modifier.height(8.dp))
                // 1h → 18h presets in a scrollable row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(18) { idx ->
                        val preset   = (idx + 1) * 60          // 60, 120 … 1080
                        val selected = preset == preferences.dailyGoalMinutes
                        FilterChip(
                            selected = selected,
                            onClick  = { viewModel.setDailyGoal(preset) },
                            label    = {
                                Text(
                                    text  = "${idx + 1}h",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPrimary,
                                selectedLabelColor     = BackgroundDeep,
                                containerColor         = BackgroundElevated,
                                labelColor             = TextSecondary
                            )
                        )
                    }
                }
            }

            // ── Music ─────────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.LibraryMusic, title = "Music") {
                SettingToggleRow(
                    title           = "Enable music",
                    subtitle        = "Auto-plays during breaks, muted during focus",
                    checked         = preferences.musicEnabled,
                    onCheckedChange = viewModel::setMusicEnabled
                )

                // Info card explaining the music rule
                Spacer(Modifier.height(4.dp))
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = BackgroundOverlay)
                ) {
                    Row(
                        modifier              = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.Top
                    ) {
                        Icon(
                            imageVector        = Icons.Rounded.Info,
                            contentDescription = null,
                            tint               = TextMuted,
                            modifier           = Modifier.size(15.dp).padding(top = 2.dp)
                        )
                        Text(
                            text  = "Music starts automatically when a break begins " +
                                    "and stops when focus resumes. Select a track from " +
                                    "the main screen music bar.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color    = TextMuted,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // ── Display ───────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Brightness4, title = "Display") {
                SettingToggleRow(
                    title           = "Keep screen on",
                    subtitle        = "Screen stays awake during sessions (set in app)",
                    checked         = true,
                    onCheckedChange = { /* always on via FLAG_KEEP_SCREEN_ON in MainActivity */ }
                )
                HorizontalDivider(color = BackgroundOverlay, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text  = "AMOLED black theme",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                        )
                        Text(
                            text  = "True black — unlit pixels save battery",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                    Text(
                        text  = "ON",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // ── Notifications ─────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Notifications, title = "Notifications") {
                SettingToggleRow(
                    title           = "Session notifications",
                    subtitle        = "Alert when focus or break sessions complete",
                    checked         = preferences.notificationsEnabled,
                    onCheckedChange = { /* viewModel.setNotificationsEnabled(it) */ }
                )
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection(icon = Icons.Rounded.Info, title = "About") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Focus Sessions", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    Text("v1.0.0",         style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Inspired by Windows 11 Clock · AMOLED optimised",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = TextSecondary,
                modifier           = Modifier.size(15.dp)
            )
            Text(
                text  = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color         = TextSecondary,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            )
        }
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundElevated)
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content             = content
            )
        }
    }
}

// Shared helper
private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60; val m = minutes % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h ${m}m" }
}
