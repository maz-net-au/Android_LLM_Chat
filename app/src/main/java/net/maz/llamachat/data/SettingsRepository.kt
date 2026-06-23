package net.maz.llamachat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.maz.llamachat.data.model.Catalog

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists the server connection details and the currently-selected model. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val IP = stringPreferencesKey("server_ip")
        val PORT = stringPreferencesKey("server_port")
        val CURRENT_MODEL = stringPreferencesKey("current_model")
        val USER_NAME = stringPreferencesKey("user_name")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    data class Settings(
        val ip: String,
        val port: String,
        val currentModel: String,
        /** Default name for new conversations' `{{user}}`; the last name the user set. */
        val userName: String,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            ip = prefs[Keys.IP] ?: "192.168.1.42",
            port = prefs[Keys.PORT] ?: "8080",
            currentModel = prefs[Keys.CURRENT_MODEL] ?: Catalog.fallbackModels.first(),
            userName = prefs[Keys.USER_NAME]?.takeIf { it.isNotBlank() } ?: "user",
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun saveServer(ip: String, port: String) {
        context.dataStore.edit {
            it[Keys.IP] = ip
            it[Keys.PORT] = port
        }
    }

    suspend fun setCurrentModel(model: String) {
        context.dataStore.edit { it[Keys.CURRENT_MODEL] = model }
    }

    /** Remember the most recently used name as the default for future conversations. */
    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun wasSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() {
        context.dataStore.edit { it[Keys.SEEDED] = true }
    }
}
