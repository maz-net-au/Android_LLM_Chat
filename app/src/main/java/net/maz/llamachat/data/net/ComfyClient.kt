package net.maz.llamachat.data.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to the ComfyUI HTTP API (same host as llama-server, separate port).
 * Generation is asynchronous server-side: [queuePrompt] submits a patched
 * workflow graph and returns a prompt id, which the caller polls via [queue] /
 * [history] until the outputs can be fetched with [download].
 */
class ComfyClient {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val jsonMedia = "application/json".toMediaType()

    // Plain request/response calls; downloads stream the body, and a generous
    // read timeout covers slow /view responses for large videos.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base(ip: String, port: String): String {
        val host = ip.trim()
        val p = port.trim()
        return if (p.isEmpty()) "http://$host" else "http://$host:$p"
    }

    /**
     * Upload an input file into ComfyUI's `input/` directory. The endpoint is
     * `/upload/image` (part name `image`) for every media kind — LoadAudio/video
     * loaders read from the same directory. [uploadName] should be unique per
     * job (with `overwrite`, re-submits can't trigger ComfyUI's dedup rename,
     * which would silently diverge from the name patched into the graph).
     */
    suspend fun uploadInput(
        ip: String,
        port: String,
        file: File,
        mime: String,
        uploadName: String,
    ): Result<ComfyUploadResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", uploadName, file.asRequestBody(mime.toMediaType()))
                .addFormDataPart("overwrite", "true")
                .build()
            val request = Request.Builder()
                .url("${base(ip, port)}/upload/image")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Upload failed: HTTP ${resp.code}: ${respBody.take(500)}")
                json.decodeFromString<ComfyUploadResponse>(respBody)
            }
        }.onFailure { Log.w(TAG, "uploadInput failed: ${it.message}") }
    }

    /** Queue a workflow graph for execution; returns the server's prompt id. */
    suspend fun queuePrompt(
        ip: String,
        port: String,
        graph: JsonObject,
        clientId: String,
    ): Result<ComfyPromptResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("prompt", graph)
                put("client_id", clientId)
            }
            val request = Request.Builder()
                .url("${base(ip, port)}/prompt")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                // A rejected prompt returns 400 with node_errors JSON describing
                // exactly which node/input failed validation — surface it whole.
                if (!resp.isSuccessful) error("HTTP ${resp.code}: ${respBody.take(1000)}")
                json.decodeFromString<ComfyPromptResponse>(respBody)
            }
        }.onFailure { Log.w(TAG, "queuePrompt failed: ${it.message}") }
    }

    /** Snapshot of the server's running/pending prompt ids. */
    suspend fun queue(ip: String, port: String): Result<ComfyQueueResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("${base(ip, port)}/queue").get().build()
                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    json.decodeFromString<ComfyQueueResponse>(respBody)
                }
            }.onFailure { Log.w(TAG, "queue failed: ${it.message}") }
        }

    /**
     * Poll a prompt's history entry. `{}` means the server doesn't know the
     * prompt as finished yet (still queued/running — or forgotten, if it's not
     * in [queue] either; the caller distinguishes).
     */
    suspend fun history(ip: String, port: String, promptId: String): Result<ComfyHistoryResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${base(ip, port)}/history/${URLEncoder.encode(promptId, "UTF-8")}")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    parseHistory(json.parseToJsonElement(respBody).jsonObject, promptId)
                }
            }.onFailure { Log.w(TAG, "history failed: ${it.message}") }
        }

    private fun parseHistory(root: JsonObject, promptId: String): ComfyHistoryResult {
        val entry = root[promptId] as? JsonObject ?: return ComfyHistoryResult.Pending
        val status = entry["status"] as? JsonObject
        val statusStr = status?.get("status_str")?.jsonPrimitive?.contentOrNull
        val completed = runCatching { status?.get("completed")?.jsonPrimitive?.boolean }.getOrNull()
        val outputs = entry["outputs"] as? JsonObject
        return when {
            statusStr == "error" -> {
                // messages is [[type, {details}], ...]; the raw tail is more
                // useful than nothing when an execution error has no clean field.
                val detail = status?.get("messages")?.toString()?.take(500)
                ComfyHistoryResult.Error("ComfyUI execution failed${detail?.let { ": $it" } ?: ""}")
            }
            completed == true || (status == null && outputs != null) ->
                ComfyHistoryResult.Completed(outputs ?: JsonObject(emptyMap()))
            else -> ComfyHistoryResult.Pending
        }
    }

    /** Stream a generated file from `/view` into [dest]. */
    suspend fun download(
        ip: String,
        port: String,
        ref: ComfyFileRef,
        dest: File,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            val url = "${base(ip, port)}/view" +
                "?filename=${enc(ref.filename)}&subfolder=${enc(ref.subfolder)}&type=${enc(ref.type)}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
                val body = resp.body ?: error("Download failed: empty body")
                // Stream to disk — generated videos can be far too large to buffer.
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                Unit
            }
        }.onFailure {
            dest.delete() // never leave a truncated file behind
            Log.w(TAG, "download failed: ${it.message}")
        }
    }

    /** Interrupt whatever prompt is currently executing (server-global). */
    suspend fun interrupt(ip: String, port: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${base(ip, port)}/interrupt")
                    .post(ByteArray(0).toRequestBody(null))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                }
            }.onFailure { Log.w(TAG, "interrupt failed: ${it.message}") }
        }

    /** Remove a still-pending prompt from the server queue. */
    suspend fun deleteQueued(ip: String, port: String, promptId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("delete", buildJsonArray { add(promptId) })
                }
                val request = Request.Builder()
                    .url("${base(ip, port)}/queue")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                }
            }.onFailure { Log.w(TAG, "deleteQueued failed: ${it.message}") }
        }

    private companion object {
        const val TAG = "ComfyClient"
    }
}
