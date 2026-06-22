package net.maz.llamachat.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for llama-server's OpenAI-compatible API. `top_k` and
 * `repeat_penalty` are llama.cpp extensions to the OpenAI chat body, which
 * llama-server accepts.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Double,
    @SerialName("top_p") val topP: Double,
    @SerialName("top_k") val topK: Int,
    @SerialName("repeat_penalty") val repeatPenalty: Double,
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
