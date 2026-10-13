package com.focussessions.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── AMOLED Theme — Pure Black & White for maximum battery savings ──
// True black pixels = OLED pixels OFF = zero power draw

val BackgroundDeep     = Color(0xFF000000)   // true black
val BackgroundBase     = Color(0xFF000000)   // true black
val BackgroundCard     = Color(0xFF0A0A0A)   // near-black card
val BackgroundElevated = Color(0xFF141414)   // slightly elevated
val BackgroundOverlay  = Color(0xFF1E1E1E)   // subtle overlay

val AccentPrimary      = Color(0xFFFFFFFF)   // pure white accent
val AccentSecondary    = Color(0xFFCCCCCC)   // soft white
val AccentGlow         = Color(0xFFFFFFFF)   // white glow
val AccentMuted        = Color(0xFF2A2A2A)   // muted dark

val ProgressRing       = Color(0xFFFFFFFF)
val ProgressTrack      = Color(0xFF1A1A1A)
val ProgressBreak      = Color(0xFF888888)   // grey for break

val TextPrimary        = Color(0xFFFFFFFF)   // pure white
val TextSecondary      = Color(0xFF999999)   // medium grey
val TextMuted          = Color(0xFF555555)   // dark grey
val TextAccent         = Color(0xFFFFFFFF)

val ErrorColor         = Color(0xFFFF4444)
val WarningColor       = Color(0xFFFFCC00)
val SuccessColor       = Color(0xFFFFFFFF)

// Timer states — all white/grey on AMOLED
val TimerFocus         = Color(0xFFFFFFFF)   // white ring during focus
val TimerBreak         = Color(0xFF888888)   // grey ring during break
val TimerPaused        = Color(0xFF666666)   // dim grey when paused
val TimerCompleted     = Color(0xFFFFFFFF)   // white when done

// Kept for legacy references
val GradientStart      = Color(0xFF000000)
val GradientEnd        = Color(0xFF000000)
