package net.maz.llamachat.data.model

import kotlinx.serialization.Serializable

enum class Role { USER, ASSISTANT }

/**
 * A single message. Assistant messages can carry multiple [variants] (one per
 * "regenerate"); user messages always have exactly one. [activeVariant] selects
 * which variant is currently shown.
 */
@Serializable
data class ChatMessage(
    val id: Long,
    val role: Role,
    val variants: List<String> = listOf(""),
    val activeVariant: Int = 0,
    /** Frozen once folded into the conversation [Conversation.summary]: still shown,
     *  but no longer editable/deletable and no longer sent to the model (the summary
     *  stands in for it). Older messages are always locked as a prefix. */
    val locked: Boolean = false,
) {
    val text: String get() = variants.getOrElse(activeVariant) { "" }
    val variantCount: Int get() = variants.size

    fun withText(newText: String): ChatMessage {
        val updated = variants.toMutableList()
        if (updated.isEmpty()) updated.add(newText) else updated[activeVariant] = newText
        return copy(variants = updated)
    }

    fun addVariant(initial: String = ""): ChatMessage =
        copy(variants = variants + initial, activeVariant = variants.size)

    companion object {
        fun user(id: Long, text: String) = ChatMessage(id, Role.USER, listOf(text), 0)
        fun assistant(id: Long, text: String = "") = ChatMessage(id, Role.ASSISTANT, listOf(text), 0)
    }
}

/** A conversation and its messages. Mirrors the prototype's conversation object. */
data class Conversation(
    val id: Long,
    val title: String,
    val characterName: String,
    val presetName: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Name substituted for `{{user}}` in this conversation's prompts/greeting. */
    val userName: String = "user",
    /** Per-chat sampling tweaks layered over [presetName]; empty = pure preset. */
    val sampling: SamplingOverrides = SamplingOverrides(),
    /** Running summary of the locked (older) part of the chat. When set, it replaces
     *  those messages in every request, letting the conversation continue past the
     *  model's context window. Editable in the chat-details screen. */
    val summary: String = "",
    val messages: List<ChatMessage> = emptyList(),
) {
    val character get() = Catalog.character(characterName)
    val preset get() = Catalog.preset(presetName)

    /** The [presetName] template with this conversation's [sampling] overrides
     *  applied — what actually gets sent to llama-server. */
    val effectivePreset: Preset get() = preset.let { p ->
        p.copy(
            temperature = sampling.temperature ?: p.temperature,
            topP = sampling.topP ?: p.topP,
            topK = sampling.topK ?: p.topK,
            minP = sampling.minP ?: p.minP,
            typicalP = sampling.typicalP ?: p.typicalP,
            repeatPenalty = sampling.repeatPenalty ?: p.repeatPenalty,
            repeatLastN = sampling.repeatLastN ?: p.repeatLastN,
            presencePenalty = sampling.presencePenalty ?: p.presencePenalty,
            frequencyPenalty = sampling.frequencyPenalty ?: p.frequencyPenalty,
            mirostat = sampling.mirostat ?: p.mirostat,
            mirostatTau = sampling.mirostatTau ?: p.mirostatTau,
            mirostatEta = sampling.mirostatEta ?: p.mirostatEta,
        )
    }

    /** One-line preview for the conversation list, matching the prototype. */
    fun preview(): String {
        val last = messages.lastOrNull() ?: return "No messages yet"
        val prefix = if (last.role == Role.USER) "You: " else ""
        val cleaned = last.text
            .replace(Regex("[`*#\\n]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val combined = prefix + cleaned
        return if (combined.length > 54) combined.take(54) + "…" else combined
    }
}
