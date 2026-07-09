package net.maz.llamachat.data.comfy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ComfyJobStatus(val label: String) {
    /** Not yet running server-side: uploading inputs, submitting, or queued. */
    QUEUED("Queued"),
    RUNNING("Running"),
    /** Finished server-side; outputs are being fetched into the gallery. */
    DOWNLOADING("Downloading"),
    DONE("Done"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    val isTerminal: Boolean get() = this == DONE || this == FAILED || this == CANCELLED
}

/**
 * One generation job. Serialized (non-terminal ones only) to
 * `filesDir/comfy/jobs.json` so submitted jobs survive process death — ComfyUI
 * holds the queue and history server-side, so a reloaded [promptId] can simply
 * resume polling.
 */
@Serializable
data class ComfyJob(
    val id: Long,
    /** [FlowType.key] — gallery grouping for the results. */
    val flowType: String,
    val workflowName: String,
    val createdAt: Long,
    /** Graph node id whose history outputs hold the results (resolved at submit). */
    val outputNodeId: String,
    /** Key inside that node's outputs entry, e.g. "images". */
    val outputField: String,
    /** Installed workflow this came from; -1 when unknown (can't regenerate). */
    val workflowId: Long = -1L,
    /** Scalar form values used, so the form can reopen pre-filled to regenerate. */
    val inputs: List<JobInput> = emptyList(),
    /** Server-side id; null until `POST /prompt` succeeds. */
    val promptId: String? = null,
    val status: ComfyJobStatus = ComfyJobStatus.QUEUED,
    /** Failure reason (FAILED) or progress hint. */
    val message: String? = null,
    /** Where finished outputs land: [DEST_GALLERY] (default) or [DEST_CHAT].
     *  Defaults keep pre-existing ledgers decoding. */
    val destination: String = DEST_GALLERY,
    /** For [DEST_CHAT]: the conversation and scene-image message to fill in. */
    val convId: Long = -1L,
    val messageId: Long = -1L,
) {
    val canRegenerate: Boolean get() = workflowId >= 0

    companion object {
        const val DEST_GALLERY = "gallery"
        const val DEST_CHAT = "chat"
    }
}

/** One scalar form value, addressed like [PatchOp], captured for regeneration. */
@Serializable
data class JobInput(val nodeTitle: String, val input: String, val value: String)

/** A file input already copied into the pending dir, waiting for upload. */
@Serializable
data class PendingFileInput(
    val nodeTitle: String,
    val input: String,
    /** Name under `filesDir/comfy/pending/`. */
    val fileName: String,
    val mime: String,
)

/**
 * Everything the service needs to submit a job, written to
 * `filesDir/comfy/pending/<jobId>.json` — never Intent extras (patched graphs
 * can exceed the binder transaction limit).
 */
@Serializable
data class PendingSubmission(
    val jobId: Long,
    /** Graph with all non-file values already patched in. */
    val graph: JsonObject,
    val fileInputs: List<PendingFileInput> = emptyList(),
)
