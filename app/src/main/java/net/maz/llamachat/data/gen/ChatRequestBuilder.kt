package net.maz.llamachat.data.gen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.data.net.ApiMessage
import net.maz.llamachat.data.net.ChatRequest
import net.maz.llamachat.data.net.TextPart
import net.maz.llamachat.data.net.apiParts
import net.maz.llamachat.data.net.apiText

/**
 * Builds the llama-server chat requests for both the assistant [reply] path (used
 * by the [GenerationService]) and the [impersonate] path (used by ChatViewModel to
 * write the user's next turn). Kept here so the transcript framing — the system
 * persona, the "Name:" prefixes and the matching stop sequences — lives in one
 * place.
 */
object ChatRequestBuilder {

    /** No-attachment default so text-only callers (and tests) stay unchanged. */
    val NO_ATTACHMENTS: (ChatMessage) -> List<JsonElement> = { emptyList() }

    /**
     * Request for generating (or continuing) the assistant message at [assistantIndex].
     * [attachmentPart] resolves a message's attachments to OpenAI content parts
     * (a lambda so this object stays free of Context/file dependencies); it's applied
     * to every unlocked history message — llama-server supports multimodal history.
     */
    fun reply(
        conv: Conversation,
        assistantIndex: Int,
        includePartial: Boolean,
        forceContinue: Boolean,
        s: SettingsRepository.Settings,
        attachmentPart: (ChatMessage) -> List<JsonElement> = NO_ATTACHMENTS,
    ): ChatRequest {
        val out = ArrayList<ApiMessage>()
        systemMessage(conv)?.let { out += it }
        conv.messages.forEachIndexed { i, m ->
            // Locked messages are represented by the summary in [systemMessage], not resent.
            // Scene images are local-only and never reach the model.
            if (i < assistantIndex && !m.locked && !m.isSceneImage) out += apiMessage(m, attachmentPart)
        }
        if (includePartial) {
            // Sent verbatim, including any trailing space, so the model continues from
            // exactly where the text left off rather than fusing onto the last word.
            val partial = conv.messages.getOrNull(assistantIndex)?.text.orEmpty()
            if (partial.isNotBlank()) out += apiText("assistant", partial)
        }
        return request(
            conv = conv,
            messages = out,
            s = s,
            // Bound a continue so it can't run away; EOS is respected so it stops naturally.
            maxTokens = if (forceContinue) 1000 else null,
        )
    }

    /**
     * Request that *suggests* the user's next line, the chat-completions
     * equivalent of text-generation-webui's "Impersonate" (which appends "You:"
     * to its raw prompt and continues). llama-server only continues a trailing
     * *assistant* message, so we fold the user's "Name:" prefix onto the final
     * assistant turn and continue it: the model writes the user's line as a
     * transcript continuation, stopping at the character's "Name:". Only
     * meaningful in transcript mode (a character with name prefixes); the streamed
     * text goes into the input box for the user to send as their own turn.
     */
    fun impersonate(
        conv: Conversation,
        s: SettingsRepository.Settings,
        attachmentPart: (ChatMessage) -> List<JsonElement> = NO_ATTACHMENTS,
    ): ChatRequest {
        val out = ArrayList<ApiMessage>()
        systemMessage(conv)?.let { out += it }
        val last = conv.messages.lastIndex
        conv.messages.forEachIndexed { i, m ->
            if (m.locked) return@forEachIndexed // folded into the summary; not resent
            if (m.isSceneImage) return@forEachIndexed // local-only; never sent to the model
            if (i == last && m.role == Role.ASSISTANT && conv.character.usesNamePrefixes) {
                // No trailing space after the "Name:" prefix: ending the prompt on a
                // lone space token makes the model predict end-of-turn immediately
                // (an instant EOS). Without it the next token starts a fresh word and
                // generation proceeds; the VM trims the leading space off the stream.
                out += apiText("assistant", m.text.trimEnd() + "\n${conv.userName}:")
            } else {
                out += apiMessage(m, attachmentPart)
            }
        }
        return request(conv, out, s, maxTokens = 1000)
    }

