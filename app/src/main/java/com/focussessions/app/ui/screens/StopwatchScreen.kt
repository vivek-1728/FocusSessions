package com.focussessions.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focussessions.app.stopwatch.StopwatchState
import com.focussessions.app.ui.components.MusicMiniBar
import com.focussessions.app.viewmodel.StopwatchViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StopwatchScreen(viewModel: StopwatchViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sw      = uiState.stopwatch
    val haptic  = LocalHapticFeedback.current

    // Completion overlay
    if (uiState.showCompletionOverlay) {
        StopwatchCompletionOverlay(
            onDismiss = { viewModel.dismissCompletion(); viewModel.reset() },
            onStopMusic = viewModel::stopMusic
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Stopwatch", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                Text("Custom countdown timer", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF333333)))
            }
        }

        // Duration picker (idle state only)
        AnimatedVisibility(visible = sw.isIdle, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            DurationSetterRow(
                hours   = sw.hours,
                minutes = sw.minutes,
                seconds = sw.seconds,
                onHours   = viewModel::setHours,
                onMinutes = viewModel::setMinutes,
                onSeconds = viewModel::setSeconds
            )
        }

        // Circular countdown ring
        StopwatchRing(
            progress    = sw.progressFraction,
            displayTime = sw.displayTime,
            state       = sw.state,
            size        = 272.dp
        )

        // Controls
        StopwatchControls(
            state    = sw.state,
            onStart  = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.start() },
            onPause  = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.pause() },
            onResume = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.resume() },
            onReset  = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.reset() }
        )

        // Music — loads automatically, plays on completion
        MusicSetup(
            viewModel = viewModel,
            selectedUri  = uiState.selectedCompletionUri,
            isPlaying    = uiState.isMusicPlaying,
            currentTrack = uiState.currentTrack
        )
    }
}

// ── Duration Setter ───────────────────────────────────────────────────────────

@Composable
private fun DurationSetterRow(
    hours: Int, minutes: Int, seconds: Int,
    onHours: (Int) -> Unit, onMinutes: (Int) -> Unit, onSeconds: (Int) -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SET DURATION", style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF333333), letterSpacing = 1.5.sp, fontSize = 10.sp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeUnitPicker("Hours",   hours,   0, 23, onHours,   Modifier.weight(1f))
                Text(":", style = MaterialTheme.typography.headlineSmall.copy(color = Color(0xFF333333), fontWeight = FontWeight.Thin),
                    modifier = Modifier.padding(top = 28.dp))
                TimeUnitPicker("Min",     minutes, 0, 59, onMinutes, Modifier.weight(1f))
                Text(":", style = MaterialTheme.typography.headlineSmall.copy(color = Color(0xFF333333), fontWeight = FontWeight.Thin),
                    modifier = Modifier.padding(top = 28.dp))
                TimeUnitPicker("Sec",     seconds, 0, 59, onSeconds, Modifier.weight(1f))
            }

            // Quick-set chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple(0, 1, 0) to "1m", Triple(0, 5, 0) to "5m",
                       Triple(0, 15, 0) to "15m", Triple(0, 25, 0) to "25m",
                       Triple(0, 45, 0) to "45m", Triple(1, 0, 0) to "1h").forEach { (t, label) ->
                    val (h, m, s) = t
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF111111))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onHours(h); onMinutes(m); onSeconds(s)
                        }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF555555), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeUnitPicker(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 9.sp))
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color(0xFF444444), modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF141414)).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            Text("%02d".format(value), style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White, fontWeight = FontWeight.Light, fontSize = 26.sp))
        }
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color(0xFF444444), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Stopwatch Ring ────────────────────────────────────────────────────────────

@Composable
private fun StopwatchRing(
    progress: Float, displayTime: String,
    state: StopwatchState, size: androidx.compose.ui.unit.Dp
) {
    val animProgress by animateFloatAsState(progress, tween(50), label = "sw_ring")

    val arcColor by animateColorAsState(
        targetValue = when (state) {
            StopwatchState.RUNNING   -> Color.White
            StopwatchState.PAUSED    -> Color(0xFF444444)
            StopwatchState.COMPLETED -> Color.White
            else                     -> Color(0xFF222222)
        },
        animationSpec = tween(400), label = "arc_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(0.98f, 1.02f,
        infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse), label = "pulse")

    Box(Modifier.size(size).scale(if (state == StopwatchState.RUNNING) pulse else 1f), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sw = 10.dp.toPx()
            val r  = this.size.minDimension / 2f - sw
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2f, r * 2f)

            // Subtle glow when running
            if (state == StopwatchState.RUNNING) {
                drawCircle(Color.White.copy(alpha = 0.04f), r + 20.dp.toPx(), center)
            }

            // Track
            drawArc(Color(0xFF0D0D0D), -90f, 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))

            // Tick marks
            for (i in 0 until 60) {
                val angle = Math.toRadians(i * 6.0 - 90.0)
                val major = i % 5 == 0
                val r1 = r - sw - (if (major) 7.dp.toPx() else 3.dp.toPx())
                val r2 = r - sw - 1.dp.toPx()
                drawLine(Color.White.copy(alpha = if (major) 0.12f else 0.04f),
                    Offset(center.x + r1 * cos(angle).toFloat(), center.y + r1 * sin(angle).toFloat()),
                    Offset(center.x + r2 * cos(angle).toFloat(), center.y + r2 * sin(angle).toFloat()),
                    if (major) 1.5.dp.toPx() else 0.5.dp.toPx())
            }

            // Progress arc
            if (animProgress > 0f) {
                drawArc(arcColor, -90f, 360f * animProgress, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                val tipAngle = Math.toRadians(-90.0 + 360.0 * animProgress)
                val tx = center.x + r * cos(tipAngle).toFloat()
                val ty = center.y + r * sin(tipAngle).toFloat()
                drawCircle(arcColor, sw / 2f, Offset(tx, ty))
                drawCircle(Color.White.copy(.5f), sw / 3.5f, Offset(tx, ty))
            }

            // Inner black fill
            drawCircle(Color.Black, r - sw / 2f - 3.dp.toPx(), center)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = when (state) { StopwatchState.IDLE -> "00:00.00"; else -> displayTime },
                style = MaterialTheme.typography.displaySmall.copy(
                    color         = if (state == StopwatchState.PAUSED) Color(0xFF444444) else Color.White,
                    fontWeight    = FontWeight.Thin,
                    letterSpacing = (-2).sp,
                    fontSize      = if (displayTime.length > 8) 36.sp else 44.sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = when (state) {
                    StopwatchState.IDLE      -> "Set duration above"
                    StopwatchState.RUNNING   -> "Counting down"
                    StopwatchState.PAUSED    -> "Paused"
                    StopwatchState.COMPLETED -> "Time's up!"
                },
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF333333), fontSize = 10.sp)
            )
        }
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

