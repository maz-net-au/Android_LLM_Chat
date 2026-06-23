package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.data.net.ApiMessage
import net.maz.llamachat.data.net.ChatRequest
import net.maz.llamachat.data.net.LlamaClient

data class ChatUiState(
    val loaded: Boolean = false,
    val conversation: Conversation? = null,
    val input: String = "",
    val streaming: Boolean = false,
    val selectedMsgId: Long? = null,
    val editingMsgId: Long? = null,
    val editText: String = "",
    val chatMenuOpen: Boolean = false,
    /** True once the conversation has been deleted — the screen pops to Home. */
    val closed: Boolean = false,
)

class ChatViewModel(
    val convId: Long,
    private val client: LlamaClient,
    private val settings: SettingsRepository,
    private val repo: ConversationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUiState())
    val ui = _ui.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            val conv = repo.get(convId)
            _ui.update { it.copy(loaded = true, conversation = conv) }
        }
    }

    // ---- state helpers -----------------------------------------------------

    private fun updateConv(block: (Conversation) -> Conversation) {
        _ui.update { st ->
            val c = st.conversation ?: return@update st
            st.copy(conversation = block(c).copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private fun setText(messageId: Long, text: String) {
        updateConv { c ->
            c.copy(messages = c.messages.map { if (it.id == messageId) it.withText(text) else it })
        }
    }

    private fun persistAsync() {
        val c = _ui.value.conversation ?: return
        viewModelScope.launch { repo.save(c) }
    }

    // ---- input -------------------------------------------------------------

    fun setInput(v: String) = _ui.update { it.copy(input = v) }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.streaming) return
        val userId = IdGen.next()
        val assistantId = IdGen.next()
        updateConv { c ->
            val title = if (c.title == "New chat") deriveTitle(text) else c.title
            c.copy(
                title = title,
                messages = c.messages +
                    ChatMessage.user(userId, "${c.userName}: $text") +
                    // Prefill the reply with "Character: " so the model is forced
                    // to speak as the character; generation continues from it.
                    ChatMessage.assistant(assistantId, "${c.characterName}: "),
            )
        }
        _ui.update { it.copy(input = "", selectedMsgId = null) }
        persistAsync()
        startGeneration(assistantId, includePartial = true)
    }

    private fun deriveTitle(text: String): String =
        if (text.length > 30) text.take(30) + "…" else text

    // ---- generation --------------------------------------------------------

    private fun startGeneration(targetId: Long, includePartial: Boolean) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val s = settings.current()
            val conv = _ui.value.conversation ?: return@launch
            val idx = conv.messages.indexOfFirst { it.id == targetId }
            if (idx < 0) return@launch
            val base = if (includePartial) conv.messages[idx].text else ""
            val preset = conv.preset
            val request = ChatRequest(
                model = conv.model.ifEmpty { s.currentModel },
                messages = buildApiMessages(conv, idx, includePartial, conv.userName),
                stream = true,
                // Stop before the model speaks for the user or restarts its own turn.
                stop = listOf("${conv.userName}:", "${conv.characterName}:"),
                temperature = preset.temperature,
                topP = preset.topP,
                topK = preset.topK,
                minP = preset.minP,
                typicalP = preset.typicalP,
                repeatPenalty = preset.repeatPenalty,
                repeatLastN = preset.repeatLastN,
                presencePenalty = preset.presencePenalty,
                frequencyPenalty = preset.frequencyPenalty,
                mirostat = preset.mirostat,
                mirostatTau = preset.mirostatTau,
                mirostatEta = preset.mirostatEta,
            )
            _ui.update { it.copy(streaming = true) }
            val sb = StringBuilder(base)
            try {
                client.streamChat(s.ip, s.port, request).collect { delta ->
                    sb.append(delta)
                    setText(targetId, sb.toString())
                }
                _ui.value.conversation?.let { repo.save(it) }
            } catch (c: CancellationException) {
                throw c // Stop / navigation away: keep the partial text (persisted by stop()).
            } catch (e: Exception) {
                if (sb.length <= base.length) { // nothing generated beyond the prefill
                    setText(targetId, "⚠️ Couldn't generate a reply: ${e.message ?: "unknown error"}")
                }
                _ui.value.conversation?.let { repo.save(it) }
            } finally {
                _ui.update { it.copy(streaming = false) }
            }
        }
    }

    /**
     * Build the OpenAI-style message history sent to the server. Messages before
     * [assistantIndex] form the context; when [includePartial] is set (the
     * "Continue" action) the target assistant's current text is appended so the
     * server keeps generating from it.
     */
    private fun buildApiMessages(
        conv: Conversation,
        assistantIndex: Int,
        includePartial: Boolean,
        userName: String,
    ): List<ApiMessage> {
        val out = ArrayList<ApiMessage>()
        conv.character.resolvedContext(userName).takeIf { it.isNotBlank() }
            ?.let { out += ApiMessage("system", it) }
        conv.messages.forEachIndexed { i, m ->
            if (i < assistantIndex) {
                out += ApiMessage(if (m.role == Role.USER) "user" else "assistant", m.text)
            }
        }
        if (includePartial) {
            val partial = conv.messages.getOrNull(assistantIndex)?.text.orEmpty()
            if (partial.isNotBlank()) out += ApiMessage("assistant", partial)
        }
        return out
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _ui.update { it.copy(streaming = false) }
        persistAsync()
    }

    /** Add a fresh variant to the last assistant message and regenerate it. */
    fun regenerate() {
        if (_ui.value.streaming) return
        val conv = _ui.value.conversation ?: return
        val last = conv.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        updateConv { c ->
            c.copy(messages = c.messages.map { if (it.id == last.id) it.addVariant("${c.characterName}: ") else it })
        }
        _ui.update { it.copy(selectedMsgId = null, chatMenuOpen = false) }
        startGeneration(last.id, includePartial = true)
    }

    fun continueMessage(id: Long) {
        if (_ui.value.streaming) return
        _ui.update { it.copy(selectedMsgId = null) }
        startGeneration(id, includePartial = true)
    }

    // ---- variants ----------------------------------------------------------

    fun prevVariant(id: Long) = shiftVariant(id, -1)
    fun nextVariant(id: Long) = shiftVariant(id, +1)

    private fun shiftVariant(id: Long, delta: Int) {
        updateConv { c ->
            c.copy(
                messages = c.messages.map { m ->
                    if (m.id == id && m.variants.isNotEmpty()) {
                        m.copy(activeVariant = (m.activeVariant + delta).coerceIn(0, m.variants.size - 1))
                    } else {
                        m
                    }
                },
            )
        }
        _ui.update { it.copy(selectedMsgId = null) }
        persistAsync()
    }

    // ---- selection & editing ----------------------------------------------

    fun toggleSelected(id: Long) = _ui.update {
        it.copy(selectedMsgId = if (it.selectedMsgId == id) null else id)
    }

    fun startEdit(id: Long) {
        val m = _ui.value.conversation?.messages?.firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(editingMsgId = id, editText = m.text, selectedMsgId = null) }
    }

    fun setEditText(v: String) = _ui.update { it.copy(editText = v) }

    fun saveEdit() {
        val id = _ui.value.editingMsgId ?: return
        val text = _ui.value.editText
        updateConv { c -> c.copy(messages = c.messages.map { if (it.id == id) it.withText(text) else it }) }
        _ui.update { it.copy(editingMsgId = null, editText = "") }
        persistAsync()
    }

    fun cancelEdit() = _ui.update { it.copy(editingMsgId = null, editText = "") }

    // ---- deletion ----------------------------------------------------------

    /** Delete the last assistant message and the user turn that prompted it,
     *  restoring that prompt into the input (mirrors the prototype). */
    fun deleteLastAssistant() {
        val conv = _ui.value.conversation ?: return
        val ai = conv.messages.indexOfLast { it.role == Role.ASSISTANT }
        if (ai < 0) return
        var ui = -1
        for (j in ai - 1 downTo 0) {
            if (conv.messages[j].role == Role.USER) { ui = j; break }
        }
        val assistantId = conv.messages[ai].id
        val userId = if (ui >= 0) conv.messages[ui].id else null
        val restore = if (ui >= 0) conv.messages[ui].text else _ui.value.input
        updateConv { c ->
            c.copy(messages = c.messages.filter { it.id != assistantId && it.id != userId })
        }
        _ui.update { it.copy(input = restore, selectedMsgId = null) }
        persistAsync()
    }

    fun clearMessages() {
        updateConv { it.copy(messages = emptyList()) }
        _ui.update { it.copy(chatMenuOpen = false, selectedMsgId = null) }
        persistAsync()
    }

    fun deleteConversation() {
        streamJob?.cancel()
        viewModelScope.launch {
            repo.delete(convId)
            _ui.update { it.copy(chatMenuOpen = false, closed = true) }
        }
    }

    // ---- chat menu ---------------------------------------------------------

    fun toggleChatMenu() = _ui.update { it.copy(chatMenuOpen = !it.chatMenuOpen) }
    fun closeChatMenu() = _ui.update { it.copy(chatMenuOpen = false) }

    override fun onCleared() {
        streamJob?.cancel()
    }

    companion object {
        fun factory(app: LlamaChatApp, convId: Long) = viewModelFactory {
            initializer {
                ChatViewModel(
                    convId, app.llamaClient, app.settingsRepository, app.conversationRepository,
                )
            }
        }
    }
}
