package net.maz.llamachat.vm

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
import net.maz.llamachat.data.net.ServerHealth

data class SettingsUiState(
    val ip: String = "",
    val llamaPort: String = "",
    val comfyPort: String = "",
    /** One-shot "Saved" confirmation; cleared by the next edit. */
    val saved: Boolean = false,
)

class SettingsViewModel(private val app: LlamaChatApp) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    /** Live probe results, shown as immediate feedback after saving. */
    val health: StateFlow<ServerHealth> = app.healthMonitor.state

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

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer { SettingsViewModel(app) }
        }
    }
}
