package net.maz.llamachat.vm

import android.net.Uri
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
import kotlinx.coroutines.withContext
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
     * Restore a conversation from the backup file at [uri]. Best-effort decode; overwrites
     * any existing conversation with the same id. Reports whether the referenced character
     * is missing (the chat still restores and re-links if it's created later). Reads and
     * writes on the IO dispatcher so a large (image-heavy) backup doesn't block the UI.
     */
    fun import(uri: Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openInputStream(uri)?.use { BackupCodec.decodeFrom(it) }
                }.getOrNull()
            }
            if (parsed == null) {
                onResult(ImportResult.Failed)
                return@launch
            }
            val conv = parsed.toDomain()
            repo.save(conv)
            // Restore any inlined attachment bytes into the conversation's dir (overwrite-by-id
            // means conv.id matches what the metadata points at). Text-only files carry none.
            if (parsed.attachmentBytes.isNotEmpty()) {
                withContext(Dispatchers.IO) {
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

    /** Stream [conv] to the backup file at [uri], off the main thread. Attachment bytes are
     *  streamed straight to disk (see [BackupCodec.encodeTo]) so image-heavy conversations
     *  don't OOM. [onDone] reports success/failure back on the main thread. */
    fun export(conv: Conversation, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use {
                        BackupCodec.encodeTo(it, conv, app.attachmentStore)
                    } ?: error("could not open output stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

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
