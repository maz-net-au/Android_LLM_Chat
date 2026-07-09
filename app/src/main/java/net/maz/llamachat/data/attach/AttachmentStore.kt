package net.maz.llamachat.data.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.model.Attachment
import net.maz.llamachat.data.net.ImageUrl
import net.maz.llamachat.data.net.ImageUrlPart
import net.maz.llamachat.data.net.InputAudio
import net.maz.llamachat.data.net.InputAudioPart
import java.io.File
import java.util.Base64

/**
 * Owns the bytes behind [Attachment]s: app-private files under
 * `filesDir/attachments/<convId>/`. Messages only carry the metadata; requests
 * pull the bytes back out via [toContentPart] at build time.
 */
class AttachmentStore(private val context: Context) {

    private val json = Json

    fun dirFor(convId: Long): File =
        File(File(context.filesDir, "attachments"), convId.toString()).apply { mkdirs() }

    fun fileFor(convId: Long, att: Attachment): File = File(dirFor(convId), att.fileName)

    /** Fresh target file for a WAV recording; pairs with the [Attachment] built on stop. */
    fun newAudioFile(convId: Long, id: Long): File = File(dirFor(convId), "$id.wav")

    /** Fresh target file for a downloaded image (e.g. a scene image); pairs with the
     *  [Attachment] built once the bytes are written. */
    fun newImageFile(convId: Long, id: Long, ext: String): File =
        File(dirFor(convId), if (ext.isEmpty()) "$id" else "$id.$ext")

    /**
     * Copy the image at [uri] into the conversation's attachment dir, downscaled
     * to at most [MAX_DIMENSION_PX] on the long side and re-encoded as JPEG —
     * camera photos are 12MP+, which would blow out the request size and the
     * model's context. EXIF rotation is baked in. Returns null if the source
     * can't be read.
     */
    suspend fun importImage(convId: Long, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            // Pass 1: bounds only, to pick a power-of-two subsample for the real decode.
            // decodeStream always returns null in bounds-only mode; success shows up
            // in outWidth/outHeight instead.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (resolver.openInputStream(uri) ?: return@runCatching null)
                .use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION_PX) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val scale = MAX_DIMENSION_PX.toFloat() / maxOf(decoded.width, decoded.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else decoded
            val rotated = if (rotation != 0f) {
                Bitmap.createBitmap(
                    scaled, 0, 0, scaled.width, scaled.height,
                    Matrix().apply { postRotate(rotation) }, true,
                )
            } else scaled

            val id = IdGen.next()
            val att = Attachment(id, Attachment.KIND_IMAGE, "$id.jpg", "image/jpeg")
            fileFor(convId, att).outputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            att
        }.getOrNull()
    }

    /** Delete the files behind [atts] (metadata stays with the message's owner). */
    fun delete(convId: Long, atts: List<Attachment>) {
        atts.forEach { fileFor(convId, it).delete() }
    }

    /** Remove every attachment file for a cleared/deleted conversation. */
    fun deleteAll(convId: Long) {
        File(File(context.filesDir, "attachments"), convId.toString()).deleteRecursively()
    }

    /**
     * The OpenAI content part for [att]: a base64 `data:` URI for images, an
     * `input_audio` part for audio. Null when the file is missing (e.g. an
     * imported backup that never carried the bytes).
     */
    fun toContentPart(convId: Long, att: Attachment): JsonElement? {
        val file = fileFor(convId, att)
        if (!file.exists()) return null
        val b64 = Base64.getEncoder().encodeToString(file.readBytes())
        return when (att.kind) {
            Attachment.KIND_IMAGE -> {
                val mime = att.mimeType.ifEmpty { "image/jpeg" }
                json.encodeToJsonElement(ImageUrlPart(imageUrl = ImageUrl("data:$mime;base64,$b64")))
            }
            Attachment.KIND_AUDIO ->
                json.encodeToJsonElement(InputAudioPart(inputAudio = InputAudio(data = b64, format = "wav")))
            else -> null
        }
    }

    private companion object {
        const val MAX_DIMENSION_PX = 1024
        const val JPEG_QUALITY = 85
    }
}
