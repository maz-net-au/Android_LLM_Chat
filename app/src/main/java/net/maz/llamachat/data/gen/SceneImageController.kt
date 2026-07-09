package net.maz.llamachat.data.gen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-scoped set of scene-image placeholders whose LLM *describe* step is running
 * right now, shared between the foreground [SceneImageService] (writer) and
 * ChatViewModel (reader). The message's own persisted `sceneImage.status` is the
 * source of truth for the rest of the lifecycle; this only distinguishes a live
 * describe from a stale one left behind by a process death (a message stuck at
 * "describing" that isn't in this set can be shown as failed with Retry).
 *
 * Keyed by message id so several scene images can generate at once — unlike
 * summarization, scene-image requests are not single-flight.
 */
class SceneImageController {

    private val _describing = MutableStateFlow<Set<Long>>(emptySet())
    val describing = _describing.asStateFlow()

    fun begin(messageId: Long) = _describing.update { it + messageId }

    fun end(messageId: Long) = _describing.update { it - messageId }

    fun isDescribing(messageId: Long): Boolean = messageId in _describing.value
}
