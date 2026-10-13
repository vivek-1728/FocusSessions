package com.focussessions.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focussessions.app.timer.SessionPhase
import com.focussessions.app.ui.components.*
import com.focussessions.app.ui.theme.*
import com.focussessions.app.viewmodel.FocusViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cycle    = uiState.cycleState
    val haptic   = LocalHapticFeedback.current

    val audioPermission = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // ── Completion overlay ────────────────────────────────────────────────────
    if (uiState.showCompletionScreen && uiState.completedCycleState != null) {
        CompletionScreen(
            state     = uiState.completedCycleState!!,
            onDismiss = viewModel::dismissCompletionScreen
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── App bar ───────────────────────────────────────────────────────
            AppBar(
                streakCount = uiState.preferences.streakCount,
                onSettings  = viewModel::showSettings
            )

            // ── Dual-ring timer ───────────────────────────────────────────────
            CycleProgressRing(
                state    = cycle,
                size     = 288.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ── Stats row ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = cycle.isActive || cycle.isCompleted,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CycleStatsRow(state = cycle)
                    OverallProgressBar(state = cycle)
                }
            }

            // ── Preset chips (idle only) ──────────────────────────────────────
            AnimatedVisibility(
                visible = cycle.isIdle,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                IdleHint(
                    targetMinutes   = uiState.preferences.dailyGoalMinutes,
                    focusMinutes    = uiState.preferences.focusDurationMinutes,
                    breakMinutes    = uiState.preferences.shortBreakMinutes,
                    onChangeDuration = viewModel::setFocusDuration
                )
            }

            // ── Control buttons ───────────────────────────────────────────────
            CycleControls(
                cycle    = cycle,
                onStart  = { viewModel.showTargetPicker() },
                onPause  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.pause()
                },
                onResume = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.resume()
                },
                onSkip   = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.skip()
                },
                onReset  = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.reset()
                }
            )

            // ── Tasks ─────────────────────────────────────────────────────────
            TaskSection(
                tasks        = uiState.tasks,
                onAddTask    = viewModel::addTask,
                onToggleTask = viewModel::toggleTask,
                onDeleteTask = viewModel::deleteTask
            )

            // ── Music section ─────────────────────────────────────────────────
            MusicSection(
                isPlaying    = uiState.isPlaying,
                currentTrack = uiState.currentTrack,
                musicEnabled = uiState.preferences.musicEnabled,
                isFocusPhase = cycle.phase == SessionPhase.FOCUS,
                onOpenPicker = {
                    if (audioPermission.status.isGranted) viewModel.showMusicPicker()
                    else audioPermission.launchPermissionRequest()
                },
                onPlayPause   = viewModel::toggleMusicPlayback,
                onNext        = viewModel::nextTrack,
                onPrevious    = viewModel::previousTrack,
                onToggleMusic = viewModel::setMusicEnabled
            )

            // ── Daily summary ─────────────────────────────────────────────────
            DailyProgressCard(
                progress         = uiState.todayProgress,
                dailyGoalMinutes = uiState.preferences.dailyGoalMinutes,
                streakCount      = uiState.preferences.streakCount
            )
        }
    }

    // ── Overlays / sheets ─────────────────────────────────────────────────────
    if (uiState.showTargetPicker) {
        TargetPickerDialog(
            currentTargetMinutes = uiState.preferences.dailyGoalMinutes,
            focusDurationMinutes = uiState.preferences.focusDurationMinutes,
            breakDurationMinutes = uiState.preferences.shortBreakMinutes,
            onConfirm            = viewModel::startCycleWithTarget,
            onDismiss            = viewModel::hideTargetPicker
        )
    }

    if (uiState.showMusicPicker) {
        MusicPickerDialog(
            tracks        = uiState.trackList,
            currentTrack  = uiState.currentTrack,
            onSelectTrack = viewModel::playTrack,
            onDismiss     = viewModel::hideMusicPicker
        )
    }

    if (uiState.showSettings) {
        SettingsBottomSheet(
            preferences = uiState.preferences,
            viewModel   = viewModel,
            onDismiss   = viewModel::hideSettings
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun AppBar(streakCount: Int, onSettings: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text  = "Focus",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text  = "Continuous cycle mode",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF444444))
            )
        }
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (streakCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔥", fontSize = 13.sp)
                        Text(
                            text  = "$streakCount",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color      = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, null, tint = Color(0xFF444444), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun IdleHint(
    targetMinutes: Int,
    focusMinutes: Int,
    breakMinutes: Int,
    onChangeDuration: (Int) -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text  = "CURRENT SETUP",
            style = MaterialTheme.typography.labelSmall.copy(
                color         = Color(0xFF444444),
                letterSpacing = 1.sp,
                fontSize      = 10.sp
            )
        )

        val cycles = (targetMinutes + focusMinutes - 1) / focusMinutes
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HintItem("Target",   formatMin(targetMinutes))
            HintItem("Focus",    "${focusMinutes}m each")
            HintItem("Break",    "${breakMinutes}m each")
            HintItem("Cycles",   "≈ $cycles")
        }

        Text(
            text  = "Tap ▶ below to set your target and start the cycle",
            style = MaterialTheme.typography.labelSmall.copy(
                color    = Color(0xFF333333),
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun HintItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFF777777),
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp)
        )
    }
}

