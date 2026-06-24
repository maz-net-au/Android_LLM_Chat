package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.model.Conversation

enum class ConnStatus { CONNECTED, CONNECTING, OFFLINE }

data class HomeUiState(
    val conversations: List<Conversation> = emptyList(),
    val connection: ConnStatus = ConnStatus.OFFLINE,
)

class HomeViewModel(
    private val app: LlamaChatApp,
    private val repo: ConversationRepository,
) : ViewModel() {

    private val session = app.session

    val state: StateFlow<HomeUiState> =
        combine(repo.conversations, session.connected, session.connecting) { convs, connected, connecting ->
            HomeUiState(
                conversations = convs,
                connection = when {
                    connected -> ConnStatus.CONNECTED
                    connecting -> ConnStatus.CONNECTING
                    else -> ConnStatus.OFFLINE
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    /** Re-check the saved server when the user returns to this screen. */
    fun refreshConnection() = app.probeConnection()

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                HomeViewModel(app, app.conversationRepository)
            }
        }
    }
}
