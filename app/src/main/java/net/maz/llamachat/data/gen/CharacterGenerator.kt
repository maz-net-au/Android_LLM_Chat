package net.maz.llamachat.data.gen

import net.maz.llamachat.data.CharacterYaml
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.net.ChatRequest
import net.maz.llamachat.data.net.apiText

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
                apiText("system", SYSTEM),
                apiText("user", userPrompt(seed)),
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
            appendLine("Invent ONE believable, everyday person for a casual, slice-of-life chat.")
            appendLine()
            appendLine("Honor any constraint that is filled in; freely invent the rest:")
            appendLine("- Gender: ${orAny(seed.gender)}")
            appendLine("- Age: ${orAny(seed.age)}")
            appendLine("- Profession or hobby: ${orAny(seed.profession)}")
            appendLine("- Extra notes / vibe: ${seed.vibe.trim().ifBlank { "none" }}")
            appendLine()
            appendLine("Keep them grounded and realistic — an ordinary person you might actually")
            appendLine("meet, not a larger-than-life or fantastical figure. Give them a common,")
            appendLine("natural-sounding name and a couple of specific, relatable everyday details.")
            appendLine("Let these things quietly inspire those details, without naming them: $sparks.")
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
        "You design believable, down-to-earth people for a casual, slice-of-life " +
            "chat app — ordinary characters with realistic lives and relatable quirks. " +
            "You always answer with a single YAML document and nothing else."

    private val GENDERS = listOf("female", "male", "non-binary", "ambiguous", "any")

    private val AGES = listOf(
        "toddler", "child", "school aged", "preteen", "tween", "early teens",
	"late teens", "early 20s", "mid 20s", "late 20s", "early 30s",
        "mid 30s", "early 40s", "late 40s",
    )

    private val PROFESSIONS = listOf(
        // Everyday jobs
        "nurse", "schoolteacher", "barista", "accountant", "plumber", "graphic designer",
        "bus driver", "librarian", "chef", "electrician", "software developer",
        "retail manager", "florist", "mechanic", "real estate agent", "physical therapist",
        "bartender", "social worker", "carpenter", "hairdresser", "pharmacist",
        "paramedic", "veterinary tech", "baker", "postal worker", "dental hygienist",
        "office administrator", "warehouse worker", "barber", "midwife", "optometrist",
        "primary school teacher", "freelance illustrator", "call-centre agent", "farmer",
        "personal trainer", "house painter", "bookkeeper", "delivery driver", "waiter",
        // Everyday hobbies and pursuits
        "amateur baker", "weekend hiker", "allotment gardener", "marathon runner",
        "choir member", "amateur photographer", "home cook", "knitting-circle regular",
        "guitarist in a covers band", "board-game collector", "birdwatcher",
        "book-club regular", "amateur potter", "fishing enthusiast", "cyclist",
    )

    private val VIBES = listOf(
        "warm but guarded", "relentlessly cheerful", "dryly sarcastic", "haunted by a secret",
        "endlessly curious", "world-weary and kind", "chaotic and impulsive",
        "precise and formal", "dreamy and distracted", "fiercely loyal",
        "mischievous", "quietly grieving", "wildly ambitious", "gentle and patient",
    )

    private val SPARKS = listOf(
        // Home & domestic life
        "kettle", "houseplants", "leftovers", "laundry", "spare key", "junk drawer",
        "fridge magnets", "doormat", "slippers", "to-do list", "spare room",
        "garden shed", "back porch", "kitchen radio", "good mugs", "hand-me-downs",
        "photo album", "thermostat", "recipe card", "spare change",
        // Food & drink
        "coffee", "toast", "leftover curry", "homemade jam", "corner café",
        "packed lunch", "Sunday roast", "biscuit tin", "takeaway", "herbal tea",
        "birthday cake", "lukewarm tea", "midnight snack", "burnt dinner",
        // Routines & places
        "commute", "school run", "lunch break", "night shift", "queue", "bus stop",
        "supermarket", "waiting room", "parking", "local pub", "gym membership",
        "morning alarm", "weekend lie-in", "office kitchen", "neighbourhood",
        // Weather & seasons (everyday)
        "drizzle", "heatwave", "first frost", "muddy boots", "umbrella", "sunburn",
        "autumn leaves", "long evenings", "grey morning", "snow day",
        // Hobbies & small pleasures
        "crossword", "knitting", "playlist", "paperback", "podcast", "houseplant",
        "five-a-side", "allotment", "jigsaw", "dog walk", "karaoke", "Sunday market",
        "old records", "weekend project", "group chat",
        // Relationships & feelings
        "old friends", "in-jokes", "first crush", "homesickness", "fresh start",
        "second chances", "stage fright", "nostalgia", "small talk", "white lie",
        "long-distance", "reunion", "awkward silence", "quiet pride", "guilty pleasure",
        // Objects & odds and ends
        "bicycle", "phone charger", "loose button", "shoebox of letters", "ticket stub",
        "worn-out trainers", "house plant", "spare tyre", "battered notebook",
        "headphones", "lucky pen", "fridge poetry", "stack of mail", "broken watch",
    )
}
