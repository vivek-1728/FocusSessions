package com.focussessions.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focussessions.app.domain.model.MusicTrack
import com.focussessions.app.ui.theme.*

// ── Mini Music Bar ─────────────────────────────────────────────────────────────
// Shows at the bottom of the main screen when music is enabled.
// Note: music only actually plays during break phases — this bar is always
// visible when enabled so the user can select/preview tracks.
@Composable
fun MusicMiniBar(
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    isFocusPhase: Boolean,          // true = focus, music is intentionally silent
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = BackgroundCard)
    ) {
        Column {
            // Phase hint banner — only shown during focus to explain silence
            if (isFocusPhase) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BackgroundElevated)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.VolumeOff,
                        contentDescription = null,
                        tint               = TextMuted,
                        modifier           = Modifier.size(12.dp)
                    )
                    Text(
                        text  = "Music plays automatically during breaks",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Album art / waveform indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BackgroundElevated)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = onOpenPicker
                        )
                ) {
                    if (isPlaying && !isFocusPhase) {
                        WaveformIcon()
                    } else {
                        Icon(
                            imageVector        = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint               = if (isFocusPhase) TextMuted else TextSecondary,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }

                // Track info
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text     = currentTrack?.title ?: "Tap to browse music",
                        style    = MaterialTheme.typography.labelLarge.copy(
                            color = if (currentTrack != null && !isFocusPhase) TextPrimary
                                    else TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentTrack != null) {
                        Text(
                            text     = if (isFocusPhase) "Will play on break"
                                       else currentTrack.artist,
                            style    = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Playback controls — dimmed during focus
                if (currentTrack != null) {
                    IconButton(
                        onClick  = onPrevious,
                        enabled  = !isFocusPhase,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint     = if (isFocusPhase) TextMuted else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFocusPhase) BackgroundElevated else AccentPrimary
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null,
                                enabled           = !isFocusPhase,
                                onClick           = onPlayPause
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying && !isFocusPhase)
                                              Icons.Rounded.Pause
                                          else
                                              Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint     = if (isFocusPhase) TextMuted else BackgroundDeep,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick  = onNext,
                        enabled  = !isFocusPhase,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint     = if (isFocusPhase) TextMuted else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    TextButton(onClick = onOpenPicker) {
                        Text(
                            text  = "Browse",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Animated Waveform (AMOLED — white bars) ───────────────────────────────────
@Composable
fun WaveformIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")

    val bar1 by transition.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(400, easing = EaseInOutSine), RepeatMode.Reverse), label = "b1")
    val bar2 by transition.animateFloat(0.7f, 1f,
        infiniteRepeatable(tween(600, easing = EaseInOutSine), RepeatMode.Reverse), label = "b2")
    val bar3 by transition.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse), label = "b3")

    Row(
        modifier              = modifier.height(18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(bar1, bar2, bar3).forEach { scale ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)   // pure white on AMOLED
            )
        }
    }
}

// ── Music Picker Dialog ────────────────────────────────────────────────────────
@Composable
fun MusicPickerDialog(
    tracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    onSelectTrack: (MusicTrack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundCard)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text  = "Choose Music",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color      = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text  = "Plays automatically during breaks",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                HorizontalDivider(color = BackgroundOverlay)

                if (tracks.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MusicOff,
                                contentDescription = null,
                                tint     = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text  = "No music found on device",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                            )
                            Text(
                                text  = "Add audio files to use this feature",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding  = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(tracks) { _, track ->
                            TrackItem(
                                track      = track,
                                isSelected = track.id == currentTrack?.id,
                                onSelect   = {
                                    onSelectTrack(track)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: MusicTrack,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onSelect
            )
            .background(if (isSelected) BackgroundElevated else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track icon / playing indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BackgroundElevated)
        ) {
            if (isSelected) {
                WaveformIcon(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint     = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    color      = if (isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = track.artist,
                style    = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text  = formatMs(track.duration),
            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontSize = 11.sp)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
