package net.maz.llamachat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.maz.llamachat.data.db.ConversationDao
import net.maz.llamachat.data.db.ConversationEntity
import net.maz.llamachat.data.model.Conversation

/** CRUD over conversations. A fresh install starts with no conversations. */
class ConversationRepository(private val dao: ConversationDao) {

    /** All listable conversations. The ephemeral "Image to Text" scratch chat is
     *  stored like any other (the generation pipeline needs it in Room) but never
     *  shown in the list. */
    val conversations: Flow<List<Conversation>> =
        dao.observeAll().map { list ->
            list.filter { it.id != Conversation.QUICK_IMAGE_ID }.map { it.toDomain() }
        }

    suspend fun get(id: Long): Conversation? = dao.getById(id)?.toDomain()

    /** Observe a single conversation, re-emitting on every write (incl. streamed
     *  checkpoints from the generation service). Emits null once it's deleted. */
    fun observe(id: Long): Flow<Conversation?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun save(conversation: Conversation) {
        dao.upsert(ConversationEntity.fromDomain(conversation))
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
