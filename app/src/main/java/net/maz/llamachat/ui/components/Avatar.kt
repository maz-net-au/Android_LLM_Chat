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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colored circular avatar showing a character's initial — used in lists, the
 *  New Conversation picker, and the chat header. */
@Composable
fun Avatar(
    initial: String,
    color: Color,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
            .let { if (bordered) it.border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape) else it },
    ) {
        Text(
            text = initial.take(1),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )
    }
}
