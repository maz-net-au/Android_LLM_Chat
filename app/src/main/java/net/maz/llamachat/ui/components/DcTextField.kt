package net.maz.llamachat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.theme.DcColors

/**
 * Filled text field matching the prototype: tinted background, a 2dp purple
 * underline, and a small always-visible purple label above the input.
 */
@Composable
fun DcTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val underline = DcColors.Primary
    Box(
        modifier = modifier
            .background(DcColors.SurfaceTint, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = underline,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            }
            .padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 6.dp),
    ) {
        Column {
            Text(
                text = label,
                color = DcColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    color = DcColors.OnSurface,
                ),
                cursorBrush = SolidColor(DcColors.Primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = DcColors.OnSurfaceFaint, fontSize = 16.sp)
                    }
                    inner()
                },
            )
        }
    }
}
