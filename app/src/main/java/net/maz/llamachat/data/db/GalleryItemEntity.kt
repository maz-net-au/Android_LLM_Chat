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
    /** Installed workflow that produced this; -1 = unknown (can't regenerate).
     *  A plain id snapshot: if the workflow is later removed, regeneration just
     *  fails gracefully — the row is never orphaned. */
    val workflowId: Long = -1L,
    /** The request that generated this file: a JSON-encoded `List<JobInput>`
     *  (the scalar form values), so the prompt is viewable long after the
     *  producing job is gone and the form can reopen pre-filled. */
    val inputsJson: String = "[]",
)
