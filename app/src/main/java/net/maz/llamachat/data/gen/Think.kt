package net.maz.llamachat.data.gen

/**
 * Remove `<think>…</think>` reasoning blocks from a stored message so only the
 * visible answer remains. Mirrors the answer-extraction in the UI's `parseThink`
 * (ui/chat/ThinkingSection.kt), but as a plain string→string helper usable from the
 * data layer — for the text handed to the summarizer and for permanently cleaning
 * messages as they're locked.
 *
 * Handles multiple blocks and a dangling, still-open `<think>` (everything after it
 * is dropped). The leftover run of spaces/tabs where a block used to sit is collapsed
 * so a `"Name: <think>…</think> reply"` becomes `"Name: reply"`; the leading `Name:`
 * prefix and any internal newlines in the answer are preserved.
 */
fun stripThink(text: String): String {
    var out = Regex("(?s)<think>.*?</think>").replace(text, "")
    val dangling = out.indexOf("<think>")
    if (dangling >= 0) out = out.substring(0, dangling)
    // Collapse only horizontal whitespace runs (leave newlines/formatting intact),
    // then trim the ends left bare by a removed block.
    return out.replace(Regex("[ \\t]{2,}"), " ").trim()
}
