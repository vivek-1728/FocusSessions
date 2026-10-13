package com.focussessions.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmoledColorScheme = darkColorScheme(
    primary               = AccentPrimary,
    onPrimary             = BackgroundDeep,
    primaryContainer      = AccentMuted,
    onPrimaryContainer    = AccentPrimary,
    secondary             = AccentSecondary,
    onSecondary           = BackgroundDeep,
    secondaryContainer    = BackgroundElevated,
    onSecondaryContainer  = TextPrimary,
    tertiary              = ProgressBreak,
    onTertiary            = BackgroundDeep,
    background            = BackgroundBase,
    onBackground          = TextPrimary,
    surface               = BackgroundCard,
    onSurface             = TextPrimary,
    surfaceVariant        = BackgroundElevated,
    onSurfaceVariant      = TextSecondary,
    outline               = BackgroundOverlay,
    outlineVariant        = BackgroundOverlay,
    error                 = ErrorColor,
    onError               = BackgroundDeep,
    inversePrimary        = AccentMuted,
    scrim                 = Color(0xCC000000)
)

@Composable
fun FocusSessionsTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // True black status/nav bars for full AMOLED effect
            window.statusBarColor     = 0xFF000000.toInt()
            window.navigationBarColor = 0xFF000000.toInt()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        typography  = Typography,
        content     = content
    )
}
