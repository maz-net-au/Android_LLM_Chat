package net.maz.llamachat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.SamplingOverrides

private val json = Json { ignoreUnknownKeys = true }

/**
 * One row per conversation. Messages are stored as a serialized JSON blob, which
 * keeps saves atomic and avoids a join table while remaining real persistence.
 *
 * ⚠️ HANDLE WITH CARE — this is the persisted schema. Changing the columns, or the
 * shape of the serialized structures ([messagesJson], [samplingJson], [summary]),
 * affects TWO things at once: the Room DB (needs a version bump + Migration in
 * AppDatabase.kt) AND the on-disk backup format (see [net.maz.llamachat.data.backup]).
 * Only add fields with safe defaults; if a change is NOT backwards compatible with
 * existing databases or backup files, STOP and ask the user first.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val characterName: String,
    val presetName: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val userName: String = "user",
    val samplingJson: String = "{}",
    val summary: String = "",
    val messagesJson: String,
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        characterName = characterName,
        presetName = presetName,
        model = model,
        createdAt = createdAt,
        updatedAt = updatedAt,
        userName = userName,
        sampling = runCatching {
            json.decodeFromString<SamplingOverrides>(samplingJson)
        }.getOrDefault(SamplingOverrides()),
        summary = summary,
        messages = runCatching {
            json.decodeFromString<List<ChatMessage>>(messagesJson)
        }.getOrDefault(emptyList()),
    )

    companion object {
        fun fromDomain(c: Conversation): ConversationEntity = ConversationEntity(
            id = c.id,
            title = c.title,
            characterName = c.characterName,
            presetName = c.presetName,
            model = c.model,
            createdAt = c.createdAt,
            updatedAt = c.updatedAt,
            userName = c.userName,
            samplingJson = json.encodeToString(c.sampling),
            summary = c.summary,
            messagesJson = json.encodeToString(c.messages),
        )
    }
}
