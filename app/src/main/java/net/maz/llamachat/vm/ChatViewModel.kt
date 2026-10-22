package net.maz.llamachat.vm

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.maz.llamachat.ui.components.bubbleInverse
import androidx.lifecycle.ViewModel
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.attach.WavRecorder
import net.maz.llamachat.data.backup.BackupCodec
import net.maz.llamachat.data.comfy.ComfyGenerationService
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.comfy.ComfyJobStatus
import net.maz.llamachat.data.gen.ChatRequestBuilder
import net.maz.llamachat.data.gen.GenerationController
import net.maz.llamachat.data.gen.GenerationService
import net.maz.llamachat.data.gen.SceneImageService
import net.maz.llamachat.data.gen.SummarizationConfig
import net.maz.llamachat.data.gen.SummarizationController
import net.maz.llamachat.data.gen.SummarizationService
import net.maz.llamachat.data.gen.SummaryPhase
import net.maz.llamachat.data.gen.containsThink
import net.maz.llamachat.data.gen.stripThink
import net.maz.llamachat.data.model.Attachment
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.CastMember
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.ModelCapabilities
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.data.model.SceneImageMeta

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
     *  after each reply finishes and cached on the conversation. On reopen it shows the
     *  last cached value (no fresh `/tokenize` on load); null only when never measured. */
    val tokenCount: Int? = null,
    /** The server's context window (`n_ctx`) from `/props`, or null while unknown. */
    val contextLimit: Int? = null,
    /** Image staged in the input bar, sent with the next message. */
    val pendingImage: Attachment? = null,
    /** True when the conversation's model name marks it vision-capable (-VL-/-VAL-). */
    val canAttachImage: Boolean = false,
    /** True when the conversation's model name marks it audio-capable (-AL-/-VAL-). */
    val canRecordAudio: Boolean = false,
    /** True while the mic button is held and audio is being captured. */
    val recording: Boolean = false,
    /** True when a scene-image workflow + prompt field are configured (enables the menu item). */
    val sceneImageEnabled: Boolean = false,
    /** Live ComfyUI phase of each scene-image placeholder in this chat, by message id. */
    val sceneJobPhases: Map<Long, ComfyJobStatus> = emptyMap(),
    /** Scene-image placeholders whose LLM describe step is running right now. */
    val describingSceneIds: Set<Long> = emptySet(),
    /** Multi-character chats: the cast member picked to speak next; null = Auto (the
     *  model chooses from context). Only meaningful when [showSpeakerPicker]. */
    val speaker: String? = null,
    /** True when a speaker picker should show: transcript mode with 2+ characters. */
    val showSpeakerPicker: Boolean = false,
    /** Cast member whose LLM state update is streaming right now (one at a time). */
    val updatingCastName: String? = null,
    /** Names queued for per-character state updates after a "Story + characters" summarize. */
    val castSummariesPending: List<String> = emptyList(),
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
        val closed: Boolean = false,
        val summarizeDoneId: Long? = null,
        val pendingImage: Attachment? = null,
        val recording: Boolean = false,
        /** Cast member picked to speak next; null = Auto. Screen lifetime only. */
        val speaker: String? = null,
    )

    /** Per-character state updates in flight (button tap or post-summarize queue). */
    private data class CastUpdate(
        val name: String,
        /** Names still waiting behind the one currently streaming. */
        val pending: List<String> = emptyList(),
    )

    private val local = MutableStateFlow(Local())
    private val castUpdate = MutableStateFlow<CastUpdate?>(null)

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
                closed = l.closed,
                summarizing = summarizing,
                summaryProgress = if (summarizing) sum!!.text else "",
                summarizeDoneId = l.summarizeDoneId,
                canSummarize = merged != null && SummarizationConfig.canSummarize(merged.messages.size),
                // Prefer a live measurement; fall back to the count persisted on the
                // conversation so a reopened chat shows its last-known usage without a
                // fresh `/tokenize` (which would force the server to load the model).
                tokenCount = u.tokens ?: merged?.tokenCount?.takeIf { it > 0 },
                contextLimit = u.limit ?: merged?.contextLimit?.takeIf { it > 0 },
                pendingImage = l.pendingImage,
                recording = l.recording,
            )
        }.combine(app.settingsRepository.settings) { state, s ->
            // Attachment affordances follow the model actually used for requests:
            // the conversation's own, falling back to the persisted current model.
            val model = state.conversation?.model?.takeIf { it.isNotEmpty() } ?: s.currentModel
            val sceneEnabled = s.sceneWorkflowId >= 0 &&
                s.scenePromptNodeTitle.isNotBlank() && s.scenePromptInput.isNotBlank() &&
                app.workflowStore.workflows.value.any { it.id == s.sceneWorkflowId }
            state.copy(
                canAttachImage = ModelCapabilities.supportsImages(model),
                canRecordAudio = ModelCapabilities.supportsAudio(model),
                sceneImageEnabled = sceneEnabled,
            )
        }.combine(app.comfyJobs.jobs) { state, jobs ->
            // Live phase of this chat's scene-image jobs, so placeholders can show progress.
            val phases = jobs
                .filter { it.destination == ComfyJob.DEST_CHAT && it.convId == convId }
                .associate { it.messageId to it.status }
            state.copy(sceneJobPhases = phases)
        }.combine(app.sceneImages.describing) { state, describing ->
            state.copy(describingSceneIds = describing)
        }.combine(castUpdate) { state, cu ->
            val conv2 = state.conversation
            // The picker shows in transcript mode with more than one character: the
            // primary plus any cast members (the primary's own state entry, if present,
            // doesn't add a speaker).
            val extraSpeakers = conv2?.cast?.map { it.name }
                ?.filterNot { it == conv2.characterName } ?: emptyList()
            state.copy(
                speaker = local.value.speaker,
                showSpeakerPicker = (conv2?.character?.usesNamePrefixes == true) && extraSpeakers.size >= 1,
                updatingCastName = cu?.name,
                castSummariesPending = cu?.pending ?: emptyList(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        // Prime [base] so actions work even before the UI starts collecting.
        viewModelScope.launch { base = repo.get(convId) }
        // Reconcile scene-image placeholders left mid-flight by a process death: a
        // "describing" message with no live describe, or a "generating" one with no
        // Comfy job (submitted jobs resume via the ledger), can never finish — mark it
        // failed so the bubble offers Retry instead of spinning forever. One-shot at
        // startup, before the user can trigger new scene images.
        viewModelScope.launch { reconcileStaleScenes() }
        // Measure context usage whenever a generation for this chat finishes.
        // distinctUntilChanged keeps us to the active→inactive edges rather than every
        // token; drop(1) skips the initial state on open so we DON'T `/tokenize` merely
        // for reopening an old chat (which would force the server to load the model) —
        // the readout shows the value cached on the conversation until the next reply.
        // The service persists the final text before clearing this state, so Room is up
        // to date by the time we measure.
        viewModelScope.launch {
            controller.state
                .map { it?.convId == convId }
                .distinctUntilChanged()
                .drop(1)
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
                        local.update { it.copy(summarizeDoneId = convId) }
                        summarizer.clear(convId)
                        refreshUsage()
                        // "Story + characters": now that the fresh story summary is in,
                        // run each cast member's own independent state update.
                        castSummarizeQueue?.let { queue ->
                            castSummarizeQueue = null
                            startCastUpdates(queue)
                        }
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
     *  finished reply guarantees. A fresh count is also cached on the conversation so
     *  the readout can show on reopen without re-measuring. Failures leave the previous
     *  values in place. */
    private var usageJob: Job? = null
    private fun refreshUsage() {
        usageJob?.cancel()
        usageJob = viewModelScope.launch {
            val conv = repo.get(convId) ?: return@launch
            val s = app.settingsRepository.current()
            val model = conv.model.ifEmpty { s.currentModel }
            // The window doesn't change per model, so prefer any value already known —
            // the session's, then the one cached on the conversation — before asking
            // `/props`.
            val limit = usage.value.limit
                ?: conv.contextLimit.takeIf { it > 0 }
                ?: app.llamaClient.fetchContextSize(s.ip, s.port, model).getOrNull()
            val tokens = app.llamaClient
                .countTokens(s.ip, s.port, ChatRequestBuilder.transcriptForCount(conv), model)
                .getOrNull()
            usage.update { it.copy(tokens = tokens ?: it.tokens, limit = limit ?: it.limit) }
            // Cache the fresh values on the conversation for the next reopen. Re-read so a
            // concurrent edit isn't clobbered, and keep updatedAt as-is (a usage refresh
            // shouldn't reorder the conversation list).
            repo.get(convId)?.let { fresh ->
                val newTokens = tokens ?: fresh.tokenCount
                val newLimit = limit ?: fresh.contextLimit
                if (newTokens != fresh.tokenCount || newLimit != fresh.contextLimit) {
                    repo.save(fresh.copy(tokenCount = newTokens, contextLimit = newLimit))
                }
            }
        }
    }

    private fun isStreaming(): Boolean = controller.isActive(convId)
    private fun isBusy(): Boolean = controller.isActive(convId) || summarizer.isActive(convId)

    // ---- input -------------------------------------------------------------

    fun setInput(v: String) = local.update { it.copy(input = v) }

    /** Pick which cast member speaks next; null = Auto (the model chooses). */
    fun setSpeaker(name: String?) = local.update { it.copy(speaker = name) }

    fun send() = sendInternal(local.value.input.trim(), listOfNotNull(local.value.pendingImage))

    /**
     * Append a user turn (text and/or [attachments]) plus the empty assistant turn,
     * then hand generation to the service. A blank, attachment-free message is
     * allowed: it forces the assistant to take another turn.
     */
    private fun sendInternal(text: String, attachments: List<Attachment>) {
        if (isBusy()) return
        val conv = base ?: return
        val prefixes = conv.character.usesNamePrefixes
        // In a multi-character chat the picker names who speaks; Auto (null) leaves the
        // turn empty and lets the model choose from the "## Characters" system section.
        val speaker = if (prefixes) local.value.speaker else null
        val userId = IdGen.next()
        val assistantId = IdGen.next()
        val title = if (conv.title == "New chat" && text.isNotEmpty()) deriveTitle(text) else conv.title
        val updated = conv.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages +
                // A blank input sends a truly empty user turn even in transcript mode,
                // so the prefix isn't added to an otherwise-empty message.
                ChatMessage.user(
                    userId,
                    if (prefixes && text.isNotEmpty()) "${conv.userName}: $text" else text,
                    attachments = attachments,
                ) +
                // In transcript mode, prefill the reply with "Name: " so the model is
                // forced to speak as that character; generation continues it. Auto mode
                // seeds an empty turn (no prefix) instead.
                ChatMessage.assistant(
                    assistantId,
                    if (prefixes && speaker != null) "$speaker: " else "",
                    speaker = speaker.orEmpty(),
                ),
        )
        local.update { it.copy(input = "", selectedMsgId = null, pendingImage = null) }
        saveThen(updated) {
            GenerationService.start(
                app, convId, assistantId,
                includePartial = prefixes && speaker != null,
                forceContinue = false, title = updated.title,
                speaker = speaker.orEmpty(),
            )
        }
    }

    // ---- attachments ---------------------------------------------------------

    /** Import (downscale + copy) the picked image and stage it in the input bar,
     *  replacing any image already staged. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val imported = app.attachmentStore.importImage(convId, uri) ?: return@launch
            val previous = local.value.pendingImage
            local.update { it.copy(pendingImage = imported) }
            previous?.let { app.attachmentStore.delete(convId, listOf(it)) }
        }
    }

    fun removePendingImage() {
        val old = local.value.pendingImage ?: return
        local.update { it.copy(pendingImage = null) }
        viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(old)) }
    }

    // ---- voice messages ------------------------------------------------------

    private val wavRecorder = WavRecorder()
    private var recordingId: Long? = null

    /** Begin capturing a voice message (mic held). Caller must hold RECORD_AUDIO. */
    fun startRecording() {
        if (isBusy() || local.value.impersonating || local.value.recording) return
        val id = IdGen.next()
        if (wavRecorder.start(app.attachmentStore.newAudioFile(convId, id))) {
            recordingId = id
            local.update { it.copy(recording = true) }
        }
    }

    /**
     * Mic released: finalize the clip and send it immediately as its own message
     * (audio and text are mutually exclusive). Sub-[MIN_RECORDING_MS] clips are
     * discarded as accidental taps.
     */
    fun stopRecording(cancelled: Boolean = false) {
        val id = recordingId ?: return
        recordingId = null
        val durationMs = wavRecorder.stop()
        local.update { it.copy(recording = false) }
        val att = Attachment(id, Attachment.KIND_AUDIO, "$id.wav", "audio/wav", durationMs)
        if (cancelled || durationMs < MIN_RECORDING_MS) {
            viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(att)) }
            return
        }
        sendInternal("", listOf(att))
    }

    override fun onCleared() {
        // Screen died mid-hold: stop the hardware and drop the never-sent clip.
        val id = recordingId
        recordingId = null
        wavRecorder.release()
        if (id != null) {
            app.attachmentStore.delete(convId, listOf(Attachment(id, Attachment.KIND_AUDIO, "$id.wav")))
        }
        super.onCleared()
    }

    private fun deriveTitle(text: String): String =
        if (text.length > 30) text.take(30) + "…" else text

    /** Add a fresh variant to the last assistant message and regenerate it — or, when the
     *  conversation ends on a bare user turn (e.g. an empty reply was discarded, or a reply
     *  never landed), append a new assistant turn and generate a reply for it. This keeps a
     *  stranded chat recoverable with one tap. */
    fun regenerate(ignoreEos: Boolean = false) {
        if (isBusy()) return
        val conv = base ?: return
        val prefixes = conv.character.usesNamePrefixes
        val speaker = if (prefixes) local.value.speaker else null
        // Re-rolling a turn that already has a speaker keeps it, so "Regenerate" means
        // "another take by the same character"; Auto re-rolls stay auto.
        val lastReal = conv.messages.lastOrNull { !it.isSceneImage } ?: return
        if (lastReal.role == Role.USER) {
            // No trailing assistant to revariant: give the dangling user turn a reply.
            val assistantId = IdGen.next()
            val prefill = if (prefixes && speaker != null) "$speaker: " else ""
            val updated = conv.copy(
                updatedAt = System.currentTimeMillis(),
                messages = conv.messages + ChatMessage.assistant(assistantId, prefill, speaker = speaker.orEmpty()),
            )
            local.update { it.copy(selectedMsgId = null) }
            saveThen(updated) {
                GenerationService.start(app, convId, assistantId, includePartial = prefixes && speaker != null, forceContinue = false, title = updated.title, ignoreEos = ignoreEos, speaker = speaker.orEmpty())
            }
            return
        }
        val last = conv.messages.lastOrNull { it.role == Role.ASSISTANT && !it.isSceneImage } ?: return
        val who = if (prefixes) (last.speaker.ifBlank { conv.characterName }) else ""
        val prefill = if (prefixes) "$who: " else ""
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.map {
                if (it.id == last.id) it.addVariant(prefill).copy(speaker = who) else it
            },
        )
        local.update { it.copy(selectedMsgId = null) }
        saveThen(updated) {
            GenerationService.start(app, convId, last.id, includePartial = prefixes, forceContinue = false, title = updated.title, ignoreEos = ignoreEos, speaker = who)
        }
    }

    fun continueMessage(id: Long, ignoreEos: Boolean = false) {
        if (isBusy()) return
        local.update { it.copy(selectedMsgId = null) }
        // The partial is already in Room; the service reads and extends it.
        GenerationService.start(app, convId, id, includePartial = true, forceContinue = true, title = base?.title ?: "", ignoreEos = ignoreEos)
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
     *
     * @param continueInput keep what's already typed and generate the rest of that
     *   line; otherwise the box is cleared and the model writes the turn from scratch.
     */
    fun impersonate(continueInput: Boolean = false, ignoreEos: Boolean = false) {
        if (isBusy() || local.value.impersonating) return
        val conv = base ?: return
        val head = if (continueInput) local.value.input.trimEnd() else ""
        local.update { it.copy(impersonating = true, input = head, selectedMsgId = null) }
        impersonateJob = viewModelScope.launch {
            val s = app.settingsRepository.current()
            val request = ChatRequestBuilder.impersonate(conv, s, prefill = head, ignoreEos = ignoreEos) { m ->
                m.attachments.mapNotNull { app.attachmentStore.toContentPart(convId, it) }
            }
            // ChatRequestBuilder folds the impersonation onto the prior assistant turn,
            // ending the prefill with this marker; the model's continuation is the user's
            // line. Newer llama-server echoes the whole prefill back at the start of the
            // stream (older builds returned only the continuation), so keep just the text
            // after the last marker — otherwise the prior assistant turn lands in the box.
            val marker = "\n${conv.userName}:"
            val sb = StringBuilder()
            try {
                app.llamaClient.streamChat(s.ip, s.port, request).collect { delta ->
                    sb.append(delta)
                    // The model may emit a reasoning block (e.g. Gemma's thought channel)
                    // ahead of the user's line; keep it out of the input box. A block still
                    // being streamed drops out entirely until it closes.
                    val shown = sb.toString().substringAfterLast(marker)
                    val body = if (containsThink(shown)) stripThink(shown) else shown
                    local.update { it.copy(input = foldOntoPrefill(head, body)) }
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

    /**
     * Combine the text we prefilled with ([head], already in the input box) and the
     * model's [body] for the input box. Servers differ: newer llama-server echoes the
     * prefill back at the start of the stream (so [body] already contains [head]),
     * older ones return only the continuation and we join the two ourselves — keeping
     * the model's own leading whitespace, since it may be continuing mid-word.
     */
    private fun foldOntoPrefill(head: String, body: String): String {
        val shown = body.trimStart()
        return when {
            head.isEmpty() -> shown
            shown.startsWith(head) -> shown // the echo landed
            head.startsWith(shown) -> head // still echoing our own text back
            else -> head + body
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
        if (m.locked || m.isSceneImage) return // summarized messages are frozen; scene images aren't text
        // Edit the visible answer: drop any reasoning block (e.g. Gemma's thought
        // channel) so it isn't shown — or re-saved — then strip the "Name:" prefix,
        // remembering the exact prefix to re-apply on save. In multi-character chats
        // the assistant's speaker may be any cast member, so try every known name.
        val body = if (containsThink(m.text)) stripThink(m.text) else m.text
        val names = if (m.role == Role.USER) listOf(conv.userName) else speakerNames(conv)
        val editPrefix = names.firstNotNullOfOrNull { namePrefix(body, it) } ?: ""
        local.update {
            it.copy(editingMsgId = id, editText = body.removePrefix(editPrefix), editPrefix = editPrefix, selectedMsgId = null)
        }
    }

    /** Every speaker name in this conversation: the user plus the primary character and
     *  each cast member (deduped). Used to find/strip transcript "Name:" prefixes. */
    fun speakerNames(conv: Conversation): List<String> = buildList {
        add(conv.userName)
        add(conv.characterName)
        conv.cast.forEach { add(it.name) }
    }.distinct()

    /**
     * The transcript "Name:" prefix present on [text], including its trailing space
     * when there is one — but an emote that follows the colon directly (e.g.
     * "Name:*waves*") has no space, so we strip just "Name:" and leave the "*".
     * Returns null when no prefix is present (plain, non-transcript characters).
     */
    private fun namePrefix(text: String, name: String): String? {
        val colon = "$name:"
        return when {
            text.startsWith("$colon ") -> "$colon "
            text.startsWith(colon) -> colon
            else -> null
        }
    }

    /**
     * The cast member who spoke [m], read from its stored transcript prefix. Returns
     * the primary's name for a blank (legacy/auto) speaker, or null when no known
     * name prefixes the text — e.g. plain assistant-style chats with no "Name:" at all.
     */
    fun speakerOf(m: ChatMessage, conv: Conversation): String? =
        if (m.role == Role.USER) null
        else m.speaker.ifBlank { conv.characterName }
            .takeIf { namePrefix(m.text, it) != null }

    /** The bubble colours for [name]'s messages in this conversation: the cast
     *  member's chosen palette pair when set, else a tint/inverse derived from the
     *  catalog character's colour. */
    fun speakerBubbleColors(name: String, conv: Conversation): BubbleColors {
        val pick: Long? = conv.cast.firstOrNull { it.name == name }?.colorArgb?.takeIf { it >= 0 }
        return if (pick != null) {
            Catalog.palette.firstOrNull { it.first.value.toLong() == pick || it.second.value.toLong() == pick }
                ?.let { BubbleColors(it.first, it.second) }
                ?: BubbleColors.derive(Color(pick))
        } else BubbleColors.derive(Catalog.character(name).color)
    }

    /** The palette pair a fresh cast member gets: the first entry no current cast
     *  member (or the primary character's colour) is already using, so every voice
     *  reads distinctly at a glance. */
    fun nextCastColor(conv: Conversation): Pair<Color, Color> {
        val taken = conv.cast.mapNotNull { it.colorArgb?.takeIf { c -> c >= 0 } }.toSet() +
            Catalog.character(conv.characterName).color.value.toLong()
        return Catalog.palette.firstOrNull { p ->
            p.first.value.toLong() !in taken && p.second.value.toLong() !in taken
        } ?: Catalog.defaultColor
    }

    /** Set a member's message colour to the palette pair containing [color] (either
     *  its light or dark half) — one pick drives both themes. */
    fun setCastColor(name: String, color: Color) {
        val conv = base ?: return
        val argb = Catalog.palette.firstOrNull { p ->
            p.first.value == color.value || p.second.value == color.value
        }?.let { it.first.value.toLong() } ?: color.value.toLong()
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = conv.cast.map { if (it.name == name) it.copy(colorArgb = argb) else it },
        ))
    }

    /** Auto-assign a colour to every member that doesn't have one yet, so labels and
     *  avatars are distinct from the moment a cast gains its second member. */
    private fun autoAssignCastColors(conv: Conversation): List<CastMember> {
        var taken = conv.cast.mapNotNull { it.colorArgb?.takeIf { c -> c >= 0 } }.toMutableSet()
        return conv.cast.map { m ->
            if (m.colorArgb < 0) {
                val pair = nextCastColor(conv)
                taken += pair.first.value.toLong()
                taken += pair.second.value.toLong()
                m.copy(colorArgb = pair.first.value.toLong())
            } else m
        }
    }

    /** The four colours a speaker's bubble needs: background and text colour, each
     *  for light and dark theme. A palette pick supplies both halves directly;
     *  [derive] turns a single base colour (a catalog character) into the same shape —
     *  a pale tint with dark text in light mode, its inverse with white text in dark. */
    data class BubbleColors(
        val light: Color,
        val dark: Color,
        val onLight: Color = Color(0xDE000000),
        val onDark: Color = Color.White,
    ) {
        fun background(isDark: Boolean): Color = if (isDark) dark else light
        fun text(isDark: Boolean): Color = if (isDark) onDark else onLight

        companion object {
            fun derive(base: Color): BubbleColors = BubbleColors(
                light = lerp(Color.White, base, 0.24f),
                dark = bubbleInverse(base),
            )
        }
    }

    /** The inverse of this colour: each channel flipped around mid-gray, so the same
     *  pick reads as a "dark" variant in dark mode without losing its hue. */
    private fun Color.inverse(): Color = bubbleInverse(this)

    private fun Color.redInv(): Int = 255 - ((this.red * 255).roundToInt())
    private fun Color.greenInv(): Int = 255 - ((this.green * 255).roundToInt())
    private fun Color.blueInv(): Int = 255 - ((this.blue * 255).roundToInt())

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
        val ai = conv.messages.indexOfLast { it.role == Role.ASSISTANT && !it.isSceneImage }
        if (ai < 0) return
        var ui = -1
        for (j in ai - 1 downTo 0) {
            if (conv.messages[j].role == Role.USER && !conv.messages[j].isSceneImage) { ui = j; break }
        }
        val assistantId = conv.messages[ai].id
        val userId = if (ui >= 0) conv.messages[ui].id else null
        // Restore the prompt into the input without its transcript "Name:" prefix
        // (the prefix is re-applied on send), mirroring startEdit.
        val restore = if (ui >= 0) {
            val text = conv.messages[ui].text
            text.removePrefix(namePrefix(text, conv.userName) ?: "")
        } else local.value.input
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.filter { it.id != assistantId && it.id != userId },
        )
        local.update { it.copy(input = restore, selectedMsgId = null) }
        val removedAtts = conv.messages
            .filter { it.id == assistantId || it.id == userId }
            .flatMap { it.attachments }
        if (removedAtts.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, removedAtts) }
        }
        saveThen(updated)
    }

    fun clearMessages() {
        val conv = base ?: return
        // Clearing wipes the history the summary described, so drop the summary too.
        val updated = conv.copy(updatedAt = System.currentTimeMillis(), messages = emptyList(), summary = "")
        // deleteAll wipes the conversation's whole attachment dir — including any
        // staged (pending) image — so drop that from the input bar too.
        local.update { it.copy(selectedMsgId = null, pendingImage = null) }
        cancelSceneWork() // stop scene images whose files/messages are about to vanish
        viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.deleteAll(convId) }
        saveThen(updated)
    }

    fun deleteConversation() {
        GenerationService.cancel(app) // tear down any reply still streaming for this chat
        cancelSceneWork() // and any scene-image describe/render for it
        viewModelScope.launch {
            repo.delete(convId)
            launch(Dispatchers.IO) { app.attachmentStore.deleteAll(convId) }
            local.update { it.copy(closed = true) }
        }
    }

    // ---- summarize ---------------------------------------------------------

    /** Compact the older messages into the conversation [summary] and lock them,
     *  freeing context so the chat can continue. Runs in the background service. When
     *  [includeCharacters], each cast member additionally gets its own independent state
     *  update (one request per character, each writing only its own field) once the story
     *  summary lands. */
    fun summarize(includeCharacters: Boolean = false) {
        val conv = base ?: return
        if (isBusy() || local.value.impersonating) return
        if (!SummarizationConfig.canSummarize(conv.messages.size)) return
        local.update { it.copy(selectedMsgId = null) }
        SummarizationService.start(app, convId, conv.title)
        if (includeCharacters) {
            // Queue the per-character updates; they start when the story run reaches DONE
            // (the observer below), so the fresh summary feeds each character's request.
            val names = conv.cast.map { it.name }
            if (names.isNotEmpty()) castSummarizeQueue = names
        }
    }

    /** Names waiting to be summarized after the story summary finishes; null when idle. */
    private var castSummarizeQueue: List<String>? = null

    /** Consume the one-shot navigation flag once the screen has switched to details. */
    fun consumeSummarizeDone() = local.update { it.copy(summarizeDoneId = null) }

    // ---- cast management ---------------------------------------------------

    /** The primary character's per-conversation state entry, if any. */
    private fun Conversation.primaryMember(): CastMember? =
        cast.firstOrNull { it.name == characterName }

    /** Add a self-contained (per-conversation) character to the cast. Deduped by name;
     *  returns false when the name is already taken. */
    fun addCastMember(name: String, context: String): Boolean {
        val conv = base ?: return false
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        if (conv.cast.any { it.name == cleanName } || cleanName == conv.characterName) return false
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = autoAssignCastColors(conv) +
                CastMember(cleanName, context.trim(), colorArgb = nextCastColor(conv).first.value.toLong()),
        ))
        return true
    }

    /** Rename and/or re-persona a cast member. [newName] blank keeps the old name. */
    fun updateCastMember(name: String, newName: String, context: String) {
        val conv = base ?: return
        val cleanName = newName.trim().ifEmpty { name }
        if (cleanName == name && conv.cast.any { it.name == name }) {
            saveThen(conv.copy(
                updatedAt = System.currentTimeMillis(),
                cast = conv.cast.map { if (it.name == name) it.copy(context = context.trim()) else it },
            ))
            return
        }
        if (conv.cast.any { it.name == cleanName }) return // name already taken
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = conv.cast.map {
                if (it.name == name) CastMember(cleanName, context.trim(), it.state, it.refImage, it.colorArgb) else it
            },
        ))
    }

    /** Drop a member from the cast (the character library is untouched); its reference
     *  image file is deleted too. */
    fun removeCastMember(name: String) {
        val conv = base ?: return
        val member = conv.cast.firstOrNull { it.name == name } ?: return
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = conv.cast.filterNot { it.name == name },
        ))
        member.refImage?.let { att ->
            viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(att)) }
        }
    }

    /** Manually edit a member's state text. */
    fun setCastState(name: String, state: String) {
        val conv = base ?: return
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = conv.cast.map { if (it.name == name) it.copy(state = state.trim()) else it },
        ))
    }

    /** Import (downscale + copy) the picked image as a member's reference, replacing any
     *  previous one. */
    fun setRefImageFromUri(name: String, uri: Uri) {
        viewModelScope.launch {
            val imported = app.attachmentStore.importImage(convId, uri) ?: return@launch
            val conv = base ?: return@launch
            val member = conv.cast.firstOrNull { it.name == name } ?: return@launch
            saveThen(conv.copy(
                updatedAt = System.currentTimeMillis(),
                cast = conv.cast.map { if (it.name == name) it.copy(refImage = imported) else it },
            ))
            member.refImage?.let { old ->
                if (old.fileName != imported.fileName)
                    launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(old)) }
            }
        }
    }

    /** Take a finished scene image in this conversation as the member's reference. The
     *  bytes are copied so the member owns its own file (deleting the scene later can't
     *  strip the reference). */
    fun setRefImageFromScene(name: String, messageId: Long) {
        val conv = base ?: return
        val msg = conv.messages.firstOrNull { it.id == messageId } ?: return
        val att = msg.attachments.firstOrNull() ?: return
        viewModelScope.launch {
            val copy = app.attachmentStore.copy(convId, att) ?: return@launch
            val fresh = base ?: return@launch
            val member = fresh.cast.firstOrNull { it.name == name } ?: return@launch
            saveThen(fresh.copy(
                updatedAt = System.currentTimeMillis(),
                cast = fresh.cast.map { if (it.name == name) it.copy(refImage = copy) else it },
            ))
            member.refImage?.let { old ->
                if (old.fileName != copy.fileName)
                    launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(old)) }
            }
        }
    }

    /** Remove a member's reference image. */
    fun clearRefImage(name: String) {
        val conv = base ?: return
        val member = conv.cast.firstOrNull { it.name == name } ?: return
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = conv.cast.map { if (it.name == name) it.copy(refImage = null) else it },
        ))
        member.refImage?.let { att ->
            viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, listOf(att)) }
        }
    }

    /** Copy a per-conversation member into the shared character library (explicit export;
     *  nothing syncs automatically). Returns the resolved library name. */
    fun exportToCatalog(name: String): String? {
        val conv = base ?: return null
        val member = conv.cast.firstOrNull { it.name == name } ?: return null
        viewModelScope.launch {
            app.characterRepository.upsert(
                originalName = null,
                name = member.name,
                context = member.context,
                greeting = null,
                description = "",
                usesNamePrefixes = true,
            )
        }
        return member.name
    }

    /** Pull a catalog character into this conversation's cast as a fresh per-conversation
     *  member (name + persona copied; state starts blank). Returns false when the name is
     *  already in the cast or is the primary. */
    fun importFromCatalog(name: String): Boolean {
        val conv = base ?: return false
        val c = app.characterRepository.get(name) ?: return false
        if (conv.cast.any { it.name == c.name } || c.name == conv.characterName) return false
        saveThen(conv.copy(
            updatedAt = System.currentTimeMillis(),
            cast = autoAssignCastColors(conv) +
                CastMember(c.name, c.context, colorArgb = nextCastColor(conv).first.value.toLong()),
        ))
        return true
    }

    /**
     * Rewrite one member's state notes from the conversation so far — an independent LLM
     * request that writes only that member's [CastMember.state]. Runs in the screen's scope
     * (never the single-flight generation service, so it can't cancel an in-flight reply);
     * at most one runs at a time, and a failure keeps the previous state.
     */
    fun updateCastState(name: String) {
        if (castUpdate.value != null) return
        val conv = base ?: return
        if (conv.cast.none { it.name == name }) return
        castUpdate.value = CastUpdate(name)
        viewModelScope.launch {
            runCastStateUpdate(conv, name)
            // Continue with whatever is queued behind this one; a single update from the
            // button has no queue, so clear the banner and stop.
            val next = castUpdate.value?.pending?.firstOrNull()
            if (next != null) {
                castUpdate.update { cu -> cu?.copy(name = next, pending = cu.pending.drop(1)) }
                runCastStateUpdate(base ?: conv, next)
            } else {
                castUpdate.value = null
            }
        }
    }

    /** Queue [names] for sequential state updates starting now. */
    private fun startCastUpdates(names: List<String>) {
        if (names.isEmpty() || castUpdate.value != null) return
        val head = names.first()
        castUpdate.value = CastUpdate(head, names.drop(1))
        viewModelScope.launch {
            runCastStateUpdate(base ?: return@launch, head)
            continueCastUpdates()
        }
    }

    private fun continueCastUpdates() {
        val next = castUpdate.value?.pending?.firstOrNull() ?: return
        castUpdate.update { cu -> cu?.copy(name = next, pending = cu.pending.drop(1)) }
        viewModelScope.launch {
            runCastStateUpdate(base ?: return@launch, next)
            continueCastUpdates()
        }
    }

    private suspend fun runCastStateUpdate(conv: Conversation, name: String) {
        val s = app.settingsRepository.current()
        val request = ChatRequestBuilder.castStateUpdate(conv, name, s) { m ->
            m.refImage?.let { listOfNotNull(app.attachmentStore.toContentPart(convId, it)) } ?: emptyList()
        }
        val sb = StringBuilder()
        try {
            app.llamaClient.streamChat(s.ip, s.port, request).collect { delta -> sb.append(delta) }
        } catch (_: CancellationException) {
            return // screen left or Stop pressed; keep the previous state
        } catch (_: Exception) {
            return // failure keeps the previous state; the queue continues
        }
        val state = stripThink(sb.toString()).trim()
        if (state.isEmpty()) return
        // Re-read so a concurrent edit isn't clobbered.
        val fresh = repo.get(convId) ?: return
        repo.save(fresh.copy(
            updatedAt = System.currentTimeMillis(),
            cast = fresh.cast.map { if (it.name == name) it.copy(state = state) else it },
        ))
    }

    // ---- scene images ------------------------------------------------------

    /** Embed a scene-image placeholder at the end of the chat and start generating:
     *  the model describes the scene around [focus], then a ComfyUI job renders it.
     *  The placeholder is persisted immediately so it survives a process death. */
    fun generateSceneImage(focus: String) {
        val conv = base ?: return
        val messageId = IdGen.next()
        val placeholder = ChatMessage(
            id = messageId,
            role = Role.ASSISTANT,
            sceneImage = SceneImageMeta(focus = focus.trim(), status = SceneImageMeta.STATUS_DESCRIBING),
        )
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages + placeholder,
        )
        local.update { it.copy(selectedMsgId = null) }
        saveThen(updated) { SceneImageService.start(app, convId, messageId, reusePrompt = false) }
    }

    /** Regenerate from an existing scene image, appending ANOTHER placeholder (the
     *  original is untouched). [editedPrompt], when non-null, renders that exact description
     *  (the user hand-edited it); otherwise [reusePrompt] re-runs the saved description with
     *  a fresh seed, and if neither, the model writes a new description from the same focus. */
    fun regenerateScene(sourceMessageId: Long, reusePrompt: Boolean = false, editedPrompt: String? = null) {
        val conv = base ?: return
        val src = conv.messages.firstOrNull { it.id == sourceMessageId }?.sceneImage ?: return
        val messageId = IdGen.next()
        // An edited prompt renders directly, like reuse, just with different text.
        val reuse = reusePrompt || editedPrompt != null
        val meta = when {
            editedPrompt != null ->
                SceneImageMeta(focus = src.focus, prompt = editedPrompt.trim(), status = SceneImageMeta.STATUS_GENERATING)
            reusePrompt ->
                SceneImageMeta(focus = src.focus, prompt = src.prompt, status = SceneImageMeta.STATUS_GENERATING)
            else ->
                SceneImageMeta(focus = src.focus, status = SceneImageMeta.STATUS_DESCRIBING)
        }
        val placeholder = ChatMessage(id = messageId, role = Role.ASSISTANT, sceneImage = meta)
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages + placeholder,
        )
        local.update { it.copy(selectedMsgId = null) }
        saveThen(updated) { SceneImageService.start(app, convId, messageId, reuse) }
    }

    /** Embed a scene-image placeholder whose prompt is [prompt] itself — sent verbatim
     *  to ComfyUI with no describe step, so the image model sees exactly this text
     *  (e.g. a chat message copied straight over). */
    fun generateSceneFromText(prompt: String) {
        val conv = base ?: return
        // Drop the transcript "Name:" prefix — it's bookkeeping for the chat, not part
        // of what should reach the image model. The speaker may be any cast member.
        val trimmed = (speakerNames(conv).firstNotNullOfOrNull { namePrefix(prompt, it) } ?: "")
            .let { prompt.removePrefix(it) }.trim()
        if (trimmed.isEmpty()) return
        val messageId = IdGen.next()
        val placeholder = ChatMessage(
            id = messageId,
            role = Role.ASSISTANT,
            sceneImage = SceneImageMeta(
                focus = trimmed.take(80),
                prompt = trimmed,
                status = SceneImageMeta.STATUS_GENERATING,
                verbatimPrompt = true,
            ),
        )
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages + placeholder,
        )
        local.update { it.copy(selectedMsgId = null) }
        saveThen(updated) { SceneImageService.start(app, convId, messageId, reusePrompt = true) }
    }

    /** Re-run a failed (or stalled) scene-image placeholder in place. Reuses the saved
     *  prompt when one exists (failed during rendering), else re-describes. */
    fun retryScene(messageId: Long) {
        val conv = base ?: return
        val meta = conv.messages.firstOrNull { it.id == messageId }?.sceneImage ?: return
        val reuse = meta.prompt.isNotBlank()
        val status = if (reuse) SceneImageMeta.STATUS_GENERATING else SceneImageMeta.STATUS_DESCRIBING
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.map { m ->
                if (m.id == messageId && m.sceneImage != null)
                    m.copy(sceneImage = m.sceneImage.copy(status = status, error = ""))
                else m
            },
        )
        saveThen(updated) { SceneImageService.start(app, convId, messageId, reuse) }
    }

    /**
     * Send a scene image to the model as a user turn — scene images are local-only, so
     * this is the one way the model ever sees one. The image rides on a narrated line
     * ("[User has shared an image of <focus>]") because a bare image with no context
     * reads as a non-sequitur mid-roleplay. Only meaningful on a vision model; the
     * caller gates the action on that.
     *
     * The bytes are copied to a fresh attachment so the new message owns its own file
     * and deleting the scene image later can't strip the image from the transcript.
     */
    fun shareSceneImage(messageId: Long) {
        if (isBusy()) return
        val msg = base?.messages?.firstOrNull { it.id == messageId } ?: return
        val meta = msg.sceneImage ?: return
        val att = msg.attachments.firstOrNull() ?: return
        viewModelScope.launch {
            val copy = app.attachmentStore.copy(convId, att) ?: return@launch
            val focus = meta.focus.trim()
            val text =
                if (focus.isBlank()) "[User has shared an image]"
                else "[User has shared an image of $focus]"
            sendInternal(text, listOf(copy))
        }
    }

    /**
     * Copy a scene image out of app-private storage into the device's
     * Pictures/PrivateAI, where other apps (and the system gallery) can see it.
     * [onResult] gets the message to show; on API 26–28 the caller must already hold
     * WRITE_EXTERNAL_STORAGE.
     */
    fun saveSceneImage(messageId: Long, onResult: (String) -> Unit) {
        val msg = base?.messages?.firstOrNull { it.id == messageId }
        val att = msg?.attachments?.firstOrNull()
        if (att == null) {
            onResult("No image to save")
            return
        }
        viewModelScope.launch {
            val file = app.attachmentStore.fileFor(convId, att)
            app.galleryRepository.store.exportToMediaStore(file, att.fileName, att.mimeType)
                .onSuccess { onResult("Saved to Pictures/PrivateAI") }
                .onFailure { onResult("Save failed: ${it.message}") }
        }
    }

    /** Remove a scene-image message: cancel any work still in flight for it and delete
     *  its image files. */
    fun deleteSceneMessage(messageId: Long) {
        val conv = base ?: return
        val msg = conv.messages.firstOrNull { it.id == messageId } ?: return
        val atts = msg.attachments
        val jobId = msg.sceneImage?.jobId ?: -1L
        val updated = conv.copy(
            updatedAt = System.currentTimeMillis(),
            messages = conv.messages.filterNot { it.id == messageId },
        )
        local.update { it.copy(selectedMsgId = null) }
        SceneImageService.cancel(app, messageId)
        if (jobId >= 0) ComfyGenerationService.cancelJob(app, jobId)
        if (atts.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { app.attachmentStore.delete(convId, atts) }
        }
        saveThen(updated)
    }

    /** Fail scene-image placeholders that can no longer make progress after a restart. */
    private suspend fun reconcileStaleScenes() {
        val conv = repo.get(convId) ?: return
        val staleIds = conv.messages.mapNotNull { m ->
            val meta = m.sceneImage ?: return@mapNotNull null
            val stale = when (meta.status) {
                SceneImageMeta.STATUS_DESCRIBING -> m.id !in app.sceneImages.describing.value
                SceneImageMeta.STATUS_GENERATING ->
                    app.comfyJobs.jobs.value.none { it.messageId == m.id && it.convId == convId }
                else -> false
            }
            m.id.takeIf { stale }
        }.toSet()
        if (staleIds.isEmpty()) return
        val fresh = repo.get(convId) ?: return
        val updated = fresh.copy(
            messages = fresh.messages.map { m ->
                val meta = m.sceneImage
                if (m.id in staleIds && meta != null &&
                    (meta.status == SceneImageMeta.STATUS_DESCRIBING || meta.status == SceneImageMeta.STATUS_GENERATING)
                ) {
                    m.copy(sceneImage = meta.copy(status = SceneImageMeta.STATUS_FAILED, error = "Interrupted before completion"))
                } else m
            },
        )
        base = updated
        repo.save(updated)
    }

    /** Stop any scene-image work in flight for this conversation (describe + Comfy jobs). */
    private fun cancelSceneWork() {
        base?.messages?.filter { it.isSceneImage }?.forEach { SceneImageService.cancel(app, it.id) }
        app.comfyJobs.jobs.value
            .filter { it.destination == ComfyJob.DEST_CHAT && it.convId == convId && !it.status.isTerminal }
            .forEach { ComfyGenerationService.cancelJob(app, it.id) }
    }

    // ---- backup ------------------------------------------------------------

    /** Stream the current conversation to the backup file at [uri], off the main thread.
     *  [onDone] reports success on the main thread; false if not loaded yet or the write
     *  failed. Attachment bytes are streamed to disk (see [BackupCodec.encodeTo]) so an
     *  image-heavy conversation doesn't OOM. */
    fun backup(uri: Uri, onDone: (Boolean) -> Unit) {
        val conv = base
        if (conv == null) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use {
                        BackupCodec.encodeTo(it, conv, app.attachmentStore)
                    } ?: error("could not open output stream")
                }.isSuccess
            }
            onDone(ok)
        }
    }

    // ---- chat menu ---------------------------------------------------------


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
        /** Clips shorter than this are treated as accidental mic taps and discarded. */
        private const val MIN_RECORDING_MS = 500L

        fun factory(app: LlamaChatApp, convId: Long) = viewModelFactory {
            initializer { ChatViewModel(convId, app) }
        }
    }
}

