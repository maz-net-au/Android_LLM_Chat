package net.maz.llamachat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {

    @Query("SELECT * FROM gallery_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GalleryItemEntity>>

    @Query("SELECT * FROM gallery_items WHERE flowType = :flowType ORDER BY createdAt DESC")
    fun observeByFlow(flowType: String): Flow<List<GalleryItemEntity>>

    @Query("SELECT * FROM gallery_items WHERE id = :id")
    suspend fun getById(id: Long): GalleryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GalleryItemEntity)

    @Query("DELETE FROM gallery_items WHERE id = :id")
    suspend fun delete(id: Long)
}
