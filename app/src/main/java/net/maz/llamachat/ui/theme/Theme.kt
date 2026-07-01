package net.maz.llamachat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun materialColors(dc: DcScheme) = if (dc.isDark) {
    darkColorScheme(
        primary = dc.primary,
        onPrimary = Color.White,
        primaryContainer = dc.primaryContainer,
        onPrimaryContainer = dc.onSurface,
        secondary = dc.primary,
        onSecondary = Color.White,
        background = dc.surface,
        onBackground = dc.onSurface,
        surface = dc.surface,
        onSurface = dc.onSurface,
        surfaceVariant = dc.surfaceTint,
        onSurfaceVariant = dc.onSurfaceVariant,
        outline = dc.outline,
        outlineVariant = dc.divider,
        error = dc.error,
        onError = Color.White,
        errorContainer = dc.errorContainer,
        onErrorContainer = dc.error,
    )
} else {
    lightColorScheme(
        primary = dc.primary,
        onPrimary = Color.White,
        primaryContainer = dc.primaryContainer,
        onPrimaryContainer = dc.primaryDark,
        secondary = dc.primary,
        onSecondary = Color.White,
        background = dc.surface,
        onBackground = dc.onSurface,
        surface = dc.surface,
        onSurface = dc.onSurface,
        surfaceVariant = dc.surfaceTint,
        onSurfaceVariant = dc.onSurfaceVariant,
        outline = dc.outline,
        outlineVariant = dc.divider,
        error = dc.error,
        onError = Color.White,
        errorContainer = dc.errorContainer,
        onErrorContainer = dc.error,
    )
}

/**
 * Material theme for the app. Follows the system light/dark setting: both modes
 * keep the deep-purple chrome, while dark mode inverts surfaces and text. The
 * custom [DcColors] palette is provided via [LocalDcScheme] so direct call sites
 * adapt along with the Material color scheme.
 */
@Composable
fun LlamaChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dc = if (darkTheme) DcDark else DcLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The status bar sits under the purple app bars in both themes; the
            // nav bar matches the page surface so it disappears into the content.
            window.statusBarColor = dc.primary.toArgb()
            window.navigationBarColor = dc.surface.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = !dc.isDark
        }
    }
    CompositionLocalProvider(LocalDcScheme provides dc) {
        MaterialTheme(
            colorScheme = materialColors(dc),
            typography = AppTypography,
            content = content,
        )
    }
}
