package net.maz.llamachat.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.FieldType
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.InstalledWorkflow
import net.maz.llamachat.data.comfy.ParsedWorkflowZip
import net.maz.llamachat.data.comfy.WorkflowField
import net.maz.llamachat.data.gen.SceneImageConfig
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.net.ServerHealth

data class SettingsUiState(
    val ip: String = "",
    val llamaPort: String = "",
    val comfyPort: String = "",
    /** One-shot "Saved" confirmation; cleared by the next edit. */
    val saved: Boolean = false,
    /** A validated workflow zip awaiting the name/flow-type install dialog. */
    val pendingImport: ParsedWorkflowZip? = null,
    /** Editable workflow name shown in the install dialog. */
    val importName: String = "",
    /** One-shot outcome of the last workflow/enums action. */
    val workflowStatus: String? = null,
    val workflowStatusIsError: Boolean = false,
    /** True while an unload request to that server is in flight. */
    val unloadingLlama: Boolean = false,
    val unloadingComfy: Boolean = false,
    /** One-shot result of the last unload action. */
    val unloadStatus: String? = null,
    val unloadStatusIsError: Boolean = false,
    /** Model the "Generate a character" flow uses; the effective value (a blank
     *  stored setting is shown resolved to the current chat model). */
    val characterGenModel: String = "",
    /** Model / character / preset the launcher's "Image to Text" quick chat uses. */
    val imageToTextModel: String = "",
    val imageToTextCharacter: String = "",
    val imageToTextPreset: String = "",
    /** Model the "Summarize & continue" flow uses (effective value, blank shown resolved). */
    val summaryModel: String = "",
    /** Model that writes scene-image descriptions (effective value). */
    val sceneImageModel: String = "",
    /** Selected scene-image t2i workflow; -1 = none. */
    val sceneWorkflowId: Long = -1L,
    /** Display name of that workflow, or blank if none/uninstalled. */
    val sceneWorkflowName: String = "",
    /** The string-typed fields of the selected workflow, offered as prompt targets. */
    val scenePromptFields: List<WorkflowField> = emptyList(),
    /** The currently-chosen prompt field, addressed by node title + input. */
    val scenePromptNodeTitle: String = "",
    val scenePromptInput: String = "",
    /** System instruction steering the scene-image description (editable; the built-in
     *  default is shown when the user hasn't overridden it). */
    val sceneSystemPrompt: String = "",
)

