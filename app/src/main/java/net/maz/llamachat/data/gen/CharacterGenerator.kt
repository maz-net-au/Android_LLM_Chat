package net.maz.llamachat.data.gen

import net.maz.llamachat.data.CharacterYaml
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.net.ApiMessage
import net.maz.llamachat.data.net.ChatRequest

/** Seed inputs for a generation. Any field may be blank — the model invents the
 *  rest, so this doubles as the "surprise me" payload when all four are filled in. */
data class CharacterSeed(
    val gender: String = "",
    val age: String = "",
    val profession: String = "",
    val vibe: String = "",
)

/** A generated, not-yet-saved character mapped onto the editable [net.maz.llamachat.data.model.Character] fields. */
data class CharacterDraft(
    val name: String,
    val context: String,
    val greeting: String?,
    val description: String = "",
)

/**
 * Turns a [CharacterSeed] into a llama-server request that invents a character,
 * and parses the reply back into a [CharacterDraft].
 *
 * Novelty is engineered rather than hoped for, because small local models drift
 * hard toward stock archetypes: the sampling runs hot (the "Creative" preset),
 * a few random "spark" words are injected on every call (so each Regenerate
 * diverges), and the prompt explicitly pushes away from predictable names and
 * asks for a surprising contradiction. The model is asked to reply in the same
 * TGW YAML the importer already understands, so [CharacterYaml.parse] does the
 * mapping; a malformed reply falls back to raw prose in [CharacterDraft.context]
 * so the user still gets something editable instead of a dead end.
 */
object CharacterGenerator {

    /** The chat request that asks the model to invent a character. */
    fun request(seed: CharacterSeed, model: String): ChatRequest {
        val preset = Catalog.preset("Creative")
        return ChatRequest(
            model = model,
            messages = listOf(
                ApiMessage("system", SYSTEM),
                ApiMessage("user", userPrompt(seed)),
            ),
            stream = true,
            maxTokens = 700, // a character sheet is short; bound runaway output
            temperature = preset.temperature,
            topP = preset.topP,
            topK = preset.topK,
            minP = preset.minP,
            repeatPenalty = preset.repeatPenalty,
        )
    }

    /** Map the model's reply to a draft, falling back to raw prose on malformed YAML. */
    fun parse(raw: String, seed: CharacterSeed): CharacterDraft {
        CharacterYaml.parse(extractYaml(raw))?.let {
            return CharacterDraft(it.name, it.context, it.greeting)
        }
        val fallbackName = seed.profession.trim().ifBlank { "New character" }
            .replaceFirstChar { c -> c.uppercase() }
        return CharacterDraft(name = fallbackName, context = raw.trim(), greeting = null)
    }

    /** Random values for every field, for the "Surprise me" button. */
    fun surprise(): CharacterSeed = CharacterSeed(
        gender = GENDERS.random(),
        age = AGES.random(),
        profession = PROFESSIONS.random(),
        vibe = VIBES.random(),
    )

    // ---- internals ---------------------------------------------------------

    private fun userPrompt(seed: CharacterSeed): String {
        val sparks = SPARKS.shuffled().take(3).joinToString(", ")
        fun orAny(v: String) = v.trim().ifBlank { "any — you choose" }
        return buildString {
            appendLine("Invent ONE original fictional character for a roleplay chat.")
            appendLine()
            appendLine("Honor any constraint that is filled in; freely invent the rest:")
            appendLine("- Gender: ${orAny(seed.gender)}")
            appendLine("- Age: ${orAny(seed.age)}")
            appendLine("- Profession or hobby: ${orAny(seed.profession)}")
            appendLine("- Extra notes / vibe: ${seed.vibe.trim().ifBlank { "none" }}")
            appendLine()
            appendLine("Make them genuinely novel: avoid stock archetypes and the most")
            appendLine("predictable names, and give them one specific, surprising contradiction.")
            appendLine("Let these words quietly inspire the details, without naming them: $sparks.")
            appendLine()
            appendLine("Reply with ONLY this YAML — no commentary, no code fences:")
            appendLine("name: <a fitting, non-generic name>")
            appendLine("greeting: \"<a short, in-character first line spoken to {{user}}>\"")
            appendLine("context: |")
            appendLine("  <2-4 sentences in the second person, starting \"You are {{char}}, ...\",")
            appendLine("  covering personality, background and speech style>")
        }
    }