@Composable
private fun StopwatchControls(
    state: StopwatchState,
    onStart: () -> Unit, onPause: () -> Unit,
    onResume: () -> Unit, onReset: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(state != StopwatchState.IDLE, enter = scaleIn(), exit = scaleOut()) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF0D0D0D))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onReset),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Refresh, null, tint = Color(0xFF444444), modifier = Modifier.size(22.dp))
            }
        }

        // Primary
        val bgColor by animateColorAsState(
            if (state == StopwatchState.PAUSED) Color(0xFF111111) else Color.White, tween(300), label = "sw_btn")
        Box(Modifier.size(72.dp).clip(CircleShape).background(bgColor)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                when (state) {
                    StopwatchState.IDLE      -> onStart()
                    StopwatchState.RUNNING   -> onPause()
                    StopwatchState.PAUSED    -> onResume()
                    StopwatchState.COMPLETED -> onReset()
                }
            }, contentAlignment = Alignment.Center) {
            AnimatedContent(state, transitionSpec = { scaleIn(spring(Spring.DampingRatioMediumBouncy)) togetherWith scaleOut(tween(100)) }, label = "sw_icon") { s ->
                Icon(when (s) {
                    StopwatchState.IDLE      -> Icons.Rounded.PlayArrow
                    StopwatchState.RUNNING   -> Icons.Rounded.Pause
                    StopwatchState.PAUSED    -> Icons.Rounded.PlayArrow
                    StopwatchState.COMPLETED -> Icons.Rounded.Replay
                }, null, tint = if (s == StopwatchState.PAUSED) Color.White else Color.Black, modifier = Modifier.size(32.dp))
            }
        }

        // Spacer to balance
        Box(Modifier.size(48.dp))
    }
}

// ── Music setup ───────────────────────────────────────────────────────────────

@Composable
private fun MusicSetup(
    viewModel: StopwatchViewModel,
    selectedUri: String, isPlaying: Boolean,
    currentTrack: com.focussessions.app.domain.model.MusicTrack?
) {
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadTracks() }

    val trackToDisplay = currentTrack
        ?: viewModel.uiState.value.trackList.find { it.uri == selectedUri }
        ?: viewModel.uiState.value.trackList.firstOrNull()

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF444444), modifier = Modifier.size(14.dp))
                    Text("Music / Ringtone on completion", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF666666)))
                }
                TextButton(onClick = { showPicker = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Change", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888)))
                }
            }

            MusicMiniBar(
                currentTrack = trackToDisplay,
                isPlaying    = isPlaying,
                isFocusPhase = false,
                onPlayPause  = viewModel::toggleMusic,
                onNext       = {},
                onPrevious   = {},
                onOpenPicker = { showPicker = true }
            )
        }
    }

    if (showPicker) {
        com.focussessions.app.ui.components.MusicPickerDialog(
            tracks        = viewModel.uiState.value.trackList,
            currentTrack  = trackToDisplay,
            onSelectTrack = { track ->
                viewModel.selectCompletionTrack(track.uri)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

// ── Completion overlay ────────────────────────────────────────────────────────

@Composable
private fun StopwatchCompletionOverlay(onDismiss: () -> Unit, onStopMusic: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "done_pulse")
    val pulse by infiniteTransition.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse), label = "done_scale")

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)) {
            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(90.dp).scale(pulse).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Timer, null, tint = Color.Black, modifier = Modifier.size(44.dp))
            }
            Text("Time's Up!", style = MaterialTheme.typography.displaySmall.copy(
                color = Color.White, fontWeight = FontWeight.Thin, letterSpacing = (-1).sp))
            Text("Music started automatically", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF555555)))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Done", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            TextButton(onClick = { onStopMusic(); onDismiss() }) {
                Text("Stop music", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF333333)))
            }
        }
    }
}
