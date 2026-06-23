package net.maz.llamachat

import android.app.Application
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.db.AppDatabase
import net.maz.llamachat.data.net.LlamaClient
import kotlinx.coroutines.flow.MutableStateFlow

/** Live, in-memory connection state — not persisted, so each launch reconnects. */
class ServerSession {
    val connected = MutableStateFlow(false)
    /** Models reported by the server's /v1/models after connecting. */
    val models = MutableStateFlow<List<String>>(emptyList())
}

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
    val session = ServerSession()

    override fun onCreate() {
        super.onCreate()
        characterRepository // touch to load the library at startup
    }

    companion object {
        fun from(app: Application): LlamaChatApp = app as LlamaChatApp
    }
}
