package net.maz.llamachat.data.gen

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live progress of the single in-flight generation, shared between the
 * foreground [GenerationService] (the writer) and ChatViewModel (the reader).
 *
 * The service persists throttled checkpoints to Room so a partial survives a
 * process kill, but Room writes are too coarse for smooth per-token UI; this
 * holds the full accumulated text in memory so the chat repaints on every token
 * while the durable copy lags slightly behind. Null when nothing is generating.
 */
data class GenerationState(
    /** Monotonic id of this generation, so a finishing run can't clear a newer one. */
    val token: Long,
    val convId: Long,
    val targetId: Long,
    /** Full accumulated text of the target message: prefill/base + streamed deltas. */
    val text: String,
)

/** App-scoped coordinator for the at-most-one active generation. */
class GenerationController {

    private val _state = MutableStateFlow<GenerationState?>(null)
    val state = _state.asStateFlow()

    private val tokens = AtomicLong(0)

    /** Open a new generation, replacing any previous state. Returns its token. */
    fun begin(convId: Long, targetId: Long, base: String): Long {
        val token = tokens.incrementAndGet()
        _state.value = GenerationState(token, convId, targetId, base)
        return token
    }

    /** Publish the latest accumulated text; ignored once a newer run has begun. */
    fun update(token: Long, text: String) {
        _state.update { if (it?.token == token) it.copy(text = text) else it }
    }

    /** Clear this run's state; ignored if a newer run has already replaced it. */
    fun end(token: Long) {
        _state.update { if (it?.token == token) null else it }
    }

    /** True while a generation for [convId] is in flight (drives the UI's streaming flag). */
    fun isActive(convId: Long): Boolean = _state.value?.convId == convId
}
