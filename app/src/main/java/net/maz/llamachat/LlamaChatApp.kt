package net.maz.llamachat

import android.app.Application
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.db.AppDatabase
import net.maz.llamachat.data.gen.GenerationController
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.net.LlamaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Live, in-memory connection state — not persisted, so each launch reconnects. */
class ServerSession {
    val connected = MutableStateFlow(false)
    /** True while a connection attempt (the startup probe or a manual connect) runs. */
    val connecting = MutableStateFlow(false)
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
    /** Live progress of the in-flight reply, shared with the GenerationService. */
    val generation = GenerationController()

    /** App-scoped, for connection probes that outlive any single screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        characterRepository // touch to load the library at startup
    }

    /**
     * Try to reach the server at [ip]/[port], persisting them and updating the
     * live [session] state. Returns null on success, or a user-facing error
     * message on failure. The conversations screen stays usable offline either way.
     */
    suspend fun connectTo(ip: String, port: String): String? {
        session.connecting.value = true
        val result = llamaClient.listModels(ip, port)
        session.connecting.value = false
        return result.fold(
            onSuccess = { models ->
                settingsRepository.saveServer(ip.trim(), port.trim())
                val list = models.ifEmpty { Catalog.fallbackModels }
                session.models.value = list
                // Pin a current model the server actually offers.
                if (settingsRepository.current().currentModel !in list) {
                    settingsRepository.setCurrentModel(list.first())
                }
                session.connected.value = true
                null
            },
            onFailure = { e ->
                session.connected.value = false
                "Couldn't reach server: ${e.message}"
            },
        )
    }

    /** Background connection probe against the saved server details. */
    fun probeConnection() {
        if (session.connecting.value) return
        appScope.launch {
            val s = settingsRepository.current()
            connectTo(s.ip, s.port)
        }
    }

    companion object {
        fun from(app: Application): LlamaChatApp = app as LlamaChatApp
    }
}
