package net.maz.llamachat.data.backup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.maz.llamachat.data.attach.AttachmentStore
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.SamplingOverrides
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream

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
 *   - Import is best-effort: [BackupCodec.decodeFrom] uses ignoreUnknownKeys plus a default
 *     for every field, so a newer file also loads on an older app (minus unknown fields),
 *     and a partial file loads what it can rather than failing.
 *   - Bump [BACKUP_VERSION] only for a genuinely breaking change, and add an explicit
 *     upgrade path in [BackupCodec.decodeFrom].
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
     *  attachment metadata still restores but the images/audio won't resolve.
     *
     *  On EXPORT this map is populated straight from the file rather than into a
     *  [BackupConversation]: [BackupCodec.encodeTo] serializes the metadata with this left
     *  empty and streams the base64 in afterwards, so a conversation full of images is never
     *  held in memory all at once (it used to OOM). On IMPORT it is decoded normally. */
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
        /** Snapshot [c]'s metadata (everything but the attachment bytes) into the backup
         *  format. The bytes are streamed in separately by [BackupCodec.encodeTo] to keep
         *  peak memory bounded, so [attachmentBytes] is deliberately left empty here. */
        fun metadataOnly(c: Conversation): BackupConversation = BackupConversation(
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
            // Keep the attachment metadata; its bytes are streamed in by encodeTo.
            messages = c.messages,
        )
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

    /**
     * Write [conv]'s backup to [out] (same on-disk JSON as before), streaming attachment
     * bytes straight from disk so a conversation full of images never sits in memory all at
     * once — the old string-building [encode] OOM-ed on large exports.
     *
     * Strategy: serialize everything EXCEPT the attachment bytes to a small in-memory string
     * (metadata + message text only), which ends in an empty `"attachmentBytes": {}` object;
     * then split that object open and stream each attachment's base64 between the braces.
     * [out] is not closed (the caller owns it).
     */
    fun encodeTo(out: OutputStream, conv: Conversation, store: AttachmentStore) {
        val skeleton = json.encodeToString(
            ConversationBackup(
                exportedAt = System.currentTimeMillis(),
                conversation = BackupConversation.metadataOnly(conv),
            ),
        )
        // Locate the empty attachmentBytes object and split it into `{` + `}`. It is the
        // last field, so lastIndexOf can't be fooled by a message that mentions the key.
        val keyAt = skeleton.lastIndexOf("\"attachmentBytes\"")
        val open = skeleton.indexOf('{', keyAt)
        val close = skeleton.indexOf('}', open)

        val sink = BufferedOutputStream(out)
        sink.write(skeleton.substring(0, open + 1).encodeToByteArray())

        val seen = HashSet<String>()
        var first = true
        for (m in conv.messages) {
            for (att in m.attachments) {
                if (!store.hasFile(conv.id, att) || !seen.add(att.fileName)) continue
                sink.write((if (first) "\n" else ",\n").encodeToByteArray())
                first = false
                // Reuse the serializer for the key so the file name is correctly quoted.
                sink.write(json.encodeToString(att.fileName).encodeToByteArray())
                sink.write(": \"".encodeToByteArray())
                store.streamBase64Into(conv.id, att, sink)
                sink.write("\"".encodeToByteArray())
            }
        }
        if (!first) sink.write("\n".encodeToByteArray())
        sink.write(skeleton.substring(close).encodeToByteArray())
        sink.flush()
    }

    /**
     * Best-effort decode from [input] (streamed, so the whole file isn't first read into a
     * String). Returns the conversation payload, or null only when the input isn't a usable
     * backup at all. Individual missing/unknown fields are tolerated via defaults; they don't
     * fail the whole import. [input] is not closed (the caller owns it).
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decodeFrom(input: InputStream): BackupConversation? =
        runCatching { json.decodeFromStream<ConversationBackup>(input).conversation }.getOrNull()
}
