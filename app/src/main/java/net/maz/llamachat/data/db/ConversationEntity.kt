package net.maz.llamachat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation

private val json = Json { ignoreUnknownKeys = true }

/**
 * One row per conversation. Messages are stored as a serialized JSON blob, which
 * keeps saves atomic and avoids a join table while remaining real persistence.
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
            messagesJson = json.encodeToString(c.messages),
        )
    }
}
