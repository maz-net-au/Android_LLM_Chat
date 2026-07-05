package net.maz.llamachat.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One generated media file. Metadata only — the bytes live app-privately at
 * `filesDir/gallery/<fileName>` (owned by GalleryStore). [workflowName] is a
 * plain string snapshot, so deleting a workflow never orphans gallery rows.
 */
@Entity(tableName = "gallery_items")
data class GalleryItemEntity(
    @PrimaryKey val id: Long,
    /** FlowType.key of the flow that produced this (gallery tab grouping). */
    val flowType: String,
    val workflowName: String,
    val createdAt: Long,
    /** File name under `filesDir/gallery/`. */
    val fileName: String,
    /** e.g. image/png, video/mp4, audio/flac — routes viewer and export. */
    val mimeType: String,
)
