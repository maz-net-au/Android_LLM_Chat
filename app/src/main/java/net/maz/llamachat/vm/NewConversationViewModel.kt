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
import net.maz.llamachat.ServerSession
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation

enum class NewMenu { NONE, CHARACTER, PRESET }

data class NewConvUiState(
    val editing: Boolean = false,
    val title: String = "",
    val character: String = "Assistant",
    val preset: String = "Default",
    val model: String = "",
    val openMenu: NewMenu = NewMenu.NONE,
    /** Set to the conversation id once started/saved, for the screen to navigate. */
    val startedId: Long? = null,
)

class NewConversationViewModel(
    private val settings: SettingsRepository,
    private val repo: ConversationRepository,
    private val session: ServerSession,
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
                        )
                    }
                }
            } else {
                _state.update { it.copy(model = current.currentModel) }
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun selectCharacter(name: String) = _state.update { it.copy(character = name, openMenu = NewMenu.NONE) }
    fun selectPreset(name: String) = _state.update { it.copy(preset = name, openMenu = NewMenu.NONE) }
    fun selectModel(model: String) = _state.update { it.copy(model = model) }
    fun toggleMenu(menu: NewMenu) = _state.update {
        it.copy(openMenu = if (it.openMenu == menu) NewMenu.NONE else menu)
    }

    fun start() {
        val st = _state.value
        val title = st.title.trim()
        viewModelScope.launch {
            if (st.editing && editConvId != null) {
                repo.get(editConvId)?.let { existing ->
                    repo.save(
                        existing.copy(
                            characterName = st.character,
                            presetName = st.preset,
                            model = st.model,
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
                val greeting = Catalog.character(st.character).resolvedGreeting(current.userName)
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
    val models: List<String> get() = session.models.value.ifEmpty { Catalog.fallbackModels }
    fun modelLabel(): String = Catalog.shortModel(_state.value.model)

    companion object {
        fun factory(app: LlamaChatApp, editConvId: Long?) = viewModelFactory {
            initializer {
                NewConversationViewModel(
                    app.settingsRepository, app.conversationRepository,
                    app.session, editConvId,
                )
            }
        }
    }
}
