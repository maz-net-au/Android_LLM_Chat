package net.maz.llamachat.data.net

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.Catalog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ProbeStatus { UNKNOWN, UP, DOWN }

data class ServerHealth(
    val llama: ProbeStatus = ProbeStatus.UNKNOWN,
    val comfy: ProbeStatus = ProbeStatus.UNKNOWN,
    /** True while a probe round is in flight. */
    val checking: Boolean = false,
    /** Models reported by llama-server's /v1/models on the last successful probe. */
    val models: List<String> = emptyList(),
) {
    enum class Overall { ALL_UP, DEGRADED, DOWN, UNKNOWN }

    val overall: Overall
        get() = when {
            llama == ProbeStatus.UP && comfy == ProbeStatus.UP -> Overall.ALL_UP
            llama == ProbeStatus.UP || comfy == ProbeStatus.UP -> Overall.DEGRADED
            llama == ProbeStatus.DOWN && comfy == ProbeStatus.DOWN -> Overall.DOWN
            else -> Overall.UNKNOWN
        }
}

/**
 * App-scoped poller for the two backing servers: llama-server (GET /health) and
 * ComfyUI (GET /system_stats). Polls while the app is foregrounded and re-probes
 * immediately on return to foreground. Also owns fetching the model list, which
 * used to happen in the (removed) connect flow.
 */
class ServerHealthMonitor(
    private val settings: SettingsRepository,
    private val llamaClient: LlamaClient,
    private val scope: CoroutineScope,
) {
    // Probes need short timeouts; LlamaClient's client disables the read timeout
    // to keep SSE streams open, which would make a dead server hang the probe.
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ServerHealth())
    val state: StateFlow<ServerHealth> = _state

    private val probeLock = Mutex()

    fun start() {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    probe()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    /** Re-probe now (settings saved, indicator tapped) without waiting for the next cycle. */
    fun refreshNow() {
        scope.launch { probe() }
    }

    private suspend fun probe() {
        if (!probeLock.tryLock()) return
        try {
            _state.value = _state.value.copy(checking = true)
            val s = settings.current()
            coroutineScope {
                val llamaUp = async { isUp("http://${s.ip}:${s.port}/health") }
                val comfyUp = async { isUp("http://${s.ip}:${s.comfyPort}/system_stats") }
                val llama = if (llamaUp.await()) ProbeStatus.UP else ProbeStatus.DOWN
                val comfy = if (comfyUp.await()) ProbeStatus.UP else ProbeStatus.DOWN
                val models = if (llama == ProbeStatus.UP) fetchModels(s) else _state.value.models
                _state.value = ServerHealth(llama, comfy, checking = false, models = models)
            }
        } finally {
            probeLock.unlock()
        }
    }

    private suspend fun fetchModels(s: SettingsRepository.Settings): List<String> {
        val list = llamaClient.listModels(s.ip, s.port).getOrNull()?.ifEmpty { null }
            ?: Catalog.fallbackModels
        // Pin a current model the server actually offers.
        if (list != _state.value.models && settings.current().currentModel !in list) {
            settings.setCurrentModel(list.first())
        }
        return list
    }

    private suspend fun isUp(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }
}
