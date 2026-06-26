package net.maz.llamachat.data.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
import java.io.IOException
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

    // --- debug replay hook ---------------------------------------------------
    // When set, every chat request is also written to "last-request.sh" in this
    // dir as a runnable curl script, so a generation can be replayed and tweaked
    // from a shell (see dumpForReplay). Set by LlamaChatApp; remove the field,
    // dumpForReplay, prettyJson and its one call site to drop the feature.
    var debugDumpDir: File? = null

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val prettyJson = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = true }

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
     * Stream a chat completion. Emits incremental text deltas; the flow
     * completes when the server sends the `[DONE]` sentinel and fails (throws)
     * on transport or server errors.
     */
    fun streamChat(ip: String, port: String, body: ChatRequest): Flow<String> = callbackFlow {
        val url = "${base(ip, port)}/v1/chat/completions"
        dumpForReplay(url, body)
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(body).toRequestBody(jsonMedia))
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                val delta = runCatching {
                    json.decodeFromString<ChatChunk>(data)
                        .choices.firstOrNull()?.delta?.content
                }.getOrNull()
                if (!delta.isNullOrEmpty()) {
                    trySend(delta)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val message = when {
                    t != null -> t.message ?: t.javaClass.simpleName
                    response != null -> "HTTP ${response.code}"
                    else -> "Connection failed"
                }
                close(IOException(message))
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    /**
     * Debug-only: dump the request as a runnable curl script to [debugDumpDir]
     * ("last-request.sh"), overwritten each call so it always holds the most
     * recent generation. Pull it (`adb pull`), edit the pretty-printed JSON, and
     * re-run to experiment — e.g. to chase an Impersonate that stops at the first
     * token. Best-effort and silent: a dump failure never affects generation.
     */
    private fun dumpForReplay(url: String, body: ChatRequest) {
        val dir = debugDumpDir ?: return
        runCatching {
            val script = buildString {
                append("#!/bin/sh\n")
                append("# Last LlamaChat request — edit the JSON below and re-run to experiment.\n")
                append("curl -N ").append(url).append(" \\\n")
                append("  -H 'Content-Type: application/json' \\\n")
                append("  -H 'Accept: text/event-stream' \\\n")
                append("  --data-binary @- <<'JSON'\n")
                append(prettyJson.encodeToString(body))
                append("\nJSON\n")
            }
            File(dir, "last-request.sh").writeText(script)
        }
    }
}
