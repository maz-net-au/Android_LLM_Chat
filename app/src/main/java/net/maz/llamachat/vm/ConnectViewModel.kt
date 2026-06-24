package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.SettingsRepository

data class ConnectUiState(
    val ip: String = "",
    val port: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
)

class ConnectViewModel(
    private val app: LlamaChatApp,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.current()
            _state.update { it.copy(ip = s.ip, port = s.port) }
        }
    }

    fun setIp(v: String) = _state.update { it.copy(ip = v, error = null) }
    fun setPort(v: String) = _state.update { it.copy(port = v, error = null) }

    fun connect() {
        val st = _state.value
        if (st.connecting) return
        if (st.ip.isBlank()) {
            _state.update { it.copy(error = "Enter a server IP address.") }
            return
        }
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val error = app.connectTo(st.ip, st.port)
            _state.update { it.copy(connecting = false, connected = error == null, error = error) }
        }
    }

    /** Called by the screen once it has navigated, to reset the one-shot flag. */
    fun consumedNavigation() = _state.update { it.copy(connected = false) }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer {
                ConnectViewModel(app, app.settingsRepository)
            }
        }
    }
}
