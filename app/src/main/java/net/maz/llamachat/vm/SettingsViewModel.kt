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
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.ParsedWorkflowZip
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
)

class SettingsViewModel(private val app: LlamaChatApp) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    /** Live probe results, shown as immediate feedback after saving. */
    val health: StateFlow<ServerHealth> = app.healthMonitor.state

    /** Count of installed base enum lists (the "Media generation" section). */
    val baseEnumCount: StateFlow<Int> = app.workflowStore.baseEnumCount

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
                )
            }
        }
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
