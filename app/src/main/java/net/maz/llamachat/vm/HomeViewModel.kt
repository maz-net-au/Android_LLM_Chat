package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.ServerSession
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.Conversation

data class HomeUiState(
    val conversations: List<Conversation> = emptyList(),
    val models: List<String> = emptyList(),
    val currentModel: String = "",
)

class HomeViewModel(
    private val settings: SettingsRepository,
    private val repo: ConversationRepository,
    private val session: ServerSession,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        combine(repo.conversations, settings.settings, session.models) { convs, s, models ->
            HomeUiState(
                conversations = convs,
                models = models.ifEmpty { Catalog.fallbackModels },
                currentModel = s.currentModel,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectModel(model: String) {
        viewModelScope.launch { settings.setCurrentModel(model) }
    }

    fun disconnect() {
        session.connected.value = false
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                HomeViewModel(app.settingsRepository, app.conversationRepository, app.session)
            }
        }
    }
}
