package net.maz.llamachat.data.model

import androidx.compose.ui.graphics.Color
import net.maz.llamachat.ui.theme.DcColors
import kotlin.math.absoluteValue

/**
 * A character defines the persona for a conversation: its [context] becomes the
 * system prompt and its optional [greeting] is seeded as the first assistant
 * message. Both may contain `{{char}}` / `{{user}}` placeholders, mirroring the
 * text-generation-webui character format. Built-ins live in [Catalog]; users can
 * add, edit, delete and import/export their own.
 */
data class Character(
    val name: String,
    /** System prompt; blank means no system persona. */
    val context: String,
    /** Optional intro message spoken by the assistant when a chat starts. */
    val greeting: String? = null,
    /** Optional one-line subtitle for the picker; falls back to a context snippet. */
    val description: String = "",
    val color: Color,
    /**
     * When true, turns are formatted as a "Name:" transcript: user lines are
     * prefixed with the user's name, replies are prefilled with this character's
     * name (forcing it to stay in character), and "Name:" stop sequences are sent.
     * Disable for plain assistant-style chats where that framing hurts.
     */
    val usesNamePrefixes: Boolean = true,
) {
    /** Subtitle shown in the character picker. */
    fun subtitle(): String =
        description.ifBlank { context.replace(Regex("\\s+"), " ").trim() }
            .let { if (it.length > 90) it.take(90) + "…" else it }

    private fun resolve(text: String, userName: String): String =
        text.replace("{{char}}", name).replace("{{user}}", userName)

    /** System prompt with placeholders filled in; blank if there is no persona. */
    fun resolvedContext(userName: String): String = resolve(context, userName)

    /** Greeting with placeholders filled in, or null if the character has none. */
    fun resolvedGreeting(userName: String): String? =
        greeting?.takeIf { it.isNotBlank() }?.let { resolve(it, userName) }
}

/**
 * Sampling parameters for a conversation. Every field is optional: only the ones
 * that are set are sent to llama-server, so unset values fall back to the
 * server's own defaults. Field names mirror llama.cpp's sampler params.
 */
data class Preset(
    val name: String,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val minP: Double? = null,
    val typicalP: Double? = null,
    val repeatPenalty: Double? = null,
    val repeatLastN: Int? = null,
    val presencePenalty: Double? = null,
    val frequencyPenalty: Double? = null,
    val mirostat: Int? = null,
    val mirostatTau: Double? = null,
    val mirostatEta: Double? = null,
) {
    /** Ordered (label, value) pairs for the set fields, shown as chips. */
    fun chips(): List<Pair<String, String>> = buildList {
        temperature?.let { add("temperature" to trim(it)) }
        topP?.let { add("top_p" to trim(it)) }
        topK?.let { add("top_k" to it.toString()) }
        minP?.let { add("min_p" to trim(it)) }
        typicalP?.let { add("typical_p" to trim(it)) }
        repeatPenalty?.let { add("rep_pen" to trim(it)) }
        repeatLastN?.let { add("rep_range" to it.toString()) }
        presencePenalty?.let { add("presence" to trim(it)) }
        frequencyPenalty?.let { add("frequency" to trim(it)) }
        mirostat?.let { add("mirostat" to it.toString()) }
        mirostatTau?.let { add("miro_tau" to trim(it)) }
        mirostatEta?.let { add("miro_eta" to trim(it)) }
    }

    private fun trim(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

object Catalog {
    /** Default characters seeded on first run; also the fallback when a referenced
     *  character has been deleted. The live, user-editable list lives in
     *  [characters], kept up to date by CharacterRepository. */
    val builtInCharacters: List<Character> = listOf(
        Character("Assistant", "You are a helpful, concise assistant.", null, "Helpful, concise general assistant.", DcColors.Primary, usesNamePrefixes = false),
        Character("Default", "", null, "Neutral. No system persona.", Color(0xFF7E57C2), usesNamePrefixes = false),
        Character("Coding Helper", "You are a senior software engineer. Explain clearly and back up explanations with concise code examples.", null, "Senior engineer. Explains with code.", DcColors.PrimaryDark, usesNamePrefixes = false),
        Character("Storyteller", "You are a vivid, imaginative storyteller. Answer with rich, evocative narration.", null, "Vivid, imaginative narrator.", Color(0xFF8E24AA)),
        Character("Marcus", "You are Marcus, a stoic philosopher. Respond calmly and thoughtfully, drawing on stoic principles.", "Greetings. What weighs on your mind today?", "Stoic philosopher. Calm, thoughtful.", Color(0xFF5C6BC0)),
    )

    /** The live character list. Replaced by CharacterRepository as the user edits;
     *  defaults to the built-ins so synchronous lookups work before it loads. */
    @Volatile
    var characters: List<Character> = builtInCharacters

    /** Palette used to colour user-created / imported characters by name. */
    private val palette: List<Color> = listOf(
        DcColors.Primary, DcColors.PrimaryDark,
        Color(0xFF7E57C2), Color(0xFF8E24AA), Color(0xFF5C6BC0),
        Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFFC2185B),
        Color(0xFF00897B), Color(0xFF3949AB),
    )

    /** Deterministic colour for a character that didn't bring its own. */
    fun colorFor(name: String): Color =
        palette[name.hashCode().absoluteValue % palette.size]

    val presets: List<Preset> = listOf(
        Preset("Default", temperature = 0.7, topP = 0.9, topK = 20, repeatPenalty = 1.15),
        Preset("min_p_v5", temperature = 1.26, minP = 0.08, repeatPenalty = 1.05),
        Preset("Precise", temperature = 0.1, topP = 0.1, topK = 40, repeatPenalty = 1.18),
        Preset("Creative", temperature = 1.1, topP = 0.95, topK = 100, repeatPenalty = 1.10),
        Preset("Llama-Precise", temperature = 0.7, topP = 0.1, topK = 40, repeatPenalty = 1.18),
        Preset("Midnight Enigma", temperature = 0.98, topP = 0.37, topK = 100, repeatPenalty = 1.18),
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
        characters.firstOrNull { it.name == name } ?: builtInCharacters.first()

    fun preset(name: String): Preset =
        presets.firstOrNull { it.name == name } ?: presets.first()

    /** Strip the ".gguf" suffix for compact display, matching the prototype. */
    fun shortModel(model: String): String = model.removeSuffix(".gguf")
}
