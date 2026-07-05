package net.maz.llamachat.data

import kotlinx.coroutines.flow.Flow
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.db.GalleryDao
import net.maz.llamachat.data.db.GalleryItemEntity
import net.maz.llamachat.data.gallery.GalleryStore

/**
 * Generated-media gallery: metadata rows in Room, bytes in [GalleryStore].
 * The generation service inserts; the gallery/viewer screens observe, export,
 * and delete.
 */
class GalleryRepository(
    private val dao: GalleryDao,
    val store: GalleryStore,
) {
    fun observe(flowType: FlowType? = null): Flow<List<GalleryItemEntity>> =
        if (flowType == null) dao.observeAll() else dao.observeByFlow(flowType.key)

    suspend fun getById(id: Long): GalleryItemEntity? = dao.getById(id)

    /** Record a downloaded file; the bytes must already be in place. */
    suspend fun insert(item: GalleryItemEntity) = dao.insert(item)

    /** Remove the row and the file behind it. */
    suspend fun delete(item: GalleryItemEntity) {
        dao.delete(item.id)
        store.delete(item)
    }
}
