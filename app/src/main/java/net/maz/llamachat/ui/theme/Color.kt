package net.maz.llamachat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted directly from the Claude Design prototype (LlamaChat.dc.html).
 * The design is a Material-flavoured deep-purple theme.
 */
object DcColors {
    val Primary = Color(0xFF5E35B1)        // deep purple 600 — app bars, accents
    val PrimaryDark = Color(0xFF4527A0)    // deep purple 800 — chips, code accent
    val PrimaryHover = Color(0xFF5128A0)   // button hover
    val PrimaryContainer = Color(0xFFEDE7F6) // light purple — avatars/empty icons
    val SurfaceTint = Color(0xFFF4F2F8)    // input fields, code blocks, menu hover
    val SurfaceTintAlt = Color(0xFFFAF9FC) // subtle row hover / model box
    val Page = Color(0xFFDCD7E6)           // page backdrop behind the phone frame
    val Error = Color(0xFFC62828)
    val ErrorContainer = Color(0xFFFDECEA)

    val OnSurface = Color(0xDE000000)      // rgba(0,0,0,.87)
    val OnSurfaceMedium = Color(0x99000000) // rgba(0,0,0,.6)
    val OnSurfaceVariant = Color(0x8C000000) // rgba(0,0,0,.55)
    val OnSurfaceFaint = Color(0x73000000)  // rgba(0,0,0,.45)
    val Outline = Color(0x1F000000)         // rgba(0,0,0,.12)
    val Divider = Color(0x0F000000)         // rgba(0,0,0,.06)

    /** Avatar / character colors used by the prototype. */
    val CharacterPalette = listOf(
        Color(0xFF5E35B1),
        Color(0xFF7E57C2),
        Color(0xFF4527A0),
        Color(0xFF8E24AA),
        Color(0xFF5C6BC0),
    )
}
