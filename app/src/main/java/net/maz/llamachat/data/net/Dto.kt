package net.maz.llamachat.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for llama-server's OpenAI-compatible API. The sampler fields beyond
 * `temperature` / `top_p` are llama.cpp extensions to the OpenAI chat body, which
 * llama-server accepts. Every sampler is nullable: with the client's
 * `explicitNulls = false`, unset values are omitted entirely so the server
 * applies its own defaults (this is how sparse presets work).
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    /** Sequences that halt generation, e.g. the next speaker's "Name:" prefix. */
    val stop: List<String>? = null,
    /** Token cap; bounds a "Continue" / "Impersonate" so it can't run away. */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("min_p") val minP: Double? = null,
    @SerialName("typical_p") val typicalP: Double? = null,
    @SerialName("repeat_penalty") val repeatPenalty: Double? = null,
    @SerialName("repeat_last_n") val repeatLastN: Int? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    val mirostat: Int? = null,
    @SerialName("mirostat_tau") val mirostatTau: Double? = null,
    @SerialName("mirostat_eta") val mirostatEta: Double? = null,
)

@Serializable
data class ApiMessage(
    val role: String,
    val content: String,
)

/** A streamed chunk: choices[].delta.content carries the incremental text. */
@Serializable
data class ChatChunk(
    val choices: List<ChunkChoice> = emptyList(),
)

@Serializable
data class ChunkChoice(
    val delta: Delta = Delta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val content: String? = null,
)

@Serializable
data class ModelsResponse(
    val data: List<ModelEntry> = emptyList(),
)

/**
 * llama-server's `/props`. The context window is reported under
 * `default_generation_settings.n_ctx`; older builds expose it at the top level, so
 * both spots are parsed and the first present one wins.
 */
@Serializable
data class PropsResponse(
    @SerialName("default_generation_settings") val defaultGenerationSettings: GenerationSettings? = null,
    @SerialName("n_ctx") val nCtx: Int? = null,
) {
    val contextSize: Int? get() = defaultGenerationSettings?.nCtx ?: nCtx
}

@Serializable
data class GenerationSettings(
    @SerialName("n_ctx") val nCtx: Int? = null,
)

/** Request/response for llama-server's `/tokenize`, used to count how many tokens
 *  the current transcript occupies after a reply finishes. [model] is required by
 *  multi-model routers (e.g. llama-swap) to pick the backend; plain llama-server
 *  ignores it. */
@Serializable
data class TokenizeRequest(
    val content: String,
    val model: String? = null,
)

@Serializable
data class TokenizeResponse(
    val tokens: List<Int> = emptyList(),
)

@Serializable
data class ModelEntry(
    val id: String,
)
