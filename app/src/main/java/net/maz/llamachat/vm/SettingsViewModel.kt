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
import net.maz.llamachat.data.comfy.InstalledWorkflow
import net.maz.llamachat.data.comfy.ParsedWorkflowZip
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
)

class SettingsViewModel(private val app: LlamaChatApp) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    /** Live probe results, shown as immediate feedback after saving. */
    val health: StateFlow<ServerHealth> = app.healthMonitor.state

    /** Installed ComfyUI workflow packages (the "Media generation" section). */
    val workflows: StateFlow<List<InstalledWorkflow>> = app.workflowStore.workflows
    val baseEnumCount: StateFlow<Int> = app.workflowStore.baseEnumCount

    init {
        viewModelScope.launch {
            val s = app.settingsRepository.current()
            _state.update { it.copy(ip = s.ip, llamaPort = s.port, comfyPort = s.comfyPort) }
        }
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

    fun deleteWorkflow(workflow: InstalledWorkflow) {
        viewModelScope.launch {
            app.workflowStore.delete(workflow.id)
            setStatus("Removed '${workflow.name}'", isError = false)
        }
    }

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
