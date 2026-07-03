package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.SamplingParam
import net.maz.llamachat.data.model.samplingOverridesFrom
import net.maz.llamachat.data.model.samplingTextFrom
import net.maz.llamachat.data.net.ServerHealthMonitor

enum class NewMenu { NONE, CHARACTER, PRESET }

data class NewConvUiState(
    val editing: Boolean = false,
    val title: String = "",
    val character: String = "Assistant",
    val preset: String = "Default",
    val model: String = "",
    /** Substituted for `{{user}}`; defaults to the last-used name. */
    val userName: String = "user",
    /** Raw text per overridden sampling param; absent/blank = inherit from [preset]. */
    val samplingText: Map<SamplingParam, String> = emptyMap(),
    /** The conversation's running summary (edit mode only); sent as context for replies. */
    val summary: String = "",
    val openMenu: NewMenu = NewMenu.NONE,
    /** Set to the conversation id once started/saved, for the screen to navigate. */
    val startedId: Long? = null,
)

class NewConversationViewModel(
    private val settings: SettingsRepository,
    private val repo: ConversationRepository,
    private val health: ServerHealthMonitor,
    private val editConvId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(NewConvUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            if (editConvId != null) {
                repo.get(editConvId)?.let { c ->
                    _state.update {
                        it.copy(
                            editing = true,
                            title = if (c.title == "New chat") "" else c.title,
                            character = c.characterName,
                            preset = c.presetName,
                            model = c.model.ifEmpty { current.currentModel },
                            userName = c.userName,
                            samplingText = samplingTextFrom(c.sampling),
                            summary = c.summary,
                        )
                    }
                }
            } else {
                _state.update {
                    it.copy(
                        model = current.currentModel,
                        preset = current.currentPreset,
                        userName = current.userName,
                    )
                }
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setUserName(v: String) = _state.update { it.copy(userName = v) }
    fun setSummary(v: String) = _state.update { it.copy(summary = v) }
    fun selectCharacter(name: String) = _state.update { it.copy(character = name, openMenu = NewMenu.NONE) }
    // Switching the preset template starts from a clean slate (the new preset's values).
    fun selectPreset(name: String) = _state.update { it.copy(preset = name, samplingText = emptyMap(), openMenu = NewMenu.NONE) }
    fun selectModel(model: String) = _state.update { it.copy(model = model) }

    /** Set (or, when [value] is blank, clear) a per-conversation sampling override. */
    fun setSamplingParam(param: SamplingParam, value: String) = _state.update {
        val next = it.samplingText.toMutableMap()
        if (value.isBlank()) next.remove(param) else next[param] = value
        it.copy(samplingText = next)
    }

    /** Drop all overrides, falling back to the selected preset's values. */
    fun resetSampling() = _state.update { it.copy(samplingText = emptyMap()) }
    fun toggleMenu(menu: NewMenu) = _state.update {
        it.copy(openMenu = if (it.openMenu == menu) NewMenu.NONE else menu)
    }

    fun start() {
        val st = _state.value
        val title = st.title.trim()
        val userName = st.userName.trim().ifEmpty { "user" }
        val sampling = samplingOverridesFrom(st.samplingText)
        viewModelScope.launch {
            // Remember this name, model and preset as the defaults for future conversations.
            settings.setUserName(userName)
            settings.setCurrentPreset(st.preset)
            st.model.takeIf { it.isNotEmpty() }?.let { settings.setCurrentModel(it) }
            if (st.editing && editConvId != null) {
                repo.get(editConvId)?.let { existing ->
                    repo.save(
                        existing.copy(
                            characterName = st.character,
                            presetName = st.preset,
                            model = st.model,
                            userName = userName,
                            sampling = sampling,
                            summary = st.summary,
                            title = title.ifEmpty { existing.title },
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                _state.update { it.copy(startedId = editConvId) }
            } else {
                val now = System.currentTimeMillis()
                val id = IdGen.next()
                val current = settings.current()
                // Seed the character's greeting (if any) as the first assistant turn.
                // In transcript mode it's prefixed with the character name to match.
                val character = Catalog.character(st.character)
                val greeting = character.resolvedGreeting(userName)?.let {
                    if (character.usesNamePrefixes) "${st.character}: $it" else it
                }
                val seed = greeting?.let { listOf(ChatMessage.assistant(IdGen.next(), it)) }
                    ?: emptyList()
                repo.save(
                    Conversation(
                        id = id,
                        title = title.ifEmpty { "New chat" },
                        characterName = st.character,
                        presetName = st.preset,
                        model = st.model.ifEmpty { current.currentModel },
                        createdAt = now,
                        updatedAt = now,
                        userName = userName,
                        sampling = sampling,
                        messages = seed,
                    ),
                )
                _state.update { it.copy(startedId = id) }
            }
        }
    }

    val characters get() = Catalog.characters
    val presets get() = Catalog.presets
    /** Models the server reported, falling back to the built-in list when offline. */
    val models: List<String> get() = health.state.value.models.ifEmpty { Catalog.fallbackModels }
    fun modelLabel(): String = Catalog.shortModel(_state.value.model)

    companion object {
        fun factory(app: LlamaChatApp, editConvId: Long?) = viewModelFactory {
            initializer {
                NewConversationViewModel(
                    app.settingsRepository, app.conversationRepository,
                    app.healthMonitor, editConvId,
                )
            }
        }
    }
}