    /**
     * The whole conversation as one string for token counting via `/tokenize`:
     * the system persona (with any summary) followed by every unlocked message's
     * stored text. This mirrors the content [reply] would send (minus the chat
     * template's per-message role framing, which the server adds), so the readout
     * reflects the reduced context once older messages have been summarized.
     * Text only: image/audio attachments also consume context but /tokenize can't
     * measure them, so the context meter undercounts for multimodal chats.
     */
    fun transcriptForCount(conv: Conversation): String = buildString {
        systemContent(conv).takeIf { it.isNotBlank() }?.let { appendLine(it) }
        conv.messages.forEach { if (!it.locked && !it.isSceneImage) appendLine(it.text) }
    }

    /**
     * Build the request that compacts the older part of [conv] into a fresh summary.
     * Fold strategy: the prior summary (if any) plus the messages being locked now —
     * the unlocked messages at index < [keepFrom] — with `<think>` reasoning stripped.
     */
    fun summarize(conv: Conversation, keepFrom: Int, s: SettingsRepository.Settings): ChatRequest {
        val userTurn = buildString {
            if (conv.summary.isNotBlank()) {
                appendLine(SummarizationConfig.PREVIOUS_SUMMARY_LABEL)
                appendLine(conv.summary)
                appendLine()
                appendLine("New messages since then:")
            }
            conv.messages.take(keepFrom).forEach { m ->
                if (!m.locked && !m.isSceneImage) appendLine(stripThink(m.text))
            }
            appendLine()
            append(SummarizationConfig.FOLD_INSTRUCTION)
        }
        val messages = listOf(
            apiText("system", SummarizationConfig.systemPrompt(conv.character.usesNamePrefixes)),
            apiText("user", userTurn),
        )
        val preset = SummarizationConfig.sampling
        return ChatRequest(
            model = conv.model.ifEmpty { s.currentModel },
            messages = messages,
            stream = true,
            stop = null,
            maxTokens = SummarizationConfig.MAX_SUMMARY_TOKENS,
            temperature = preset.temperature,
            topP = preset.topP,
            topK = preset.topK,
            repeatPenalty = preset.repeatPenalty,
        )
    }

    /** The system persona plus, when present, the running summary of older messages. */
    private fun systemContent(conv: Conversation): String {
        val context = conv.character.resolvedContext(conv.userName)
        val summary = conv.summary.takeIf { it.isNotBlank() }
            ?.let { "## Story so far\n$it" }
        return listOfNotNull(context.takeIf { it.isNotBlank() }, summary).joinToString("\n\n")
    }

    private fun systemMessage(conv: Conversation): ApiMessage? =
        systemContent(conv).takeIf { it.isNotBlank() }?.let { apiText("system", it) }

    /** Text-only messages keep the plain-string content (wire format unchanged);
     *  attachments switch the message to a content-parts array: media first, then
     *  the text part when non-blank. */
    private fun apiMessage(m: ChatMessage, attachmentPart: (ChatMessage) -> List<JsonElement>): ApiMessage {
        val role = if (m.role == Role.USER) "user" else "assistant"
        val media = if (m.attachments.isEmpty()) emptyList() else attachmentPart(m)
        if (media.isEmpty()) return apiText(role, m.text)
        val textPart = m.text.takeIf { it.isNotBlank() }
            ?.let { Json.encodeToJsonElement(TextPart(text = it)) }
        return apiParts(role, media + listOfNotNull(textPart))
    }

    private fun request(
        conv: Conversation,
        messages: List<ApiMessage>,
        s: SettingsRepository.Settings,
        maxTokens: Int?,
    ): ChatRequest {
        val preset = conv.effectivePreset
        return ChatRequest(
            model = conv.model.ifEmpty { s.currentModel },
            messages = messages,
            stream = true,
            stop = if (conv.character.usesNamePrefixes)
                listOf("${conv.userName}:", "${conv.characterName}:") else null,
            maxTokens = maxTokens,
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
    }
}
