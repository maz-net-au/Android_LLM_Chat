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

@Serializable
data class ModelEntry(
    val id: String,
)