    /** Pull a fenced ```…``` block if the model wrapped its YAML in one; otherwise
     *  use the whole reply. Either way the result is fed to the YAML parser. */
    private fun extractYaml(raw: String): String {
        val fenced = Regex("```(?:ya?ml)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)
        return (fenced ?: raw).trim()
    }

    private const val SYSTEM =
        "You are an imaginative character designer who invents vivid, original " +
            "personas for a roleplay chat app. You always answer with a single YAML " +
            "document and nothing else."

    private val GENDERS = listOf("female", "male", "non-binary", "ambiguous", "any")

    private val AGES = listOf(
        "teenager", "early 20s", "late 20s", "early 30s", "40s",
        "50s", "60s", "elderly", "ageless", "centuries old",
    )

    private val PROFESSIONS = listOf(
        "lighthouse keeper", "marine biologist", "street magician", "war correspondent",
        "perfumer", "glacier guide", "forensic accountant", "puppet maker",
        "deep-sea welder", "cartographer of imaginary places", "night-shift radio host",
        "beekeeper", "video game composer", "cave diver", "obituary writer",
        "competitive sheepdog trainer", "antique clock restorer", "storm chaser",
        "submarine cook", "museum night guard", "retired stunt double", "tea sommelier",
        "volcanologist", "ghostwriter", "carnival fortune teller", "bridge inspector",
    )

    private val VIBES = listOf(
        "warm but guarded", "relentlessly cheerful", "dryly sarcastic", "haunted by a secret",
        "endlessly curious", "world-weary and kind", "chaotic and impulsive",
        "precise and formal", "dreamy and distracted", "fiercely loyal",
        "mischievous", "quietly grieving", "wildly ambitious", "gentle and patient",
    )

    private val SPARKS = listOf(
        // Materials & textures
        "salt", "brass", "velvet", "rust", "copper", "quartz", "cedar", "ash",
        "marrow", "gravel", "pearl", "wire", "silk", "iron", "amber", "obsidian",
        "chalk", "resin", "linen", "slate", "porcelain", "tin", "wax", "flint",
        "moss", "lacquer", "sandstone", "graphite", "bone", "clay",
        // Weather & sky
        "thunder", "frost", "rain", "tide", "drift", "fathom", "mist", "monsoon",
        "aurora", "eclipse", "squall", "drizzle", "haze", "gale", "dusk", "dawn",
        "solstice", "comet", "thaw", "blizzard",
        // Light, fire & sound
        "ember", "static", "neon", "echo", "smoke", "lantern", "spark", "flare",
        "glow", "shimmer", "hum", "chime", "tremor", "whisper", "siren", "crackle",
        "candle", "beacon", "flicker", "resonance",
        // Objects & instruments
        "compass", "ledger", "spindle", "hollow", "anchor", "lockpick", "telescope",
        "metronome", "sextant", "typewriter", "gramophone", "kaleidoscope", "abacus",
        "harpoon", "loom", "bellows", "scalpel", "trowel", "astrolabe", "quill",
        // Nature & creatures
        "moth", "honey", "raven", "fox", "kelp", "ivy", "lichen", "cicada", "heron",
        "wolf", "nettle", "thistle", "barnacle", "firefly", "jackdaw", "wisteria",
        "starling", "urchin", "fern", "hawthorn",
        // Abstract & temperament
        "vows", "exile", "debt", "rumor", "mercy", "grudge", "omen", "ritual",
        "lullaby", "cipher", "pilgrimage", "wager", "feud", "confession", "alibi",
        "inheritance", "superstition", "nostalgia", "vendetta", "epiphany",
    )
}