private fun formatMin(m: Int): String {
    val h = m / 60; val min = m % 60
    return when { h == 0 -> "${m}m"; min == 0 -> "${h}h"; else -> "${h}h${min}m" }
}

@Composable
private fun CycleControls(
    cycle: com.focussessions.app.timer.CycleState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Reset
        AnimatedVisibility(visible = cycle.isActive, enter = scaleIn(), exit = scaleOut()) {
            SmallCtrlBtn(Icons.Rounded.Refresh, onClick = onReset)
        }

        // Primary
        PrimaryBtn(cycle = cycle, onStart = onStart, onPause = onPause, onResume = onResume)

        // Skip
        AnimatedVisibility(visible = cycle.isActive, enter = scaleIn(), exit = scaleOut()) {
            SmallCtrlBtn(Icons.Rounded.SkipNext, onClick = onSkip)
        }
    }
}

@Composable
private fun PrimaryBtn(
    cycle: com.focussessions.app.timer.CycleState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "run_glow")
    val glowScale by infiniteTransition.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    val bgColor = when {
        cycle.isCompleted     -> Color.White
        cycle.isPaused        -> Color(0xFF1A1A1A)
        cycle.phase == SessionPhase.BREAK -> Color(0xFF1A1A1A)
        cycle.isRunning       -> Color.White
        else                  -> Color.White   // idle
    }

    val icon = when {
        cycle.isCompleted -> Icons.Rounded.Replay
        cycle.isPaused    -> Icons.Rounded.PlayArrow
        cycle.isRunning   -> Icons.Rounded.Pause
        else              -> Icons.Rounded.PlayArrow
    }

    val iconTint = when {
        cycle.isPaused -> Color.White
        else           -> Color.Black
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .scale(if (cycle.isRunning) glowScale else 1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                when {
                    cycle.isCompleted -> onStart()
                    cycle.isRunning   -> onPause()
                    cycle.isPaused    -> onResume()
                    else              -> onStart()
                }
            }
    ) {
        AnimatedContent(
            targetState  = icon,
            transitionSpec = { scaleIn(spring(Spring.DampingRatioMediumBouncy)) togetherWith scaleOut(tween(120)) },
            label        = "primary_icon"
        ) { ic ->
            Icon(ic, null, tint = iconTint, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SmallCtrlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF0D0D0D))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick
            )
    ) {
        Icon(icon, null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun MusicSection(
    isPlaying: Boolean,
    currentTrack: com.focussessions.app.domain.model.MusicTrack?,
    musicEnabled: Boolean,
    isFocusPhase: Boolean,
    onOpenPicker: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleMusic: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.LibraryMusic, null, tint = Color(0xFF555555), modifier = Modifier.size(16.dp))
                Column {
                    Text(
                        text  = "Music",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text  = if (isFocusPhase) "Plays on break only" else "Playing during break",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF444444), fontSize = 10.sp)
                    )
                }
            }
            Switch(
                checked         = musicEnabled,
                onCheckedChange = onToggleMusic,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = Color.Black,
                    checkedTrackColor   = Color.White,
                    uncheckedThumbColor = Color(0xFF444444),
                    uncheckedTrackColor = Color(0xFF111111)
                )
            )
        }
        AnimatedVisibility(visible = musicEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            MusicMiniBar(
                currentTrack = currentTrack,
                isPlaying    = isPlaying,
                isFocusPhase = isFocusPhase,
                onPlayPause  = onPlayPause,
                onNext       = onNext,
                onPrevious   = onPrevious,
                onOpenPicker = onOpenPicker
            )
        }
    }
}
