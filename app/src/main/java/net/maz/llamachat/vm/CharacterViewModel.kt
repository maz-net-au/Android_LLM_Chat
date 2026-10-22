package net.maz.llamachat.vm

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.gen.CharacterGenerator
import net.maz.llamachat.data.model.Character
import net.maz.llamachat.data.net.LlamaClient

/** Progress of an in-place "adjust the context with the LLM" request. */
data class ReviseUiState(
    val running: Boolean = false,
    /** A user-facing error from the last attempt; cleared when a new one starts. */
    val error: String? = null,
)

/** Backs the character library screens: list, create/edit, delete, import/export. */
class CharacterViewModel(
    private val repo: CharacterRepository,
    private val settings: SettingsRepository,
    private val client: LlamaClient,
) : ViewModel() {

    val characters = repo.characters

    private val _revise = MutableStateFlow(ReviseUiState())
    val revise = _revise.asStateFlow()

    private var reviseJob: Job? = null

    fun get(name: String): Character? = repo.get(name)

    /** Create or update a character. [originalName] is null when creating. */
    fun save(
        originalName: String?,
        name: String,
        context: String,
        greeting: String?,
        description: String,
        usesNamePrefixes: Boolean,
        color: Color,
    ) {
        viewModelScope.launch {
            repo.upsert(originalName, name.trim(), context, greeting, description.trim(), usesNamePrefixes, color)
        }
    }

    fun delete(name: String) {
        viewModelScope.launch { repo.delete(name) }
    }

    /**
     * Ask the character-generation model to rewrite [currentContext] per
     * [instruction], handing the new block back through [onResult] so the edit
     * screen can drop it into its (still unsaved) context field. Like the
     * generator, this is a one-shot request collected straight to completion
     * rather than a foreground service.
     */
    fun reviseContext(
        name: String,
        currentContext: String,
        instruction: String,
        onResult: (String) -> Unit,
    ) {
        if (instruction.isBlank()) return
        reviseJob?.cancel() // single-flight: a new request supersedes any in-flight one
        _revise.value = ReviseUiState(running = true)
        reviseJob = viewModelScope.launch {
            val s = settings.current()
            val model = s.characterGenModel.ifEmpty { s.currentModel }
            val request = CharacterGenerator.reviseRequest(name, currentContext, instruction, model)
            val sb = StringBuilder()
            runCatching {
                client.streamChat(s.ip, s.port, request).collect { sb.append(it) }
            }.fold(
                onSuccess = {
                    val text = CharacterGenerator.parseRevision(sb.toString())
                    if (text.isBlank()) {
                        _revise.value = ReviseUiState(error = "The model returned nothing — try again.")
                    } else {
                        _revise.value = ReviseUiState()
                        onResult(text)
                    }
                },
                onFailure = { e ->
                    _revise.value = ReviseUiState(error = "Couldn't adjust: ${e.message ?: "unknown error"}")
                },
            )
        }
    }

    /** YAML for one character, for sharing/exporting to a file. */
    fun exportYaml(name: String): String? = repo.exportYaml(name)

    /** Import one or more YAML documents; reports how many were added. */
    fun import(yamlDocs: List<String>, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.import(yamlDocs)) }
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                CharacterViewModel(app.characterRepository, app.settingsRepository, app.llamaClient)
            }
        }
    }
}
