package net.maz.llamachat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Colored circular avatar showing a character's initial — used in lists, the
 *  New Conversation picker, and the chat header. The initial is white on dark fills
 *  and near-black on pale ones (the new palette tints are too light for white text). */
@Composable
fun Avatar(
    initial: String,
    color: Color,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
) {
    val argb = color.toArgb()
    val luminance = 0.299f * ((argb shr 16) and 0xFF) +
        0.587f * ((argb shr 8) and 0xFF) +
        0.114f * (argb and 0xFF)
    val textColor = if (luminance < 150f) Color.White else Color(0xDE000000)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .let { if (bordered) it.border(2.dp, textColor.copy(alpha = 0.4f), CircleShape) else it },
    ) {
        Text(
            text = initial.take(1),
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** Invert a colour's channels around mid-gray (top-level so nested companions can call it). */
fun bubbleInverse(c: Color): Color =
    Color((0xFF000000.toInt() or ((255 - ((c.red * 255).roundToInt())) shl 16) or
            ((255 - ((c.green * 255).roundToInt())) shl 8) or
            (255 - ((c.blue * 255).roundToInt()))).toLong())
