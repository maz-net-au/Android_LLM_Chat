package net.maz.llamachat.data.gen

/**
 * All the knobs for per-character state updates (the "Update with LLM" button on the
 * cast management screen, and the per-character half of "Summarize → Story + characters").
 * Each character gets its own independent request writing only to its own state field —
 * never folded into the story summary.
 */
object CastConfig {

    /** Upper bound on a generated state update; states are short by design. */
    const val MAX_STATE_TOKENS = 500

    /** System prompt for a single character's state update. */
    const val SYSTEM_PROMPT =
        "You maintain one character's running state notes in an ongoing roleplay. From the " +
        "conversation so far, write a short, dense update of THIS character's current state: " +
        "their relationships and how those have changed, what they have been through, and any " +
        "permanent changes (an injury such as a lost limb, a death, a new possession, a shift " +
        "in location or allegiance). Write in neutral present tense. Output only the state " +
        "text — no preamble, headings chatter, or commentary."

    /** Label for the member's previous state when one exists (fold strategy: update, don't restart). */
    const val PREVIOUS_STATE_LABEL = "Previous state:"

    /** Instruction closing the user turn. */
    const val INSTRUCTION = "Write the updated state now."

    /** Note appended to the text part when a reference image is included. */
    const val REF_IMAGE_NOTE =
        "\n\nA reference image of this character is attached. Incorporate what it shows " +
        "(appearance, attire, distinguishing features) into the state where relevant."
}
