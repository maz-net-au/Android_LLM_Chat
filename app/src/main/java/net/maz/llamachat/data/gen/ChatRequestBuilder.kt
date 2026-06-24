package net.maz.llamachat.data.gen

import net.maz.llamachat.data.SettingsRepository
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.data.net.ApiMessage
import net.maz.llamachat.data.net.ChatRequest

/**
 * Builds the llama-server chat requests for both the assistant [reply] path (used
 * by the [GenerationService]) and the [impersonate] path (used by ChatViewModel to
 * write the user's next turn). Kept here so the transcript framing — the system
 * persona, the "Name:" prefixes and the matching stop sequences — lives in one
 * place.
 */
object ChatRequestBuilder {

    /** Request for generating (or continuing) the assistant message at [assistantIndex]. */
    fun reply(
        conv: Conversation,
        assistantIndex: Int,
        includePartial: Boolean,
        forceContinue: Boolean,
        s: SettingsRepository.Settings,
    ): ChatRequest {
        val out = ArrayList<ApiMessage>()
        systemMessage(conv)?.let { out += it }
        conv.messages.forEachIndexed { i, m ->
            if (i < assistantIndex) out += apiMessage(m.role, m.text)
        }
        if (includePartial) {
            val raw = conv.messages.getOrNull(assistantIndex)?.text.orEmpty()
            val partial = if (forceContinue) raw.trimEnd() else raw
            if (partial.isNotBlank()) out += ApiMessage("assistant", partial)
        }
        return request(
            conv = conv,
            messages = out,
            s = s,
            maxTokens = if (forceContinue) 1000 else null,
            ignoreEos = if (forceContinue) true else null,
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
    fun impersonate(conv: Conversation, s: SettingsRepository.Settings): ChatRequest {
        val out = ArrayList<ApiMessage>()
        systemMessage(conv)?.let { out += it }
        val last = conv.messages.lastIndex
        conv.messages.forEachIndexed { i, m ->
            if (i == last && m.role == Role.ASSISTANT && conv.character.usesNamePrefixes) {
                out += ApiMessage("assistant", m.text.trimEnd() + "\n${conv.userName}: ")
            } else {
                out += apiMessage(m.role, m.text)
            }
        }
        return request(conv, out, s, maxTokens = 1000, ignoreEos = true)
    }

    private fun systemMessage(conv: Conversation): ApiMessage? =
        conv.character.resolvedContext(conv.userName).takeIf { it.isNotBlank() }
            ?.let { ApiMessage("system", it) }

    private fun apiMessage(role: Role, text: String): ApiMessage =
        ApiMessage(if (role == Role.USER) "user" else "assistant", text)

    private fun request(
        conv: Conversation,
        messages: List<ApiMessage>,
        s: SettingsRepository.Settings,
        maxTokens: Int?,
        ignoreEos: Boolean?,
    ): ChatRequest {
        val preset = conv.preset
        return ChatRequest(
            model = conv.model.ifEmpty { s.currentModel },
            messages = messages,
            stream = true,
            stop = if (conv.character.usesNamePrefixes)
                listOf("${conv.userName}:", "${conv.characterName}:") else null,
            maxTokens = maxTokens,
            ignoreEos = ignoreEos,
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
