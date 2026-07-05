package net.maz.llamachat

import android.app.Application
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.attach.AttachmentStore
import net.maz.llamachat.data.comfy.WorkflowStore
import net.maz.llamachat.data.db.AppDatabase
import net.maz.llamachat.data.gen.GenerationController
import net.maz.llamachat.data.gen.SummarizationController
import net.maz.llamachat.data.net.ComfyClient
import net.maz.llamachat.data.net.LlamaClient
import net.maz.llamachat.data.net.ServerHealthMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application entry point doubling as a tiny manual service locator. Keeping a
 * single LlamaClient / repository pair app-scoped avoids dragging in a DI
 * framework for an app this size.
 */
class LlamaChatApp : Application() {

    val settingsRepository by lazy { SettingsRepository(this) }
    val conversationRepository by lazy {
        ConversationRepository(AppDatabase.get(this).conversationDao())
    }
    // Eagerly built so the character library is loaded (and Catalog.characters
    // mirrored) before any screen resolves a conversation's character.
    val characterRepository by lazy { CharacterRepository(this) }
    val llamaClient by lazy { LlamaClient() }
    /** ComfyUI HTTP API (media generation): submit, poll, download. */
    val comfyClient by lazy { ComfyClient() }
    /** Files behind message image/audio attachments (app-private storage). */
    val attachmentStore by lazy { AttachmentStore(this) }
    /** Installed ComfyUI workflow packages + the shared base enums file. */
    val workflowStore by lazy { WorkflowStore(this) }
    /** Live progress of the in-flight reply, shared with the GenerationService. */
    val generation = GenerationController()
    /** Live state of the in-flight summarization, shared with the SummarizationService. */
    val summarization = SummarizationController()

    /** App-scoped, for health probes that outlive any single screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Polls llama-server and ComfyUI health and keeps the model list fresh. */
    val healthMonitor by lazy { ServerHealthMonitor(settingsRepository, llamaClient, appScope) }

    override fun onCreate() {
        super.onCreate()
        characterRepository // touch to load the library at startup
        healthMonitor.start()
    }

    companion object {
        fun from(app: Application): LlamaChatApp = app as LlamaChatApp
    }
}
