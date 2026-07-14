package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.backup.BackupCodec
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.Conversation

/** Outcome of a restore, surfaced to the UI as a Toast. */
sealed interface ImportResult {
    /** Restored [title]; [missingCharacter] is the character name if it doesn't exist yet. */
    data class Restored(val title: String, val missingCharacter: String?) : ImportResult
    /** The file couldn't be read as a conversation backup at all. */
    data object Failed : ImportResult
}

data class HomeUiState(
    val conversations: List<Conversation> = emptyList(),
)

class HomeViewModel(
    private val app: LlamaChatApp,
    private val repo: ConversationRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        repo.conversations
            .map { HomeUiState(conversations = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

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
            // Restore any inlined attachment bytes into the conversation's dir (overwrite-by-id
            // means conv.id matches what the metadata points at). Text-only files carry none.
            if (parsed.attachmentBytes.isNotEmpty()) {
                launch(Dispatchers.IO) {
                    conv.messages.asSequence().flatMap { it.attachments }.forEach { att ->
                        parsed.attachmentBytes[att.fileName]?.let { b64 ->
                            app.attachmentStore.writeBase64(conv.id, att, b64)
                        }
                    }
                }
            }
            val missing = conv.characterName.takeIf { name ->
                Catalog.characters.none { it.name == name }
            }
            onResult(ImportResult.Restored(conv.title, missing))
        }
    }

    /** Serialize [conv] to the text backup format for export to a user-picked file.
     *  The list already carries full message history, so no DB round-trip is needed. */
    fun exportJson(conv: Conversation): String = BackupCodec.encode(conv, app.attachmentStore)

    /** Permanently delete a conversation (and its attachment files) by id. */
    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            launch(Dispatchers.IO) { app.attachmentStore.deleteAll(id) }
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
