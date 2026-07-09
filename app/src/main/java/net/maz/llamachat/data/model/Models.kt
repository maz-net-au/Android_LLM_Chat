package net.maz.llamachat.data.model

import kotlinx.serialization.Serializable

enum class Role { USER, ASSISTANT }

/**
 * A media file attached to a message. The bytes live in app-private storage
 * (see AttachmentStore) under `attachments/<convId>/<fileName>`; only this
 * metadata is serialized with the message. Backups stay text-only: attachments
 * are stripped on export.
 *
 * ⚠️ Serialized into the Room blob via [ChatMessage] — same compatibility rules:
 * add fields only with defaults; never rename/remove/retype. [kind] is a plain
 * string ("image" | "audio") rather than an enum so unknown future kinds still
 * decode.
 */
@Serializable
data class Attachment(
    val id: Long,
    val kind: String,
    val fileName: String,
    val mimeType: String = "",
    /** Audio only; 0 = unknown. */
    val durationMs: Long = 0L,
) {
    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_AUDIO = "audio"
    }
}

/**
 * Marks a message as an in-chat scene image (see the scene-image feature): the
 * user's [focus], the LLM-written image [prompt], and the ComfyUI job producing
 * it. A non-null value means the message is a scene image — it is rendered as a
 * placeholder/preview and is NEVER sent to llama-server (excluded from every
 * request-builder path and from token counting). The finished image rides on the
 * same message as an ordinary [Attachment] (KIND_IMAGE); it never enters the gallery.
 *
 * ⚠️ Serialized into the Room blob AND the backup via [ChatMessage] — same rules:
 * add fields only with defaults; never rename/remove/retype. [status] is a plain
 * string so unknown future states still decode.
 */
@Serializable
data class SceneImageMeta(
    val focus: String = "",
    /** LLM-generated image description; empty until the describe step finishes. */
    val prompt: String = "",
    /** ComfyJob producing the image; -1 until submitted. */
    val jobId: Long = -1L,
    /** Installed workflow used, so regenerate can reuse it; -1 when unknown. */
    val workflowId: Long = -1L,
    val status: String = STATUS_DESCRIBING,
    val error: String = "",
) {
    companion object {
        const val STATUS_DESCRIBING = "describing"
        const val STATUS_GENERATING = "generating"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * A single message. Assistant messages can carry multiple [variants] (one per
 * "regenerate"); user messages always have exactly one. [activeVariant] selects
 * which variant is currently shown.
 *
 * ⚠️ HANDLE WITH CARE — this type is serialized into the Room blob (ConversationEntity)
 * AND into the backup file format (see [net.maz.llamachat.data.backup.BACKUP_VERSION]).
 * Add fields only with defaults; never rename/remove/retype an existing one. A change
 * that isn't backwards compatible needs a DB migration and/or a backup version bump —
 * STOP and ask the user first.
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
    /** Media sent with this message (images/audio). Default keeps old blobs decoding. */
    val attachments: List<Attachment> = emptyList(),
    /** Non-null marks this as a scene-image message: shown but never sent to the model.
     *  Default keeps old blobs decoding. */
    val sceneImage: SceneImageMeta? = null,
) {
    val text: String get() = variants.getOrElse(activeVariant) { "" }
    val variantCount: Int get() = variants.size

    /** Scene-image messages are rendered locally and excluded from all LLM traffic. */
    val isSceneImage: Boolean get() = sceneImage != null

    fun withText(newText: String): ChatMessage {
        val updated = variants.toMutableList()
        if (updated.isEmpty()) updated.add(newText) else updated[activeVariant] = newText
        return copy(variants = updated)
    }

    fun addVariant(initial: String = ""): ChatMessage =
        copy(variants = variants + initial, activeVariant = variants.size)

    companion object {
        fun user(id: Long, text: String, attachments: List<Attachment> = emptyList()) =
            ChatMessage(id, Role.USER, listOf(text), 0, attachments = attachments)
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
    /** Last exact transcript token count from the server's `/tokenize`, cached so the
     *  context readout can show immediately on reopen without re-hitting the server
     *  (which would force the model to load). 0 = never measured; refreshed after each
     *  generation. Not part of the backup format (derived/ephemeral). */
    val tokenCount: Int = 0,
    /** Last known context window (`n_ctx` from `/props`), cached alongside [tokenCount]
     *  so the "/ limit · %" readout also shows on reopen. 0 = unknown. Same derived/
     *  ephemeral status: not part of the backup format. */
    val contextLimit: Int = 0,
    val messages: List<ChatMessage> = emptyList(),
) {
    companion object {
        /** Reserved id for the launcher's ephemeral "Image to Text" quick chat: a
         *  single scratch conversation that is reset on every launch, hidden from
         *  the conversation list, and stripped of chat-management menu actions.
         *  Must be positive (GenerationService rejects negative ids as missing
         *  extras) yet can't collide with IdGen, which mints from the epoch-millis
         *  clock — values this small are unreachable. */
        const val QUICK_IMAGE_ID = 1L
    }

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
        // Scene images have no text; show a label with the focus instead.
        last.sceneImage?.let { meta ->
            return if (meta.focus.isBlank()) "Scene image" else "Scene image: ${meta.focus}"
        }
        val prefix = if (last.role == Role.USER) "You: " else ""
        val cleaned = last.text
            .replace(Regex("[`*#\\n]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val combined = prefix + cleaned
        return if (combined.length > 54) combined.take(54) + "…" else combined
    }
}
