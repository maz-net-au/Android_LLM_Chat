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
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.backup.BackupCodec
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.Conversation

enum class ConnStatus { CONNECTED, CONNECTING, OFFLINE }

/** Outcome of a restore, surfaced to the UI as a Toast. */
sealed interface ImportResult {
    /** Restored [title]; [missingCharacter] is the character name if it doesn't exist yet. */
    data class Restored(val title: String, val missingCharacter: String?) : ImportResult
    /** The file couldn't be read as a conversation backup at all. */
    data object Failed : ImportResult
}

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

    /**
     * Restore a conversation from a backup file's [text]. Best-effort decode; overwrites
     * any existing conversation with the same id. Reports whether the referenced character
     * is missing (the chat still restores and re-links if it's created later).
     */
    fun import(text: String, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val parsed = BackupCodec.decode(text)
            if (parsed == null) {
                onResult(ImportResult.Failed)
                return@launch
            }
            val conv = parsed.toDomain()
            repo.save(conv)
            val missing = conv.characterName.takeIf { name ->
                Catalog.characters.none { it.name == name }
            }
            onResult(ImportResult.Restored(conv.title, missing))
        }
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                HomeViewModel(app, app.conversationRepository)
            }
        }
    }
}
