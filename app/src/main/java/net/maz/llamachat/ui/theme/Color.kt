package net.maz.llamachat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A full set of theme-able color slots. One instance backs light mode, another
 * dark mode; [LocalDcScheme] selects between them so the `DcColors.*` accessors
 * below resolve to the right value for the current system theme.
 *
 * The light values are lifted directly from the Claude Design prototype
 * (LlamaChat.dc.html); the dark values keep the same deep-purple identity while
 * inverting surfaces and text for legibility on a dark background.
 */
data class DcScheme(
    val isDark: Boolean,
    val primary: Color,
    val primaryDark: Color,
    val primaryHover: Color,
    val primaryContainer: Color,
    val surface: Color,
    val surfaceTint: Color,
    val surfaceTintAlt: Color,
    val page: Color,
    val error: Color,
    val errorContainer: Color,
    val onSurface: Color,
    val onSurfaceMedium: Color,
    val onSurfaceVariant: Color,
    val onSurfaceFaint: Color,
    val outline: Color,
    val divider: Color,
    // Markdown-renderer specific tones (see Markdown.kt).
    val mdBody: Color,
    val mdCodeBg: Color,
    val mdItalic: Color,
)

val DcLight = DcScheme(
    isDark = false,
    primary = Color(0xFF5E35B1),        // deep purple 600 — app bars, accents
    primaryDark = Color(0xFF4527A0),    // deep purple 800 — chips, code accent
    primaryHover = Color(0xFF5128A0),   // button hover
    primaryContainer = Color(0xFFEDE7F6), // light purple — avatars/empty icons
    surface = Color.White,
    surfaceTint = Color(0xFFF4F2F8),    // input fields, code blocks, menu hover
    surfaceTintAlt = Color(0xFFFAF9FC), // subtle row hover / model box
    page = Color(0xFFDCD7E6),           // page backdrop behind the phone frame
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDECEA),
    onSurface = Color(0xDE000000),      // rgba(0,0,0,.87)
    onSurfaceMedium = Color(0x99000000), // rgba(0,0,0,.6)
    onSurfaceVariant = Color(0x8C000000), // rgba(0,0,0,.55)
    onSurfaceFaint = Color(0x73000000),  // rgba(0,0,0,.45)
    outline = Color(0x1F000000),         // rgba(0,0,0,.12)
    divider = Color(0x0F000000),         // rgba(0,0,0,.06)
    mdBody = Color(0xF2000000),          // rgba(0,0,0,.95)
    mdCodeBg = Color(0x1A5E35B1),        // rgba(94,53,177,.10)
    mdItalic = Color(0xFF3949AB),        // indigo
)

val DcDark = DcScheme(
    isDark = true,
    primary = Color(0xFF7E57C2),        // deep purple 400 — legible accent on dark, keeps white-on-purple chrome
    primaryDark = Color(0xFFB39DDB),    // deep purple 200 — accent text/icons (e.g. inline code)
    primaryHover = Color(0xFF8E6AD0),
    primaryContainer = Color(0xFF332B47), // muted dark purple — avatars/empty icons
    surface = Color(0xFF15131A),         // near-black, faint purple tint
    surfaceTint = Color(0xFF26222E),     // input fields, code blocks, menu hover
    surfaceTintAlt = Color(0xFF211E29),  // subtle row hover / model box
    page = Color(0xFF0D0B12),            // page backdrop
    error = Color(0xFFEF5350),           // lighter red reads better on dark
    errorContainer = Color(0xFF3A1E1E),
    onSurface = Color(0xDEFFFFFF),       // white .87
    onSurfaceMedium = Color(0x99FFFFFF), // white .60
    onSurfaceVariant = Color(0x8CFFFFFF), // white .55
    onSurfaceFaint = Color(0x73FFFFFF),  // white .45
    outline = Color(0x26FFFFFF),         // white .15
    divider = Color(0x14FFFFFF),         // white .08
    mdBody = Color(0xF2FFFFFF),          // white .95
    mdCodeBg = Color(0x33B39DDB),        // translucent light purple
    mdItalic = Color(0xFF9FA8DA),        // light indigo
)

/** Selected light/dark scheme; provided by [LlamaChatTheme]. */
val LocalDcScheme = staticCompositionLocalOf { DcLight }

/**
 * Theme-aware palette. Each property is a composable read of [LocalDcScheme], so
 * existing `DcColors.Primary`-style call sites resolve to the light or dark value
 * automatically. These can only be read inside composition — for non-composable
 * contexts (character data, model badges, the launcher icon) use [DcBrand].
 */
object DcColors {
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.primary
    val PrimaryDark: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.primaryDark
    val PrimaryHover: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.primaryHover
    val PrimaryContainer: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.primaryContainer
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.surface
    val SurfaceTint: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.surfaceTint
    val SurfaceTintAlt: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.surfaceTintAlt
    val Page: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.page
    val Error: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.error
    val ErrorContainer: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.errorContainer
    val OnSurface: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.onSurface
    val OnSurfaceMedium: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.onSurfaceMedium
    val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.onSurfaceVariant
    val OnSurfaceFaint: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.onSurfaceFaint
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.outline
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.divider
    val MdBody: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.mdBody
    val MdCodeBg: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.mdCodeBg
    val MdItalic: Color @Composable @ReadOnlyComposable get() = LocalDcScheme.current.mdItalic

    /** Avatar / character colors used by the prototype. Saturated enough to carry
     *  white text in either theme, so they stay fixed across light/dark. */
    val CharacterPalette = listOf(
        Color(0xFF5E35B1),
        Color(0xFF7E57C2),
        Color(0xFF4527A0),
        Color(0xFF8E24AA),
        Color(0xFF5C6BC0),
    )
}

/**
 * Fixed brand colors for non-composable contexts — character/model data defined
 * outside composition, where the theme-aware [DcColors] getters can't be read.
 * These are avatar fills carrying white text, so the deep-purple values work on
 * either background.
 */
object DcBrand {
    val Primary = Color(0xFF5E35B1)
    val PrimaryDark = Color(0xFF4527A0)
}
