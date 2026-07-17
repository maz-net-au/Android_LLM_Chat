package net.maz.llamachat.data.gallery

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.comfy.JobInput
import net.maz.llamachat.data.db.GalleryItemEntity
import java.io.File

/**
 * Owns the bytes behind gallery items: app-private files under
 * `filesDir/gallery/`. Rows in the `gallery_items` table only carry metadata.
 * [exportToMediaStore] copies a file out into the system collections
 * (Pictures/Movies/Music) so other apps can see it.
 */
class GalleryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(): File = File(context.filesDir, "gallery").apply { mkdirs() }

    fun fileFor(item: GalleryItemEntity): File = File(dir(), item.fileName)

    /**
     * Build the row + destination file for a generated output named
     * [comfyFilename] on the server. The caller downloads into the file first
     * and only then inserts the row, so a failed download leaves no entry.
     */
    fun newItem(
        flowType: String,
        workflowName: String,
        comfyFilename: String,
        workflowId: Long = -1L,
        inputs: List<JobInput> = emptyList(),
    ): Pair<GalleryItemEntity, File> {
        val id = IdGen.next()
        val ext = comfyFilename.substringAfterLast('.', "").lowercase()
        val item = GalleryItemEntity(
            id = id,
            flowType = flowType,
            workflowName = workflowName,
            createdAt = System.currentTimeMillis(),
            fileName = if (ext.isEmpty()) "$id" else "$id.$ext",
            mimeType = MIME_BY_EXT[ext] ?: "application/octet-stream",
            workflowId = workflowId,
            inputsJson = json.encodeToString(inputs),
        )
        return item to fileFor(item)
    }

    fun delete(item: GalleryItemEntity) {
        fileFor(item).delete()
    }

    /**
     * Copy the item's file into the matching public collection
     * (Pictures/Movies/Music, subfolder [EXPORT_DIR]). On API 29+ this needs no
     * permission; on 26–28 the caller must already hold WRITE_EXTERNAL_STORAGE.
     */
    suspend fun exportToMediaStore(item: GalleryItemEntity): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = fileFor(item)
                if (!src.exists()) error("File is missing")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportModern(item, src)
                else exportLegacy(item, src)
            }
        }

    private fun exportModern(item: GalleryItemEntity, src: File): Uri {
        val (collection, dirName) = collectionFor(item.mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirName/$EXPORT_DIR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("MediaStore rejected the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open the destination for writing")
        } catch (t: Throwable) {
            resolver.delete(uri, null, null) // don't leave a pending ghost entry
            throw t
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Suppress("DEPRECATION") // pre-Q export path: DATA + public directories
    private fun exportLegacy(item: GalleryItemEntity, src: File): Uri {
        val (collection, dirName) = collectionFor(item.mimeType)
        val destDir = File(Environment.getExternalStoragePublicDirectory(dirName), EXPORT_DIR)
            .apply { mkdirs() }
        val dest = File(destDir, item.fileName)
        src.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.DATA, dest.absolutePath)
        }
        return context.contentResolver.insert(collection, values)
            ?: error("MediaStore rejected the file")
    }

    /** MediaStore collection + public directory name for a mime type. */
    private fun collectionFor(mime: String): Pair<Uri, String> = when {
        mime.startsWith("image/") ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        mime.startsWith("video/") ->
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
        mime.startsWith("audio/") ->
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
        else ->
            MediaStore.Files.getContentUri("external") to Environment.DIRECTORY_DOWNLOADS
    }

    private companion object {
        const val EXPORT_DIR = "PrivateAI"

        /** MimeTypeMap misses several of these (notably flac/webm audio), so the
         *  formats ComfyUI actually emits are mapped by hand. */
        val MIME_BY_EXT = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "mp4" to "video/mp4",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "flac" to "audio/flac",
            "mp3" to "audio/mpeg",
            "wav" to "audio/wav",
            "ogg" to "audio/ogg",
        )
    }
}
