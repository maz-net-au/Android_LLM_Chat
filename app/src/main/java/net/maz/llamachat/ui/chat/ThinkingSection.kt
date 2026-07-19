package net.maz.llamachat.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.theme.DcColors

/**
 * An assistant message split around a `<think>…</think>` reasoning block, as emitted
 * by reasoning models. The tags are kept in the stored message text (so they survive
 * reloads and stay in the transcript); this only affects how the text is presented.
 */
data class ThinkParts(
    /** True when a reasoning block is present and worth showing. */
    val hasThink: Boolean,
    /** Reasoning text; may be empty early in streaming, before any tokens arrive. */
    val reasoning: String,
    /** The visible answer — everything outside the think block. */
    val answer: String,
    /** True while `<think>` has opened but `</think>` hasn't arrived yet. */
    val streaming: Boolean,
)

/**
 * Open/close delimiter pairs that wrap a model's reasoning block. Most reasoning
 * models use `<think>…</think>`; Gemma 4 instead emits reasoning on a "thought
 * channel" (`<|channel>thought … <channel|>`), even the empty one it inserts when
 * thinking is disabled — which parses to an empty block and so stays hidden.
 */
private val THINK_TAGS = listOf(
    "<think>" to "</think>",
    "<|channel>thought" to "<channel|>",
)

/**
 * Contentless markers some models leave in the visible text (e.g. Gemma's `<|think|>`
 * thinking-enable token). Stripped before parsing so they never show, and never
 * confused for a reasoning block.
 */
private val THINK_NOISE = listOf("<|think|>")

private fun stripThinkNoise(text: String): String {
    var out = text
    for (marker in THINK_NOISE) if (out.contains(marker)) out = out.replace(marker, "")
    return out
}

/**
 * Split [text] into its reasoning and answer around the first reasoning block (see
 * [THINK_TAGS]). An open tag with no closing tag means the model is still reasoning
 * (everything after it is reasoning-so-far, the answer is empty). A closed-but-empty
 * block is treated as no reasoning at all.
 */
fun parseThink(text: String): ThinkParts {
    val cleaned = stripThinkNoise(text)

    // Whichever known reasoning block opens earliest wins.
    var open = -1
    var tags: Pair<String, String>? = null
    for (pair in THINK_TAGS) {
        val i = cleaned.indexOf(pair.first)
        if (i >= 0 && (open < 0 || i < open)) {
            open = i
            tags = pair
        }
    }
    if (tags == null) return ThinkParts(hasThink = false, reasoning = "", answer = cleaned, streaming = false)

    val (openTag, closeTag) = tags
    val lead = cleaned.substring(0, open) // text before the tag; virtually always empty
    val afterOpen = open + openTag.length
    val close = cleaned.indexOf(closeTag, afterOpen)
    if (close < 0) {
        return ThinkParts(
            hasThink = true,
            reasoning = cleaned.substring(afterOpen).trim(),
            answer = lead.trim(),
            streaming = true,
        )
    }
    val reasoning = cleaned.substring(afterOpen, close).trim()
    val answer = (lead + cleaned.substring(close + closeTag.length)).trim()
    return if (reasoning.isEmpty()) {
        ThinkParts(hasThink = false, reasoning = "", answer = answer, streaming = false)
    } else {
        ThinkParts(hasThink = true, reasoning = reasoning, answer = answer, streaming = false)
    }
}

/**
 * A collapsed-by-default disclosure for a model's reasoning. The header pulses
 * "Thinking…" while [animating] (the block is still streaming) so a long reasoning
 * pass doesn't look frozen before the answer appears; once done it reads "Thoughts".
 * Expanding reveals the reasoning as a muted aside.
 */
@Composable
fun ThinkingSection(reasoning: String, animating: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 3.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide reasoning" else "Show reasoning",
                tint = DcColors.OnSurfaceFaint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            if (animating) PulsingThinkingLabel() else Text(
                "Thoughts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = DcColors.OnSurfaceMedium,
            )
        }
        if (expanded && reasoning.isNotBlank()) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, start = 2.dp)
                    .height(IntrinsicSize.Min),
            ) {
                // A slim left rule marks the reasoning as an aside, distinct from the answer.
                Spacer(
                    Modifier
                        .padding(end = 10.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(DcColors.Divider, RoundedCornerShape(1.dp)),
                )
                Text(
                    text = reasoning,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = DcColors.OnSurfaceMedium,
                )
            }
        }
    }
}

@Composable
private fun PulsingThinkingLabel() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "thinkingAlpha",
    )
    Text(
        "Thinking…",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = DcColors.Primary.copy(alpha = alpha),
    )
}
