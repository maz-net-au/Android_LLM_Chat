package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.backup.BackupCodec
import net.maz.llamachat.data.gen.ChatRequestBuilder
import net.maz.llamachat.data.gen.GenerationController
import net.maz.llamachat.data.gen.GenerationService
import net.maz.llamachat.data.gen.SummarizationConfig
import net.maz.llamachat.data.gen.SummarizationController
import net.maz.llamachat.data.gen.SummarizationService
import net.maz.llamachat.data.gen.SummaryPhase
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.Role

data class ChatUiState(
    val loaded: Boolean = false,
    val conversation: Conversation? = null,
    val input: String = "",
    val streaming: Boolean = false,
    /** True while an "Impersonate" generation is streaming the user's turn into [input]. */
    val impersonating: Boolean = false,
    val selectedMsgId: Long? = null,
    val editingMsgId: Long? = null,
    val editText: String = "",
    /** Speaker prefix stripped from the message being edited, re-applied on save. */
    val editPrefix: String = "",
    val chatMenuOpen: Boolean = false,
    /** True once the conversation has been deleted — the screen pops to Home. */
    val closed: Boolean = false,
    /** True while a summarization is streaming for this chat. */
    val summarizing: Boolean = false,
    /** Live summary text as it streams, for an in-progress preview. */
    val summaryProgress: String = "",
    /** Set to this chat's id once a summarization finishes, so the screen can switch
     *  to the details view. One-shot: cleared via [ChatViewModel.consumeSummarizeDone]. */
    val summarizeDoneId: Long? = null,
    /** True when the chat has enough history to be worth summarizing. */
    val canSummarize: Boolean = false,
    /** Exact token count of the transcript from the server's `/tokenize`, measured
     *  after each reply finishes; null until the first measurement lands. */
    val tokenCount: Int? = null,
    /** The server's context window (`n_ctx`) from `/props`, or null while unknown. */
    val contextLimit: Int? = null,
)

/**
 * The chat screen's state holder. The conversation is owned by Room (the single
 * source of truth) so an in-flight reply — which is streamed and persisted by the
 * [GenerationService], not this ViewModel — keeps advancing even when the screen
 * is gone. The displayed state is a [combine] of three sources:
 *  - [local]: transient UI not worth persisting (input box, selection, editing);
 *  - the conversation flow from Room;
 *  - the live [GenerationController] overlay, which carries the streaming reply's
 *    text per-token (Room only gets throttled checkpoints).
 */
