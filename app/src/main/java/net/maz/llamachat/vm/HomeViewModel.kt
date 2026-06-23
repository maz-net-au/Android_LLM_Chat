package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.ServerSession
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.model.Conversation

data class HomeUiState(
    val conversations: List<Conversation> = emptyList(),
)

class HomeViewModel(
    private val repo: ConversationRepository,
    private val session: ServerSession,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        repo.conversations
            .map { HomeUiState(conversations = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun disconnect() {
        session.connected.value = false
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                HomeViewModel(app.conversationRepository, app.session)
            }
        }
    }
}
