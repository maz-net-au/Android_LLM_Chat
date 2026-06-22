package net.maz.llamachat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LlamaLightColors = lightColorScheme(
    primary = DcColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DcColors.PrimaryContainer,
    onPrimaryContainer = DcColors.PrimaryDark,
    secondary = DcColors.Primary,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = DcColors.OnSurface,
    surface = Color.White,
    onSurface = DcColors.OnSurface,
    surfaceVariant = DcColors.SurfaceTint,
    onSurfaceVariant = DcColors.OnSurfaceVariant,
    outline = DcColors.Outline,
    outlineVariant = DcColors.Divider,
    error = DcColors.Error,
    onError = Color.White,
    errorContainer = DcColors.ErrorContainer,
    onErrorContainer = DcColors.Error,
)

/**
 * The prototype is a light-only Material theme; we keep the app light regardless
 * of system setting so the deep-purple chrome always renders as designed.
 */
@Composable
fun LlamaChatTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = LlamaLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DcColors.Primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