class ChatViewModel(
    val convId: Long,
    private val app: LlamaChatApp,
) : ViewModel() {

    private val repo: ConversationRepository = app.conversationRepository
    private val controller: GenerationController = app.generation
    private val summarizer: SummarizationController = app.summarization

    /** Transient, screen-only state that never touches Room. */
    private data class Local(
        val input: String = "",
        val impersonating: Boolean = false,
        val selectedMsgId: Long? = null,
        val editingMsgId: Long? = null,
        val editText: String = "",
        val editPrefix: String = "",
        val chatMenuOpen: Boolean = false,
        val closed: Boolean = false,
        val summarizeDoneId: Long? = null,
    )

    private val local = MutableStateFlow(Local())

    /** Context usage, refreshed after each generation: the exact token count of the
     *  transcript (`/tokenize`) and the model's window (`/props`). Both null until
     *  first measured; kept together so the UI combine stays a single extra source. */
    private data class Usage(val tokens: Int? = null, val limit: Int? = null)
    private val usage = MutableStateFlow(Usage())

    /** Latest persisted conversation, cached so the action handlers (send, edit,
     *  delete, …) can read-modify-write without an extra suspend round-trip. */
    @Volatile private var base: Conversation? = null

    val ui: StateFlow<ChatUiState> =
        combine(local, repo.observe(convId), controller.state, usage, summarizer.state) { l, conv, gen, u, sum ->
            base = conv
            val streaming = gen != null && gen.convId == convId
            val merged = when {
                conv != null && streaming ->
                    conv.copy(messages = conv.messages.map {
                        if (it.id == gen!!.targetId) it.withText(gen.text) else it
                    })
                else -> conv
            }
            val summarizing = sum != null && sum.convId == convId && sum.phase == SummaryPhase.RUNNING
            ChatUiState(
                loaded = true,
                conversation = merged,
                input = l.input,
                streaming = streaming,
                impersonating = l.impersonating,
                selectedMsgId = l.selectedMsgId,
                editingMsgId = l.editingMsgId,
                editText = l.editText,
                editPrefix = l.editPrefix,
                chatMenuOpen = l.chatMenuOpen,
                closed = l.closed,
                summarizing = summarizing,
                summaryProgress = if (summarizing) sum!!.text else "",
                summarizeDoneId = l.summarizeDoneId,
                canSummarize = merged != null && SummarizationConfig.canSummarize(merged.messages.size),
                tokenCount = u.tokens,
                contextLimit = u.limit,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        // Prime [base] so actions work even before the UI starts collecting.
        viewModelScope.launch { base = repo.get(convId) }
        // Measure context usage whenever a generation for this chat finishes — and
        // once on open, since the flow starts out inactive. distinctUntilChanged keeps
        // us to the active→inactive edges rather than every token. The service persists
        // the final text before clearing this state, so Room is up to date by now.
        viewModelScope.launch {
            controller.state
                .map { it?.convId == convId }
                .distinctUntilChanged()
                .collect { active -> if (!active) refreshUsage() }
        }
        // React to a finished summarization: on DONE, flag the one-shot navigation to
        // the details screen and re-measure usage (the transcript just shrank); either
        // outcome clears the shared state so a later run starts clean.
        viewModelScope.launch {
            summarizer.state.collect { st ->
                if (st == null || st.convId != convId) return@collect
                when (st.phase) {
                    SummaryPhase.DONE -> {
                        local.update { it.copy(summarizeDoneId = convId, chatMenuOpen = false) }
                        summarizer.clear(convId)
                        refreshUsage()
                    }
                    SummaryPhase.FAILED -> summarizer.clear(convId)
                    SummaryPhase.RUNNING -> {}
                }
            }
        }
    }

    /** Re-measure token usage against the server: exact transcript tokens via
     *  `/tokenize`, plus the context window via `/props` if not yet known. The
     *  context window can only be read once the server has a model loaded, which a
     *  finished reply guarantees. Failures leave the previous values in place. */
    private var usageJob: Job? = null
    private fun refreshUsage() {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            val conv = repo.get(convId) ?: return@launch
            val s = app.settingsRepository.current()
            val model = conv.model.ifEmpty { s.currentModel }
            val limit = usage.value.limit
                ?: app.llamaClient.fetchContextSize(s.ip, s.port, model).getOrNull()
            val tokens = app.llamaClient
                .countTokens(s.ip, s.port, ChatRequestBuilder.transcriptForCount(conv), model)
                .getOrNull()
            usage.update { it.copy(tokens = tokens ?: it.tokens, limit = limit ?: it.limit) }
        }
    }

    private fun isStreaming(): Boolean = controller.isActive(convId)
    private fun isBusy(): Boolean = controller.isActive(convId) || summarizer.isActive(convId)

    // ---- input -------------------------------------------------------------

    fun setInput(v: String) = local.update { it.copy(input = v) }

    fun send() {
        if (isBusy()) return
        // A blank message is allowed: it appends an empty user turn and forces the
        // assistant to take another turn.
        val text = local.value.input.trim()
        val conv = base ?: return
        val prefixes = conv.character.usesNamePrefixes
        val userId = IdGen.next()
        val assistantId = IdGen.next()
        val title = if (conv.title == "New chat" && text.isNotEmpty()) deriveTitle(text) else conv.title
        val updated = conv.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages +
                // A blank input sends a truly empty user turn even in transcript mode,
                // so the prefix isn't added to an otherwise-empty message.
                ChatMessage.user(userId, if (prefixes && text.isNotEmpty()) "${conv.userName}: $text" else text) +
                // In transcript mode, prefill the reply with "Character: " so the
                // model is forced to speak as the character; generation continues it.
                ChatMessage.assistant(assistantId, if (prefixes) "${conv.characterName}: " else ""),
        )
        local.update { it.copy(input = "", selectedMsgId = null) }
        saveThen(updated) {
            GenerationService.start(app, convId, assistantId, includePartial = prefixes, forceContinue = false, title = updated.title)
        }
    }

    private fun deriveTitle(text: String): String =
        if (text.length > 30) text.take(30) + "…" else text

    /** Add a fresh variant to the last assistant message and regenerate it. */
    fun regenerate() {
        if (isBusy()) return
        val conv = base ?: return
        val last = conv.messages.lastOrNull { it.role == Role.ASSISTANT } ?: return
        val prefixes = conv.character.usesNamePrefixes
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.map {
                if (it.id == last.id) it.addVariant(if (prefixes) "${conv.characterName}: " else "") else it
            },
        )
        local.update { it.copy(selectedMsgId = null, chatMenuOpen = false) }
        saveThen(updated) {
            GenerationService.start(app, convId, last.id, includePartial = prefixes, forceContinue = false, title = updated.title)
        }
    }

    fun continueMessage(id: Long) {
        if (isBusy()) return
        local.update { it.copy(selectedMsgId = null) }
        // The partial is already in Room; the service reads and extends it.
        GenerationService.start(app, convId, id, includePartial = true, forceContinue = true, title = base?.title ?: "")
    }

    /** Stop whatever is in flight — the reply generation and/or an impersonation.
     *  The service preserves a reply's partial and removes its notification; the
     *  UI's streaming flag clears via the controller, and an impersonation keeps
     *  whatever it had written into the input. */
    fun stop() {
        GenerationService.cancel(app)
        SummarizationService.cancel(app)
        impersonateJob?.cancel()
    }

    // ---- impersonate -------------------------------------------------------

    private var impersonateJob: Job? = null

    /**
     * Generate the user's next turn and stream it into the input box (rather than
     * committing it as a message), so the user can review and edit before sending.
     * Runs in the screen's scope — cancelled if the user leaves the chat or hits
     * the shared Stop button.
     */
    fun impersonate() {
        if (isBusy() || local.value.impersonating) return
        val conv = base ?: return
        local.update { it.copy(impersonating = true, input = "", selectedMsgId = null, chatMenuOpen = false) }
        impersonateJob = viewModelScope.launch {
            val s = app.settingsRepository.current()
            val request = ChatRequestBuilder.impersonate(conv, s)
            val sb = StringBuilder()
            try {
                app.llamaClient.streamChat(s.ip, s.port, request).collect { delta ->
                    sb.append(delta)
                    local.update { it.copy(input = sb.toString().trimStart()) }
                }
            } catch (_: CancellationException) {
                // Stop pressed or screen left: keep whatever was written so far.
            } catch (_: Exception) {
                // Leave the partial in the input; nothing else to surface here.
            } finally {
                local.update { it.copy(input = it.input.trim(), impersonating = false) }
            }
        }
    }

    // ---- variants ----------------------------------------------------------

    fun prevVariant(id: Long) = shiftVariant(id, -1)
    fun nextVariant(id: Long) = shiftVariant(id, +1)

    private fun shiftVariant(id: Long, delta: Int) {
        val conv = base ?: return
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.map { m ->
                if (m.id == id && m.variants.isNotEmpty()) {
                    m.copy(activeVariant = (m.activeVariant + delta).coerceIn(0, m.variants.size - 1))
                } else {
                    m
                }
            },
        )
        local.update { it.copy(selectedMsgId = null) }
        saveThen(updated)
    }

    // ---- selection & editing ----------------------------------------------

    fun toggleSelected(id: Long) = local.update {
        it.copy(selectedMsgId = if (it.selectedMsgId == id) null else id)
    }

    fun startEdit(id: Long) {
        val conv = base ?: return
        val m = conv.messages.firstOrNull { it.id == id } ?: return
        if (m.locked) return // summarized messages are frozen
        // Edit the text without its "Name:" prefix; remember the exact prefix to re-apply.
        val name = if (m.role == Role.USER) conv.userName else conv.characterName
        val editPrefix = namePrefix(m.text, name)
        local.update {
            it.copy(editingMsgId = id, editText = m.text.removePrefix(editPrefix), editPrefix = editPrefix, selectedMsgId = null)
        }
    }

    /**
     * The transcript "Name:" prefix present on [text], including its trailing space
     * when there is one — but an emote that follows the colon directly (e.g.
     * "Name:*waves*") has no space, so we strip just "Name:" and leave the "*".
     * Returns "" when no prefix is present (plain, non-transcript characters).
     */
    private fun namePrefix(text: String, name: String): String {
        val colon = "$name:"
        return when {
            text.startsWith("$colon ") -> "$colon "
            text.startsWith(colon) -> colon
            else -> ""
        }
    }

    fun setEditText(v: String) = local.update { it.copy(editText = v) }

    fun saveEdit() {
        val id = local.value.editingMsgId ?: return
        val text = local.value.editPrefix + local.value.editText
        val conv = base ?: return
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.map { if (it.id == id) it.withText(text) else it },
        )
        local.update { it.copy(editingMsgId = null, editText = "", editPrefix = "", selectedMsgId = id) }
        saveThen(updated)
    }

    fun cancelEdit() = local.update { it.copy(editingMsgId = null, editText = "", editPrefix = "") }

    // ---- deletion ----------------------------------------------------------

    /** Delete the last assistant message and the user turn that prompted it,
     *  restoring that prompt into the input (mirrors the prototype). */
    fun deleteLastAssistant() {
        val conv = base ?: return
        val ai = conv.messages.indexOfLast { it.role == Role.ASSISTANT }
        if (ai < 0) return
        var ui = -1
        for (j in ai - 1 downTo 0) {
            if (conv.messages[j].role == Role.USER) { ui = j; break }
        }
        val assistantId = conv.messages[ai].id
        val userId = if (ui >= 0) conv.messages[ui].id else null
        // Restore the prompt into the input without its transcript "Name:" prefix
        // (the prefix is re-applied on send), mirroring startEdit.
        val restore = if (ui >= 0)
            conv.messages[ui].text.removePrefix(namePrefix(conv.messages[ui].text, conv.userName))
            else local.value.input
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.filter { it.id != assistantId && it.id != userId },
        )
        local.update { it.copy(input = restore, selectedMsgId = null) }
        saveThen(updated)
    }

    fun clearMessages() {
        val conv = base ?: return
        // Clearing wipes the history the summary described, so drop the summary too.
        val updated = conv.copy(updatedAt = System.currentTimeMillis(), messages = emptyList(), summary = "")
        local.update { it.copy(chatMenuOpen = false, selectedMsgId = null) }
        saveThen(updated)
    }

    fun deleteConversation() {
        GenerationService.cancel(app) // tear down any reply still streaming for this chat
        viewModelScope.launch {
            repo.delete(convId)
            local.update { it.copy(chatMenuOpen = false, closed = true) }
        }
    }

    // ---- summarize ---------------------------------------------------------

    /** Compact the older messages into the conversation [summary] and lock them,
     *  freeing context so the chat can continue. Runs in the background service. */
    fun summarize() {
        val conv = base ?: return
        if (isBusy() || local.value.impersonating) return
        if (!SummarizationConfig.canSummarize(conv.messages.size)) return
        local.update { it.copy(chatMenuOpen = false, selectedMsgId = null) }
        SummarizationService.start(app, convId, conv.title)
    }

    /** Consume the one-shot navigation flag once the screen has switched to details. */
    fun consumeSummarizeDone() = local.update { it.copy(summarizeDoneId = null) }

    // ---- backup ------------------------------------------------------------

    /** The current conversation serialized as a backup file, or null if not loaded yet. */
    fun backupJson(): String? = base?.let { BackupCodec.encode(it) }

    // ---- chat menu ---------------------------------------------------------

    fun toggleChatMenu() = local.update { it.copy(chatMenuOpen = !it.chatMenuOpen) }
    fun closeChatMenu() = local.update { it.copy(chatMenuOpen = false) }

    // ---- persistence helper ------------------------------------------------

    /** Persist [conv] (updating the [base] cache immediately so back-to-back
     *  actions compose) and run [after] once the write has landed. */
    private fun saveThen(conv: Conversation, after: (suspend () -> Unit)? = null) {
        base = conv
        viewModelScope.launch {
            repo.save(conv)
            after?.invoke()
        }
    }

    companion object {
        fun factory(app: LlamaChatApp, convId: Long) = viewModelFactory {
            initializer { ChatViewModel(convId, app) }
        }
    }
}