class SettingsViewModel(private val app: LlamaChatApp) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    /** Live probe results, shown as immediate feedback after saving. */
    val health: StateFlow<ServerHealth> = app.healthMonitor.state

    /** Count of installed base enum lists (the "Media generation" section). */
    val baseEnumCount: StateFlow<Int> = app.workflowStore.baseEnumCount

    /** Installed workflows; collected by the settings screen so the scene-image
     *  pickers recompose when a workflow is imported or deleted. */
    val workflows: StateFlow<List<InstalledWorkflow>> = app.workflowStore.workflows

    /** Character names offered in the picker (the live, user-editable list). */
    fun characterOptions(): List<String> = Catalog.characters.map { it.name }

    /** Preset names offered in the picker. */
    fun presetOptions(): List<String> = Catalog.presets.map { it.name }

    /** Models the server reported, falling back to the built-in list when offline.
     *  [current] is unioned in so the active selection is always pickable even if
     *  the server isn't reporting it right now. */
    fun modelOptions(current: String): List<String> {
        val reported = health.value.models.ifEmpty { Catalog.fallbackModels }
        return if (current.isNotBlank() && current !in reported) listOf(current) + reported else reported
    }

    /** Installed t2i workflows offered in the scene-image picker (by display name). */
    fun t2iWorkflowOptions(): List<String> =
        app.workflowStore.workflows.value
            .filter { it.flowType == FlowType.TEXT_TO_IMAGE.key }
            .map { it.name }

    /** Display labels of the selected workflow's string fields (prompt-target picker). */
    fun scenePromptFieldOptions(): List<String> = _state.value.scenePromptFields.map { it.displayLabel }

    /** Label of the currently-chosen prompt field, or blank if none. */
    fun scenePromptFieldLabel(): String {
        val st = _state.value
        return st.scenePromptFields
            .firstOrNull { it.nodeTitle == st.scenePromptNodeTitle && it.input == st.scenePromptInput }
            ?.displayLabel.orEmpty()
    }

    init {
        viewModelScope.launch {
            val s = app.settingsRepository.current()
            _state.update {
                it.copy(
                    ip = s.ip,
                    llamaPort = s.port,
                    comfyPort = s.comfyPort,
                    characterGenModel = s.characterGenModel.ifEmpty { s.currentModel },
                    imageToTextModel = s.imageToTextModel,
                    imageToTextCharacter = s.imageToTextCharacter,
                    imageToTextPreset = s.imageToTextPreset,
                    summaryModel = s.summaryModel.ifEmpty { s.currentModel },
                    sceneImageModel = s.sceneImageModel.ifEmpty { s.currentModel },
                    sceneWorkflowId = s.sceneWorkflowId,
                    sceneWorkflowName = workflowName(s.sceneWorkflowId),
                    scenePromptNodeTitle = s.scenePromptNodeTitle,
                    scenePromptInput = s.scenePromptInput,
                    sceneSystemPrompt = s.sceneSystemPrompt.ifEmpty { SceneImageConfig.SYSTEM_PROMPT },
                )
            }
            if (s.sceneWorkflowId >= 0) loadSceneFields(s.sceneWorkflowId)
        }
    }

    private fun workflowName(id: Long): String =
        app.workflowStore.workflows.value.firstOrNull { it.id == id }?.name.orEmpty()

    /** Load the workflow's string fields into state (empty on failure / unset). */
    private suspend fun loadSceneFields(id: Long) {
        if (id < 0) {
            _state.update { it.copy(scenePromptFields = emptyList()) }
            return
        }
        val fields = app.workflowStore.loadConfig(id).getOrNull()
            ?.fields.orEmpty()
            .filter { it.type == FieldType.STRING.wire }
        _state.update { it.copy(scenePromptFields = fields) }
    }

    /** Persist the summary model. */
    fun setSummaryModel(model: String) {
        _state.update { it.copy(summaryModel = model) }
        viewModelScope.launch { app.settingsRepository.setSummaryModel(model) }
    }

    /** Persist the scene-description model. */
    fun setSceneImageModel(model: String) {
        _state.update { it.copy(sceneImageModel = model) }
        viewModelScope.launch { app.settingsRepository.setSceneImageModel(model) }
    }

    /** Select the scene-image workflow by display name; resets the prompt field and
     *  auto-selects when the new workflow has exactly one string field. */
    fun setSceneWorkflow(name: String) {
        val wf = app.workflowStore.workflows.value
            .firstOrNull { it.flowType == FlowType.TEXT_TO_IMAGE.key && it.name == name } ?: return
        _state.update {
            it.copy(
                sceneWorkflowId = wf.id,
                sceneWorkflowName = wf.name,
                scenePromptNodeTitle = "",
                scenePromptInput = "",
            )
        }
        viewModelScope.launch {
            app.settingsRepository.setSceneWorkflow(wf.id)
            loadSceneFields(wf.id)
            val only = _state.value.scenePromptFields.singleOrNull()
            if (only != null) setScenePromptField(only.displayLabel)
        }
    }

    /** Choose the prompt-target field by its display label. */
    fun setScenePromptField(label: String) {
        val field = _state.value.scenePromptFields.firstOrNull { it.displayLabel == label } ?: return
        _state.update { it.copy(scenePromptNodeTitle = field.nodeTitle, scenePromptInput = field.input) }
        viewModelScope.launch { app.settingsRepository.setScenePromptField(field.nodeTitle, field.input) }
    }

    /** Edit the scene-image system prompt. Persisting the built-in default verbatim stores
     *  blank, so future default tweaks still flow through to users who never customized it. */
    fun setSceneSystemPrompt(prompt: String) {
        _state.update { it.copy(sceneSystemPrompt = prompt) }
        val stored = if (prompt == SceneImageConfig.SYSTEM_PROMPT) "" else prompt
        viewModelScope.launch { app.settingsRepository.setSceneSystemPrompt(stored) }
    }

    /** Restore the built-in scene-image system prompt. */
    fun resetSceneSystemPrompt() {
        _state.update { it.copy(sceneSystemPrompt = SceneImageConfig.SYSTEM_PROMPT) }
        viewModelScope.launch { app.settingsRepository.setSceneSystemPrompt("") }
    }

    /** Persist the model the character generator should use, immediately. */
    fun setCharacterGenModel(model: String) {
        _state.update { it.copy(characterGenModel = model) }
        viewModelScope.launch { app.settingsRepository.setCharacterGenModel(model) }
    }

    fun setImageToTextModel(model: String) {
        _state.update { it.copy(imageToTextModel = model) }
        viewModelScope.launch { app.settingsRepository.setImageToTextModel(model) }
    }

    fun setImageToTextCharacter(character: String) {
        _state.update { it.copy(imageToTextCharacter = character) }
        viewModelScope.launch { app.settingsRepository.setImageToTextCharacter(character) }
    }

    fun setImageToTextPreset(preset: String) {
        _state.update { it.copy(imageToTextPreset = preset) }
        viewModelScope.launch { app.settingsRepository.setImageToTextPreset(preset) }
    }

    fun setIp(v: String) = _state.update { it.copy(ip = v, saved = false) }
    fun setLlamaPort(v: String) = _state.update { it.copy(llamaPort = v, saved = false) }
    fun setComfyPort(v: String) = _state.update { it.copy(comfyPort = v, saved = false) }

    fun save() {
        val st = _state.value
        if (st.ip.isBlank()) return
        viewModelScope.launch {
            app.settingsRepository.saveServer(
                st.ip.trim(),
                st.llamaPort.trim().ifEmpty { "8080" },
                st.comfyPort.trim().ifEmpty { "8188" },
            )
            app.healthMonitor.refreshNow()
            _state.update { it.copy(saved = true) }
        }
    }

    // ---- unload models / free VRAM ----

    /** Unload every model llama-server has loaded, one after another. */
    fun unloadLlama() {
        if (_state.value.unloadingLlama) return
        _state.update { it.copy(unloadingLlama = true, unloadStatus = null) }
        viewModelScope.launch {
            val s = app.settingsRepository.current()
            app.llamaClient.unloadAllModels(s.ip, s.port)
                .onSuccess { n ->
                    setUnload(
                        if (n == 0) "llama-server: no models were loaded"
                        else "llama-server: unloaded $n model${if (n == 1) "" else "s"}",
                        isError = false,
                    )
                }
                .onFailure { setUnload("llama-server: ${it.message ?: "unload failed"}", isError = true) }
            _state.update { it.copy(unloadingLlama = false) }
            app.healthMonitor.refreshNow()
        }
    }

    /** Ask ComfyUI to unload its models and free VRAM. */
    fun unloadComfy() {
        if (_state.value.unloadingComfy) return
        _state.update { it.copy(unloadingComfy = true, unloadStatus = null) }
        viewModelScope.launch {
            val s = app.settingsRepository.current()
            app.comfyClient.freeMemory(s.ip, s.comfyPort)
                .onSuccess { setUnload("ComfyUI: models unloaded, memory freed", isError = false) }
                .onFailure { setUnload("ComfyUI: ${it.message ?: "unload failed"}", isError = true) }
            _state.update { it.copy(unloadingComfy = false) }
        }
    }

    private fun setUnload(message: String, isError: Boolean) =
        _state.update { it.copy(unloadStatus = message, unloadStatusIsError = isError) }

    // ---- ComfyUI workflow management ----

    /** Parse + validate the picked zip; success opens the install dialog. */
    fun importWorkflow(uri: Uri) {
        viewModelScope.launch {
            app.workflowStore.parse(uri)
                .onSuccess { parsed ->
                    _state.update {
                        it.copy(pendingImport = parsed, importName = parsed.config.name, workflowStatus = null)
                    }
                }
                .onFailure { setStatus(it.message ?: "Could not read the zip", isError = true) }
        }
    }

    fun setImportName(v: String) = _state.update { it.copy(importName = v) }

    fun confirmImport(flowType: FlowType) {
        val parsed = _state.value.pendingImport ?: return
        val name = _state.value.importName
        viewModelScope.launch {
            app.workflowStore.install(parsed, flowType, name)
                .onSuccess { setStatus("Installed '${it.name}' (${flowType.label})", isError = false) }
                .onFailure { setStatus(it.message ?: "Install failed", isError = true) }
            _state.update { it.copy(pendingImport = null, importName = "") }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null, importName = "") }

    fun importBaseEnums(uri: Uri) {
        viewModelScope.launch {
            app.workflowStore.installBaseEnums(uri)
                .onSuccess { n -> setStatus("Installed $n enum list${if (n == 1) "" else "s"}", isError = false) }
                .onFailure { setStatus(it.message ?: "Could not read the file", isError = true) }
        }
    }

    private fun setStatus(message: String, isError: Boolean) =
        _state.update { it.copy(workflowStatus = message, workflowStatusIsError = isError) }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer { SettingsViewModel(app) }
        }
    }
}
