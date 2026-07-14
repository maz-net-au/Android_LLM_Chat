package net.maz.llamachat.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.maz.llamachat.data.attach.AttachmentStore
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.SamplingOverrides

/**
 * ON-DISK BACKUP FORMAT — read this before changing anything in this file.
 *
 * These types define the file format for exporting/restoring a single conversation
 * (chat menu → "Export conversation", Home → import). They are DELIBERATELY decoupled
 * from the Room entity and the domain model so the file format stays stable and explicit,
 * independent of internal refactors.
 *
 * ⚠️ BACKWARDS COMPATIBILITY — old backup files must keep importing:
 *   - Add new fields ONLY with a default value, so older files (which lack them) still
 *     decode. NEVER rename, remove, or retype an existing field, change its meaning, or
 *     make an existing field required.
 *   - Import is best-effort: [BackupCodec.decode] uses ignoreUnknownKeys plus a default
 *     for every field, so a newer file also loads on an older app (minus unknown fields),
 *     and a partial file loads what it can rather than failing.
 *   - Bump [BACKUP_VERSION] only for a genuinely breaking change, and add an explicit
 *     upgrade path in [BackupCodec.decode].
 *   - If a change here is NOT backwards compatible, STOP and ask the user before proceeding.
 *
 * Note: [BackupConversation] reuses the domain [ChatMessage] and [SamplingOverrides]
 * types (which Room also serializes into its blob). The same compatibility rules apply to
 * those — see the care comments on them in data/model/Models.kt.
 */
const val BACKUP_VERSION = 1

/** The backup envelope: a version stamp plus the conversation payload. */
@Serializable
data class ConversationBackup(
    val version: Int = BACKUP_VERSION,
    /** Wall-clock time the backup was written, for display/debugging. */
    val exportedAt: Long = 0L,
    val conversation: BackupConversation = BackupConversation(),
)

/**
 * A conversation's full state, minus the character definition — only [characterName] is
 * stored, and import checks whether such a character exists. Every field has a default so
 * partial or older files still decode (best-effort import).
 */
@Serializable
data class BackupConversation(
    val id: Long = 0L,
    val title: String = "New chat",
    val characterName: String = "",
    val presetName: String = "Default",
    val model: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val userName: String = "user",
    val sampling: SamplingOverrides = SamplingOverrides(),
    val summary: String = "",
    val messages: List<ChatMessage> = emptyList(),
    /** Full backup: raw attachment bytes, base64-keyed by [net.maz.llamachat.data.model.Attachment.fileName]
     *  (unique within a conversation). Empty for text-only/legacy files, in which case the
     *  attachment metadata still restores but the images/audio won't resolve. */
    val attachmentBytes: Map<String, String> = emptyMap(),
) {
    /** Restore to a domain conversation, keeping the original [id] (overwrite-by-id). */
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        characterName = characterName,
        presetName = presetName,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        userName = userName,
        sampling = sampling,
        summary = summary,
        messages = messages,
    )

    companion object {
        /** Snapshot [c] into the backup format, pulling every attachment's bytes out of
         *  [store] and inlining them as base64 so the file is fully self-contained. */
        fun fromDomain(c: Conversation, store: AttachmentStore): BackupConversation {
            val bytes = LinkedHashMap<String, String>()
            for (m in c.messages) {
                for (att in m.attachments) {
                    store.readBase64(c.id, att)?.let { bytes[att.fileName] = it }
                }
            }
            return BackupConversation(
                id = c.id,
                title = c.title,
                characterName = c.characterName,
                presetName = c.presetName,
                model = c.model,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt,
                userName = c.userName,
                sampling = c.sampling,
                summary = c.summary,
                // Keep the attachment metadata; its bytes ride along in [attachmentBytes].
                messages = c.messages,
                attachmentBytes = bytes,
            )
        }
    }
}

/** Serializes conversations to/from the [ConversationBackup] file format. */
object BackupCodec {

    // ignoreUnknownKeys + per-field defaults make decode best-effort; encodeDefaults so a
    // file is self-describing (every field written); prettyPrint so it's human-editable.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(conv: Conversation, store: AttachmentStore): String = json.encodeToString(
        ConversationBackup(
            exportedAt = System.currentTimeMillis(),
            conversation = BackupConversation.fromDomain(conv, store),
        ),
    )

    /**
     * Best-effort decode. Returns the conversation payload, or null only when the text
     * isn't a usable backup at all. Individual missing/unknown fields are tolerated via
     * defaults; they don't fail the whole import.
     */
    fun decode(text: String): BackupConversation? =
        runCatching { json.decodeFromString<ConversationBackup>(text).conversation }.getOrNull()
}
