package net.maz.llamachat.data.gen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live state of the single in-flight summarization, shared between the foreground
 * [SummarizationService] (the writer) and ChatViewModel (the reader). Unlike a reply,
 * a summary is only applied to Room on success, so this exists mainly to drive the
 * "Summarizing…" UI and to signal completion (which switches to the details screen).
 * Null when nothing is summarizing.
 */
enum class SummaryPhase { RUNNING, DONE, FAILED }

data class SummarizationState(
    val convId: Long,
    /** Accumulated summary text so far, for a live preview while it streams. */
    val text: String,
    val phase: SummaryPhase,
)

/** App-scoped coordinator for the at-most-one active summarization. */
class SummarizationController {

    private val _state = MutableStateFlow<SummarizationState?>(null)
    val state = _state.asStateFlow()

    fun begin(convId: Long) {
        _state.value = SummarizationState(convId, "", SummaryPhase.RUNNING)
    }

    fun update(convId: Long, text: String) {
        _state.value?.let { if (it.convId == convId) _state.value = it.copy(text = text) }
    }

    /** Mark this run finished (DONE or FAILED); the reader clears it once consumed. */
    fun finish(convId: Long, phase: SummaryPhase) {
        _state.value?.let { if (it.convId == convId) _state.value = it.copy(phase = phase) }
    }

    /** Clear the state once the UI has reacted to a finished run. */
    fun clear(convId: Long) {
        _state.value?.let { if (it.convId == convId) _state.value = null }
    }

    fun isActive(convId: Long): Boolean =
        _state.value?.let { it.convId == convId && it.phase == SummaryPhase.RUNNING } == true
}
