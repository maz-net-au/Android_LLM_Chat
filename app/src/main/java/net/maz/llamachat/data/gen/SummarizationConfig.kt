package net.maz.llamachat.data.gen

import net.maz.llamachat.data.model.Preset

/**
 * All the knobs for "Summarize & continue" in one place so they're easy to tune.
 *
 * TODO: promote these to an app-wide settings screen later; for now they're consts.
 */
object SummarizationConfig {

    /** X — how many trailing messages stay verbatim (unlocked, editable) after a
     *  summarize. Everything older is folded into the summary and locked. */
    const val KEEP_UNLOCKED = 12

    /** Upper bound on the generated summary; "a few thousand tokens". */
    const val MAX_SUMMARY_TOKENS = 3000

    /** Index of the first message kept unlocked; everything before it is folded in and
     *  locked. Zero when there's nothing old enough to compact. */
    fun keepFrom(messageCount: Int): Int = (messageCount - KEEP_UNLOCKED).coerceAtLeast(0)

    /** Whether there's anything worth summarizing (more messages than we keep verbatim). */
    fun canSummarize(messageCount: Int): Boolean = keepFrom(messageCount) > 0

    /** Fixed, low-temperature sampling for the summarizer — independent of the chat's
     *  own preset, so a hot roleplay preset can't make the summary florid or unstable. */
    val sampling = Preset(
        name = "Summary",
        temperature = 0.3,
        topP = 0.9,
        topK = 40,
        repeatPenalty = 1.1,
    )

    /** Base instruction. The transcript to compact is supplied as the user turn. */
    const val SYSTEM_PROMPT =
        "You are a summarizer that compacts a long chat so it can continue without its " +
        "full history. Produce a single, information-dense summary of everything that has " +
        "happened so far, small enough to fit comfortably in a few thousand tokens. When a " +
        "previous summary is provided, fold the new messages into it without losing any " +
        "earlier facts. Preserve concrete details needed to continue coherently: names, " +
        "goals, decisions, unresolved threads, and the setting. Write in neutral past tense. " +
        "Output only the summary text — no preamble, headings chatter, or commentary."

    /** Appended for transcript ("Name:" prefix) characters — roleplay needs the state
     *  the plain-assistant summary wouldn't bother with. */
    const val ROLEPLAY_ADDENDUM =
        "\n\nThis is a roleplay between named characters. Be sure to capture: the " +
        "relationships between the characters and how they've changed; what the characters " +
        "have been through together; and each character's current state — especially " +
        "permanent changes that have occurred (e.g. an injury such as a lost limb, a death, " +
        "a new possession, a shift in location or allegiance). These persist for the rest of " +
        "the story."

    fun systemPrompt(usesNamePrefixes: Boolean): String =
        if (usesNamePrefixes) SYSTEM_PROMPT + ROLEPLAY_ADDENDUM else SYSTEM_PROMPT

    /** Label prefixed to the prior summary when re-summarizing (fold strategy). */
    const val PREVIOUS_SUMMARY_LABEL = "Previous summary:"

    /** Instruction closing the user turn, after the material to compact. */
    const val FOLD_INSTRUCTION =
        "Write the updated summary now, incorporating everything above."
}
