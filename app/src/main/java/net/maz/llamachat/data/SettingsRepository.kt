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
        val COMFY_PORT = stringPreferencesKey("comfy_port")
        val CURRENT_MODEL = stringPreferencesKey("current_model")
        val CURRENT_PRESET = stringPreferencesKey("current_preset")
        val CHARACTER_GEN_MODEL = stringPreferencesKey("character_gen_model")
        val I2T_MODEL = stringPreferencesKey("image_to_text_model")
        val I2T_CHARACTER = stringPreferencesKey("image_to_text_character")
        val I2T_PRESET = stringPreferencesKey("image_to_text_preset")
        val USER_NAME = stringPreferencesKey("user_name")
        val SEEDED = booleanPreferencesKey("seeded")
    }

    data class Settings(
        val ip: String,
        val port: String,
        /** ComfyUI port on the same host; llama-server uses [port]. */
        val comfyPort: String,
        val currentModel: String,
        /** Last selected preset; the default for new conversations. */
        val currentPreset: String,
        /** Model used by the "Generate a character" flow. Blank = follow [currentModel]. */
        val characterGenModel: String,
        /** Vision model the launcher's "Image to Text" quick chat is pinned to. */
        val imageToTextModel: String,
        /** Character (system persona) the "Image to Text" quick chat uses. */
        val imageToTextCharacter: String,
        /** Sampling preset the "Image to Text" quick chat uses. */
        val imageToTextPreset: String,
        /** Default name for new conversations' `{{user}}`; the last name the user set. */
        val userName: String,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            ip = prefs[Keys.IP] ?: "192.168.1.42",
            port = prefs[Keys.PORT] ?: "8080",
            comfyPort = prefs[Keys.COMFY_PORT] ?: "8188",
            currentModel = prefs[Keys.CURRENT_MODEL] ?: Catalog.fallbackModels.first(),
            currentPreset = prefs[Keys.CURRENT_PRESET] ?: Catalog.presets.first().name,
            characterGenModel = prefs[Keys.CHARACTER_GEN_MODEL] ?: "",
            imageToTextModel = prefs[Keys.I2T_MODEL] ?: DEFAULT_I2T_MODEL,
            imageToTextCharacter = prefs[Keys.I2T_CHARACTER] ?: "Assistant",
            imageToTextPreset = prefs[Keys.I2T_PRESET] ?: "Default",
            userName = prefs[Keys.USER_NAME]?.takeIf { it.isNotBlank() } ?: "user",
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun saveServer(ip: String, llamaPort: String, comfyPort: String) {
        context.dataStore.edit {
            it[Keys.IP] = ip
            it[Keys.PORT] = llamaPort
            it[Keys.COMFY_PORT] = comfyPort
        }
    }

    suspend fun setCurrentModel(model: String) {
        context.dataStore.edit { it[Keys.CURRENT_MODEL] = model }
    }

    /** Remember the most recently selected preset as the default for new conversations. */
    suspend fun setCurrentPreset(preset: String) {
        context.dataStore.edit { it[Keys.CURRENT_PRESET] = preset }
    }

    /** Pick the model the "Generate a character" flow uses; blank follows [Settings.currentModel]. */
    suspend fun setCharacterGenModel(model: String) {
        context.dataStore.edit { it[Keys.CHARACTER_GEN_MODEL] = model }
    }

    suspend fun setImageToTextModel(model: String) {
        context.dataStore.edit { it[Keys.I2T_MODEL] = model }
    }

    suspend fun setImageToTextCharacter(character: String) {
        context.dataStore.edit { it[Keys.I2T_CHARACTER] = character }
    }

    suspend fun setImageToTextPreset(preset: String) {
        context.dataStore.edit { it[Keys.I2T_PRESET] = preset }
    }

    /** Remember the most recently used name as the default for future conversations. */
    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun wasSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() {
        context.dataStore.edit { it[Keys.SEEDED] = true }
    }

    companion object {
        /** Vision model the "Image to Text" quick chat defaults to. */
        const val DEFAULT_I2T_MODEL = "Qwen3.6-VL-27B-NR"
    }
}
