package net.maz.llamachat.data.model

import androidx.compose.ui.graphics.Color
import net.maz.llamachat.ui.theme.DcColors

/**
 * Static catalogs of characters and sampling presets, lifted verbatim from the
 * Claude Design prototype. Characters double as the system prompt for a chat.
 */
data class Character(
    val name: String,
    val description: String,
    val color: Color,
    /** System prompt sent to the model; null = no system persona. */
    val systemPrompt: String?,
)

data class Preset(
    val name: String,
    val temperature: Double,
    val topP: Double,
    val topK: Int,
    val repeatPenalty: Double,
) {
    /** Ordered (label, value) pairs shown as chips on the New Conversation screen. */
    fun chips(): List<Pair<String, String>> = listOf(
        "temperature" to trim(temperature),
        "top_p" to trim(topP),
        "top_k" to topK.toString(),
        "rep_pen" to trim(repeatPenalty),
    )

    private fun trim(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

object Catalog {
    val characters: List<Character> = listOf(
        Character(
            "Assistant", "Helpful, concise general assistant.", DcColors.Primary,
            "You are a helpful, concise assistant.",
        ),
        Character(
            "Default", "Neutral. No system persona.", Color(0xFF7E57C2),
            null,
        ),
        Character(
            "Coding Helper", "Senior engineer. Explains with code.", DcColors.PrimaryDark,
            "You are a senior software engineer. Explain clearly and back up explanations with concise code examples.",
        ),
        Character(
            "Storyteller", "Vivid, imaginative narrator.", Color(0xFF8E24AA),
            "You are a vivid, imaginative storyteller. Answer with rich, evocative narration.",
        ),
        Character(
            "Marcus", "Stoic philosopher. Calm, thoughtful.", Color(0xFF5C6BC0),
            "You are Marcus, a stoic philosopher. Respond calmly and thoughtfully, drawing on stoic principles.",
        ),
    )

    val presets: List<Preset> = listOf(
        Preset("Default", 0.7, 0.9, 20, 1.15),
        Preset("Precise", 0.1, 0.1, 40, 1.18),
        Preset("Creative", 1.1, 0.95, 100, 1.10),
        Preset("Llama-Precise", 0.7, 0.1, 40, 1.18),
        Preset("Midnight Enigma", 0.98, 0.37, 100, 1.18),
    )

    /** Fallback model list shown before the server reports its own models. */
    val fallbackModels: List<String> = listOf(
        "Llama-3.1-8B-Instruct.Q5_K_M.gguf",
        "Mistral-7B-Instruct-v0.3.Q4_K_M.gguf",
        "Qwen2.5-14B-Instruct.Q4_K_M.gguf",
        "gemma-2-9b-it.Q5_K_M.gguf",
        "Phi-3.5-mini-instruct.Q6_K.gguf",
    )

    fun character(name: String): Character =
        characters.firstOrNull { it.name == name } ?: characters.first()

    fun preset(name: String): Preset =
        presets.firstOrNull { it.name == name } ?: presets.first()

    /** Strip the ".gguf" suffix for compact display, matching the prototype. */
    fun shortModel(model: String): String = model.removeSuffix(".gguf")
}
