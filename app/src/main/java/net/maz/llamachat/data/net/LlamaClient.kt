package net.maz.llamachat.data.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import android.util.Log
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks to a user-supplied llama-server over its OpenAI-compatible HTTP API.
 *  - [listModels] doubles as the connection check (used by the Connect screen).
 *  - [streamChat] streams assistant tokens via Server-Sent Events; collecting
 *    the returned Flow performs the request, and cancelling collection cancels
 *    the in-flight generation (this is how Stop / navigating away work).
 */
class LlamaClient {

    // explicitNulls = false omits unset (null) sampler params so the server uses
    // its own defaults; encodeDefaults keeps non-null defaults like stream = true.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val jsonMedia = "application/json".toMediaType()

    // Generation can pause between tokens, so the read timeout is disabled for
    // streaming. Connect/call timeouts still bound how long we wait to start.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun base(ip: String, port: String): String {
        val host = ip.trim()
        val p = port.trim()
        return if (p.isEmpty()) "http://$host" else "http://$host:$p"
    }

    /** Fetch the server's model list. Success implies a reachable server. */
    suspend fun listModels(ip: String, port: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${base(ip, port)}/v1/models")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("Server returned HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    val parsed = json.decodeFromString<ModelsResponse>(body)
                    parsed.data.map { it.id }
                }
            }
        }

    /**
     * Fetch the models the server currently has loaded (or is loading) from
     * `/models`, then unload each in turn via `POST /models/unload`
     * (`{"model": name}`). Returns how many were unloaded (0 if none were loaded).
     */
    suspend fun unloadAllModels(ip: String, port: String): Result<Int> = runCatching {
        val models = loadedModels(ip, port).getOrThrow()
        var unloaded = 0
        for (model in models) {
            unloadModel(ip, port, model).onSuccess { unloaded++ }
        }
        unloaded
    }

    private suspend fun loadedModels(ip: String, port: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url("${base(ip, port)}/models").get().build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${body.take(300)}")
                    parseModelIds(json.parseToJsonElement(body))
                }
            }.onFailure { Log.w("LlamaClient", "loadedModels failed: ${it.message}") }
        }

    private suspend fun unloadModel(ip: String, port: String, model: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("model", model) }
                val request = Request.Builder()
                    .url("${base(ip, port)}/models/unload")
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(300)}")
                }
            }.onFailure { Log.w("LlamaClient", "unloadModel('$model') failed: ${it.message}") }
        }

    /** Pull the ids of models worth unloading out of `/models`. Tolerates OpenAI-style
     *  `{data:[{id}]}`, a bare array of objects (`id`/`model`/`name`), or plain strings.
     *  When an entry carries a `status.value`, models the server reports as fully
     *  `unloaded` are skipped — only loaded / loading ones are returned. */
    private fun parseModelIds(el: JsonElement): List<String> {
        val arr = (el as? JsonObject)?.get("data") as? JsonArray ?: el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> {
                    val id = (item["id"] ?: item["model"] ?: item["name"])
                        .let { it as? JsonPrimitive }?.contentOrNull
                    val state = (item["status"] as? JsonObject)?.get("value")
                        .let { it as? JsonPrimitive }?.contentOrNull
                    if (state != null && state.equals("unloaded", ignoreCase = true)) null else id
                }
                else -> null
            }
        }.filter { it.isNotBlank() }.distinct()
    }

    /** Fetch the server's context window size (`n_ctx`) from `/props`, used to show
     *  how full the current conversation is. [model] is appended as `?model=` so a
     *  multi-model router (e.g. llama-swap) proxies to the right backend — without it
     *  the router reports its own `n_ctx: 0`. Fails quietly (caller treats a failure
     *  as "limit unknown" and just omits the percentage). */
    suspend fun fetchContextSize(ip: String, port: String, model: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${base(ip, port)}/props".let {
                    if (model.isEmpty()) it
                    else "$it?model=${URLEncoder.encode(model, "UTF-8")}"
                }
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${body.take(500)}")
                    }
                    // A non-positive n_ctx means the router didn't report a usable
                    // window (e.g. no model loaded, or props scoped to a different
                    // backend). Log the body so the actual shape is diagnosable, and
                    // treat it as "unknown" rather than a bogus 0.
                    json.decodeFromString<PropsResponse>(body).contextSize?.takeIf { it > 0 }
                        ?: error("no usable n_ctx in /props: ${body.take(500)}")
                }
            }.onFailure { Log.w("LlamaClient", "fetchContextSize failed: ${it.message}") }
        }

    /** Count the tokens in [content] using the server's own tokenizer (`/tokenize`),
     *  so the context readout is exact for the loaded model. Fails quietly (the error
     *  is logged with the server's response body so a rejection is diagnosable). */
    suspend fun countTokens(ip: String, port: String, content: String, model: String): Result<Int> =
        withContext(Dispatchers.IO) {
            // Empty content has nothing to tokenize; skip the round-trip (and avoid
            // sending a body some server builds reject) and report zero.
            if (content.isBlank()) return@withContext Result.success(0)
            runCatching {
                val request = Request.Builder()
                    .url("${base(ip, port)}/tokenize")
                    .post(json.encodeToString(TokenizeRequest(content, model.ifEmpty { null })).toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}: ${body.take(500)}")
                    }
                    json.decodeFromString<TokenizeResponse>(body).tokens.size
                }
            }.onFailure { Log.w("LlamaClient", "countTokens failed: ${it.message}") }
        }

    /**
     * Stream a chat completion. Emits incremental text deltas; the flow
     * completes when the server sends the `[DONE]` sentinel and fails (throws)
     * on transport or server errors.
     */
    fun streamChat(ip: String, port: String, body: ChatRequest): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url("${base(ip, port)}/v1/chat/completions")
            .post(json.encodeToString(body).toRequestBody(jsonMedia))
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            // Whether an emitted `<think>` is still open (server is streaming reasoning
            // via reasoning_content). Reconstituting the tags around the separated field
            // lets the reasoning stream to the existing think-block UI. Touched only from
            // the single SSE callback thread, so no synchronization needed.
            private var thinkOpen = false

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data == "[DONE]") {
                    finishThink()
                    close()
                    return
                }
                val delta = runCatching {
                    json.decodeFromString<ChatChunk>(data).choices.firstOrNull()?.delta
                }.getOrNull() ?: return
                // Wrap any reasoning_content in `<think>…</think>`; pass content through
                // as-is (it may already carry literal tags if the server isn't parsing
                // reasoning). A delta can, at the transition, carry both.
                val out = StringBuilder()
                if (!delta.reasoningContent.isNullOrEmpty()) {
                    if (!thinkOpen) { out.append("<think>"); thinkOpen = true }
                    out.append(delta.reasoningContent)
                }
                if (!delta.content.isNullOrEmpty()) {
                    if (thinkOpen) { out.append("</think>"); thinkOpen = false }
                    out.append(delta.content)
                }
                if (out.isNotEmpty()) trySend(out.toString())
            }

            /** Close a dangling reasoning block if the stream ends mid-thought, so the
             *  stored text stays well-formed (`<think>…</think>` rather than an open tag). */
            private fun finishThink() {
                if (thinkOpen) {
                    trySend("</think>")
                    thinkOpen = false
                }
            }

            override fun onClosed(eventSource: EventSource) {
                finishThink()
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // On a non-2xx the server's body says exactly what it rejected
                // (e.g. a malformed content part) — surface and log it.
                val httpError = response?.let { r ->
                    val body = runCatching { r.body?.string() }.getOrNull().orEmpty().take(500)
                    "HTTP ${r.code}" + if (body.isBlank()) "" else ": $body"
                }
                val message = httpError
                    ?: t?.let { it.message ?: it.javaClass.simpleName }
                    ?: "Connection failed"
                Log.w("LlamaClient", "streamChat failed: $message", t)
                close(IOException(message))
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }
}
