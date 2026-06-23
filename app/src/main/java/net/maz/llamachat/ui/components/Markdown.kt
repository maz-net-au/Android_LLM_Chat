package net.maz.llamachat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.theme.DcColors

/**
 * Minimal Markdown renderer matching the subset the prototype's `md()` supports:
 * fenced code blocks, unordered lists, inline code, **bold** and *italic*.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.text),
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    color = BodyColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                is MdBlock.Bullets -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    block.items.forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("•", fontSize = 15.sp, color = BodyColor, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = inlineAnnotated(item),
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                                color = BodyColor,
                            )
                        }
                    }
                }
                is MdBlock.Code -> CodeBlock(block.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Text(
        text = code,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = BodyColor,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .background(DcColors.SurfaceTint, RoundedCornerShape(8.dp))
            .border(1.dp, DcColors.Divider, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    )
}

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
    data class Code(val code: String) : MdBlock
}

private fun parseBlocks(src: String): List<MdBlock> {
    val lines = src.split("\n")
    val blocks = mutableListOf<MdBlock>()
    var bullets: MutableList<String>? = null
    fun flushBullets() {
        bullets?.let { blocks += MdBlock.Bullets(it) }
        bullets = null
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flushBullets()
                i++
                val code = StringBuilder()
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append("\n")
                    code.append(lines[i])
                    i++
                }
                i++ // closing fence
                blocks += MdBlock.Code(code.toString())
            }
            Regex("^\\s*[-*]\\s+").containsMatchIn(line) -> {
                val item = line.replaceFirst(Regex("^\\s*[-*]\\s+"), "")
                if (bullets == null) bullets = mutableListOf()
                bullets!!.add(item)
                i++
            }
            line.isBlank() -> {
                flushBullets()
                i++
            }
            else -> {
                flushBullets()
                blocks += MdBlock.Paragraph(line)
                i++
            }
        }
    }
    flushBullets()
    return blocks
}

private val codeSpan = SpanStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    background = Color(0x1A5E35B1), // rgba(94,53,177,.10)
    color = DcColors.PrimaryDark,
)
private val boldSpan = SpanStyle(fontWeight = FontWeight.Bold)
/** Slightly darker than the global OnSurface (.87) so message text reads crisper. */
private val BodyColor = Color(0xF2000000) // rgba(0,0,0,.95)

// System Roboto has no bundled italic face, so the synthesized slant is barely
// visible — pair it with a distinct color so *italic* (e.g. roleplay *actions*)
// reads as clearly different from body text.
private val italicSpan = SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF3949AB)) // indigo

/** Inline formatting matching the prototype's `inline()`: `code`, **bold**, *italic*. */
private fun inlineAnnotated(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(codeSpan) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '*' && i + 1 < src.length && src[i + 1] == '*' -> {
                val end = src.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(boldSpan) { append(src.substring(i + 2, end)) }
                    i = end + 2
                } else { append(c); i++ }
            }
            c == '*' -> {
                val end = src.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(italicSpan) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}
