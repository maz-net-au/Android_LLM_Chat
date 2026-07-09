package net.maz.llamachat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        val SUMMARY_MODEL = stringPreferencesKey("summary_model")
        val SCENE_MODEL = stringPreferencesKey("scene_image_model")
        val SCENE_WORKFLOW_ID = longPreferencesKey("scene_workflow_id")
        val SCENE_PROMPT_NODE = stringPreferencesKey("scene_prompt_node_title")
        val SCENE_PROMPT_INPUT = stringPreferencesKey("scene_prompt_input")
        val SCENE_SYSTEM_PROMPT = stringPreferencesKey("scene_system_prompt")
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
        /** Model the "Summarize & continue" flow uses. Blank = follow the conversation's model. */
        val summaryModel: String,
        /** Model that writes scene-image descriptions. Blank = follow the conversation's model. */
        val sceneImageModel: String,
        /** Installed ComfyUI t2i workflow used for scene images; -1 = none configured. */
        val sceneWorkflowId: Long,
        /** Node `_meta.title` of the scene workflow's prompt field. */
        val scenePromptNodeTitle: String,
        /** `inputs` key on that node that receives the generated prompt. */
        val scenePromptInput: String,
        /** System instruction that steers the scene-image description. Blank = the built-in
         *  default ([net.maz.llamachat.data.gen.SceneImageConfig.SYSTEM_PROMPT]). */
        val sceneSystemPrompt: String,
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
            summaryModel = prefs[Keys.SUMMARY_MODEL] ?: "",
            sceneImageModel = prefs[Keys.SCENE_MODEL] ?: "",
            sceneWorkflowId = prefs[Keys.SCENE_WORKFLOW_ID] ?: -1L,
            scenePromptNodeTitle = prefs[Keys.SCENE_PROMPT_NODE] ?: "",
            scenePromptInput = prefs[Keys.SCENE_PROMPT_INPUT] ?: "",
            sceneSystemPrompt = prefs[Keys.SCENE_SYSTEM_PROMPT] ?: "",
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

    /** Pick the model the "Summarize & continue" flow uses. */
    suspend fun setSummaryModel(model: String) {
        context.dataStore.edit { it[Keys.SUMMARY_MODEL] = model }
    }

    /** Pick the model that writes scene-image descriptions. */
    suspend fun setSceneImageModel(model: String) {
        context.dataStore.edit { it[Keys.SCENE_MODEL] = model }
    }

    /** Select the scene-image workflow; changing it clears the stale prompt-field mapping. */
    suspend fun setSceneWorkflow(id: Long) {
        context.dataStore.edit {
            it[Keys.SCENE_WORKFLOW_ID] = id
            it[Keys.SCENE_PROMPT_NODE] = ""
            it[Keys.SCENE_PROMPT_INPUT] = ""
        }
    }

    /** Choose which of the workflow's string fields receives the generated prompt. */
    suspend fun setScenePromptField(nodeTitle: String, input: String) {
        context.dataStore.edit {
            it[Keys.SCENE_PROMPT_NODE] = nodeTitle
            it[Keys.SCENE_PROMPT_INPUT] = input
        }
    }

    /** Override the scene-image description system prompt; blank restores the default. */
    suspend fun setSceneSystemPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.SCENE_SYSTEM_PROMPT] = prompt }
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
