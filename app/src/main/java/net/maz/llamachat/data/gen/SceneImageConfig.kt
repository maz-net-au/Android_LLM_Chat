package net.maz.llamachat.data.gen

import net.maz.llamachat.data.model.Preset

/**
 * Knobs for the in-chat "scene image" description step, kept in one place like
 * [SummarizationConfig]. The LLM turns the current scene into a single-image
 * prompt for a ComfyUI text-to-image workflow.
 */
object SceneImageConfig {

    /** Upper bound on the generated description — a text-to-image prompt, not an essay. */
    const val MAX_DESCRIPTION_TOKENS = 500

    /** Fixed, moderate sampling independent of the chat's own preset, so a hot
     *  roleplay preset can't derail the visual description. */
    val sampling = Preset(
        name = "SceneImage",
        temperature = 0.7,
        topP = 0.9,
        topK = 40,
        repeatPenalty = 1.05,
    )

    /** System instruction; the scene context + focus are supplied as the user turn. */
    const val SYSTEM_PROMPT =
        "You are a visual director that turns a moment from a story into a prompt for a " +
        "text-to-image model. Describe ONE single still image capturing the current moment. " +
        "Describe only what would be visible in that frame — never sounds, smells, thoughts, " +
        "backstory, or events outside the shot. Spend the most detail on the requested focus; " +
        "give progressively less detail to related visible elements, and only briefly note " +
        "background. Any character visible in the frame must be described at least peripherally " +
        "by apparent age, gender, race, and one or two defining features (hair, build, clothing), " +
        "so characters stay recognizable across images. Use concrete, present-tense visual " +
        "language and comma-separated descriptive phrases. Output ONLY the image description — " +
        "no preamble, labels, explanations, or commentary."

    /** Closes the user turn, after the scene context and focus. */
    const val INSTRUCTION =
        "Describe the single image now, leading with the focus above."
}
