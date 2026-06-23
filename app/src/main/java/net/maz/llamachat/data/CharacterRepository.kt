package net.maz.llamachat.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.Character
import java.io.File

/**
 * Stores the user's character library as a JSON file in app storage. The live
 * list is exposed as a [characters] StateFlow for the UI and mirrored into
 * [Catalog.characters] so the domain's synchronous lookups (e.g.
 * `Conversation.character`) resolve user characters too. Seeded with the
 * built-ins on first run.
 */
class CharacterRepository(context: Context) {

    @Serializable
    private data class Stored(
        val name: String,
        val context: String,
        val greeting: String? = null,
        val description: String = "",
        val colorArgb: Int,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(context.filesDir, "characters.json")

    private val _characters = MutableStateFlow(load())
    val characters = _characters.asStateFlow()

    init {
        Catalog.characters = _characters.value
    }

    fun get(name: String): Character? = _characters.value.firstOrNull { it.name == name }

    /** Insert or update by name. If [originalName] differs, it's a rename: the old
     *  entry is removed. Returns the resolved (possibly de-duplicated) name. */
    suspend fun upsert(
        originalName: String?,
        name: String,
        context: String,
        greeting: String?,
        description: String,
        color: Color? = null,
    ): String = withContext(Dispatchers.IO) {
        val without = _characters.value.filterNot {
            it.name == originalName || it.name == name
        }
        val finalName = uniqueName(name, without)
        val resolvedColor = color
            ?: get(originalName ?: name)?.color
            ?: Catalog.colorFor(finalName)
        val updated = without + Character(
            name = finalName,
            context = context,
            greeting = greeting?.takeIf { it.isNotBlank() },
            description = description,
            color = resolvedColor,
        )
        persist(updated.sortedBy { it.name.lowercase() })
        finalName
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        persist(_characters.value.filterNot { it.name == name })
    }

    /** Import TGW-format YAML documents, skipping any that don't parse. Returns
     *  the number successfully imported. */
    suspend fun import(yamlDocs: List<String>): Int = withContext(Dispatchers.IO) {
        var added = 0
        var working = _characters.value
        for (doc in yamlDocs) {
            val parsed = CharacterYaml.parse(doc) ?: continue
            val base = working.filterNot { it.name == parsed.name }
            val finalName = uniqueName(parsed.name, base)
            working = base + Character(
                name = finalName,
                context = parsed.context,
                greeting = parsed.greeting,
                description = "",
                color = Catalog.colorFor(finalName),
            )
            added++
        }
        if (added > 0) persist(working.sortedBy { it.name.lowercase() })
        added
    }

    /** TGW-format YAML for one character, or null if it no longer exists. */
    fun exportYaml(name: String): String? {
        val c = get(name) ?: return null
        return CharacterYaml.dump(c.name, c.context, c.greeting)
    }

    // ---- internals ---------------------------------------------------------

    private fun uniqueName(desired: String, existing: List<Character>): String {
        val taken = existing.map { it.name.lowercase() }.toSet()
        if (desired.lowercase() !in taken) return desired
        var n = 2
        while ("$desired ($n)".lowercase() in taken) n++
        return "$desired ($n)"
    }

    private fun persist(list: List<Character>) {
        _characters.value = list
        Catalog.characters = list
        runCatching {
            file.writeText(json.encodeToString(list.map { it.toStored() }))
        }
    }

    private fun load(): List<Character> {
        if (!file.exists()) {
            val seed = Catalog.builtInCharacters
            runCatching { file.writeText(json.encodeToString(seed.map { it.toStored() })) }
            return seed
        }
        return runCatching {
            json.decodeFromString<List<Stored>>(file.readText()).map { it.toCharacter() }
        }.getOrDefault(Catalog.builtInCharacters)
    }

    private fun Character.toStored() =
        Stored(name, context, greeting, description, color.toArgb())

    private fun Stored.toCharacter() =
        Character(name, context, greeting, description, Color(colorArgb))
}
