package net.maz.llamachat.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import net.maz.llamachat.ui.theme.DcBrand

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

    private fun trim(d: Double): String = formatSampling(d)
}

/**
 * Per-conversation sampling overrides. Each field mirrors a [Preset] field; a null
 * means "inherit from the conversation's named preset". Persisted as a JSON blob on
 * the conversation so a chat can be tuned without affecting any other chat or the
 * shared preset library.
 */
@Serializable
data class SamplingOverrides(
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
    val isEmpty: Boolean get() = this == SamplingOverrides()
}

/**
 * The tunable sampling fields, paired with how to read each one from a [Preset]
 * (the inherited default) and a [SamplingOverrides] (the per-chat value). Drives
 * the override editor and (de)serialization without 12-way boilerplate at each site.
 */
enum class SamplingParam(
    val label: String,
    /** True for integer-valued params (top_k, mirostat, …); the rest are decimals. */
    val isInt: Boolean,
    /** llama.cpp's built-in default (from `common_params_sampling`) — what the server
     *  uses when the field is omitted. Surfaced in the editor so it can be typed in to
     *  revert a parameter the preset sets. */
    val default: Number,
    val fromPreset: (Preset) -> Number?,
    val fromOverrides: (SamplingOverrides) -> Number?,
    /** Value that neutralises/disables the sampler, when it differs from [default]
     *  (e.g. top_p 1.0 keeps everything; the penalties already disable at their
     *  default). Surfaced alongside the default so the sampler can be switched off. */
    val off: Number? = null,
) {
    TEMPERATURE("Temperature", false, 0.8, { it.temperature }, { it.temperature }, off = 1.0),
    TOP_P("Top P", false, 0.95, { it.topP }, { it.topP }, off = 1.0),
    TOP_K("Top K", true, 40, { it.topK }, { it.topK }, off = 0),
    MIN_P("Min P", false, 0.05, { it.minP }, { it.minP }, off = 0.0),
    TYPICAL_P("Typical P", false, 1.0, { it.typicalP }, { it.typicalP }),
    REPEAT_PENALTY("Repeat penalty", false, 1.0, { it.repeatPenalty }, { it.repeatPenalty }),
    REPEAT_LAST_N("Repeat last N", true, 64, { it.repeatLastN }, { it.repeatLastN }, off = 0),
    PRESENCE_PENALTY("Presence penalty", false, 0.0, { it.presencePenalty }, { it.presencePenalty }),
    FREQUENCY_PENALTY("Frequency penalty", false, 0.0, { it.frequencyPenalty }, { it.frequencyPenalty }),
    MIROSTAT("Mirostat (0/1/2)", true, 0, { it.mirostat }, { it.mirostat }),
    MIROSTAT_TAU("Mirostat tau", false, 5.0, { it.mirostatTau }, { it.mirostatTau }),
    MIROSTAT_ETA("Mirostat eta", false, 0.1, { it.mirostatEta }, { it.mirostatEta });
}

/** Compact numeric formatting shared by preset chips and the override editor:
 *  whole-valued doubles print without a trailing ".0". */
fun formatSampling(n: Number): String =
    if (n is Double && n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** Build [SamplingOverrides] from raw editor text, dropping blank/unparseable entries. */
fun samplingOverridesFrom(text: Map<SamplingParam, String>): SamplingOverrides {
    fun d(p: SamplingParam) = text[p]?.trim()?.toDoubleOrNull()
    fun i(p: SamplingParam) = text[p]?.trim()?.toIntOrNull()
    return SamplingOverrides(
        temperature = d(SamplingParam.TEMPERATURE),
        topP = d(SamplingParam.TOP_P),
        topK = i(SamplingParam.TOP_K),
        minP = d(SamplingParam.MIN_P),
        typicalP = d(SamplingParam.TYPICAL_P),
        repeatPenalty = d(SamplingParam.REPEAT_PENALTY),
        repeatLastN = i(SamplingParam.REPEAT_LAST_N),
        presencePenalty = d(SamplingParam.PRESENCE_PENALTY),
        frequencyPenalty = d(SamplingParam.FREQUENCY_PENALTY),
        mirostat = i(SamplingParam.MIROSTAT),
        mirostatTau = d(SamplingParam.MIROSTAT_TAU),
        mirostatEta = d(SamplingParam.MIROSTAT_ETA),
    )
}

/** Raw editor text for the fields [o] actually sets (so the editor pre-fills them). */
fun samplingTextFrom(o: SamplingOverrides): Map<SamplingParam, String> =
    SamplingParam.entries.mapNotNull { p -> p.fromOverrides(o)?.let { p to formatSampling(it) } }.toMap()

object Catalog {
    /** Default characters seeded on first run; also the fallback when a referenced
     *  character has been deleted. The live, user-editable list lives in
     *  [characters], kept up to date by CharacterRepository. */
    val builtInCharacters: List<Character> = listOf(
        Character("Assistant", "You are a helpful, concise assistant.", null, "Helpful, concise general assistant.", DcBrand.Primary, usesNamePrefixes = false),
        Character("Default", "", null, "Neutral. No system persona.", Color(0xFF7E57C2), usesNamePrefixes = false),
        Character("Coding Helper", "You are a senior software engineer. Explain clearly and back up explanations with concise code examples.", null, "Senior engineer. Explains with code.", DcBrand.PrimaryDark, usesNamePrefixes = false),
    )

    /** The live character list. Replaced by CharacterRepository as the user edits;
     *  defaults to the built-ins so synchronous lookups work before it loads. */
    @Volatile
    var characters: List<Character> = builtInCharacters

    /** Swatches offered in the character editor. Every entry is dark enough to
     *  carry white avatar text/icons. */
    val palette: List<Color> = listOf(
        DcBrand.Primary, DcBrand.PrimaryDark,
        Color(0xFF7E57C2), Color(0xFF8E24AA), Color(0xFF5C6BC0),
        Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5),
        Color(0xFF00897B), Color(0xFF26A69A), Color(0xFF43A047),
        Color(0xFF7CB342), Color(0xFFF9A825), Color(0xFFEF6C00),
        Color(0xFFF4511E), Color(0xFFE53935), Color(0xFFD81B60),
        Color(0xFFC2185B), Color(0xFF6D4C41), Color(0xFF546E7A),
    )

    /** Starting colour for characters that arrive without one (imported, generated):
     *  it's settable afterwards in the editor like any other. */
    val defaultColor: Color = palette.first()

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
