package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.gen.CharacterGenerator
import net.maz.llamachat.data.gen.CharacterSeed
import net.maz.llamachat.data.net.LlamaClient

enum class GenPhase { INPUT, GENERATING, REVIEW }

data class GeneratorUiState(
    val seed: CharacterSeed = CharacterSeed(),
    val phase: GenPhase = GenPhase.INPUT,
    /** Editable review fields, populated from the model's draft on success. */
    val name: String = "",
    val greeting: String = "",
    val context: String = "",
    val description: String = "",
    /** A user-facing error from the last attempt; cleared when a new one starts. */
    val error: String? = null,
    /** Final name once saved, for the screen to navigate back. */
    val savedName: String? = null,
)

/**
 * Backs the "Generate a character" screen. Unlike reply generation, this is a
 * one-shot, conversation-less request, so it collects [LlamaClient.streamChat]
 * straight to completion here rather than going through the foreground
 * [net.maz.llamachat.data.gen.GenerationService]. The result is held as editable
 * review fields; nothing is persisted until [save].
 */
class GeneratorViewModel(
    private val settings: SettingsRepository,
    private val client: LlamaClient,
    private val repo: CharacterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state = _state.asStateFlow()

    private var job: Job? = null

    fun setGender(v: String) = updateSeed { it.copy(gender = v) }
    fun setAge(v: String) = updateSeed { it.copy(age = v) }
    fun setProfession(v: String) = updateSeed { it.copy(profession = v) }
    fun setVibe(v: String) = updateSeed { it.copy(vibe = v) }

    /** Fill every seed field with a random pick; the user can still tweak before generating. */
    fun surpriseMe() = _state.update { it.copy(seed = CharacterGenerator.surprise(), error = null) }

    fun setName(v: String) = _state.update { it.copy(name = v) }
    fun setGreeting(v: String) = _state.update { it.copy(greeting = v) }
    fun setContext(v: String) = _state.update { it.copy(context = v) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }

    /** Return from the review back to the seed inputs to adjust constraints. */
    fun editSeeds() = _state.update { it.copy(phase = GenPhase.INPUT, error = null) }

    /** Send the current seeds to the model. Regenerate is just calling this again:
     *  the seeds are unchanged but fresh spark words make the result diverge. */
    fun generate() {
        val seed = _state.value.seed
        job?.cancel() // single-flight: a new request supersedes any in-flight one
        _state.update { it.copy(phase = GenPhase.GENERATING, error = null) }
        job = viewModelScope.launch {
            val s = settings.current()
            val request = CharacterGenerator.request(seed, s.currentModel)
            val sb = StringBuilder()
            runCatching {
                client.streamChat(s.ip, s.port, request).collect { sb.append(it) }
            }.fold(
                onSuccess = {
                    val text = sb.toString()
                    if (text.isBlank()) {
                        fail("The model returned nothing — try again.")
                    } else {
                        val draft = CharacterGenerator.parse(text, seed)
                        _state.update {
                            it.copy(
                                phase = GenPhase.REVIEW,
                                name = draft.name,
                                greeting = draft.greeting.orEmpty(),
                                context = draft.context,
                                description = draft.description,
                                error = null,
                            )
                        }
                    }
                },
                onFailure = { e -> fail("Couldn't generate: ${e.message ?: "unknown error"}") },
            )
        }
    }

    /** Persist the reviewed character, then expose its resolved name to navigate back. */
    fun save() {
        val st = _state.value
        val name = st.name.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val finalName = repo.upsert(
                originalName = null,
                name = name,
                context = st.context,
                greeting = st.greeting.ifBlank { null },
                description = st.description.trim(),
                // Generated personas are roleplay characters: transcript framing fits,
                // matching the default for hand-made and imported characters.
                usesNamePrefixes = true,
            )
            _state.update { it.copy(savedName = finalName) }
        }
    }

    private fun fail(message: String) =
        _state.update { it.copy(phase = GenPhase.INPUT, error = message) }

    private fun updateSeed(f: (CharacterSeed) -> CharacterSeed) =
        _state.update { it.copy(seed = f(it.seed)) }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                GeneratorViewModel(app.settingsRepository, app.llamaClient, app.characterRepository)
            }
        }
    }
}
