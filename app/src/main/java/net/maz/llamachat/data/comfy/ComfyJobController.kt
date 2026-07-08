package net.maz.llamachat.data.comfy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * App-scoped ledger of ComfyUI generation jobs, shared between the form
 * ViewModel (enqueues), [ComfyGenerationService] (drives), and the gallery UI
 * (observes/cancels). Non-terminal jobs and their submission specs live on disk
 * under `filesDir/comfy/`, so anything already handed over survives process
 * death; [resumeIfNeeded] restarts the service to pick them back up.
 */
class ComfyJobController(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val root = File(context.filesDir, "comfy")
    private val jobsFile = File(root, "jobs.json")
    private val pendingDir = File(root, "pending")

    /** Identifies this app install to ComfyUI (`client_id` on submitted prompts). */
    val clientId: String = UUID.randomUUID().toString()

    private val _jobs = MutableStateFlow(loadJobs())
    val jobs = _jobs.asStateFlow()

    /** Gallery item id -> the job that produced it, for this session only. Lets
     *  the viewer offer "regenerate" and the queue jump to an output, as long as
     *  the source job is still around (terminal jobs aren't persisted). */
    private val itemToJob = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    fun activeJobs(): List<ComfyJob> = _jobs.value.filterNot { it.status.isTerminal }

    /** Record that a downloaded gallery item came from [jobId]. */
    fun linkGalleryItem(itemId: Long, jobId: Long) {
        itemToJob[itemId] = jobId
    }

    /** The still-present job that produced [itemId], or null once it's gone. */
    fun jobForItem(itemId: Long): ComfyJob? =
        itemToJob[itemId]?.let { jid -> _jobs.value.firstOrNull { it.id == jid } }

    /** Gallery item ids this job produced this session, oldest first. */
    fun outputsForJob(jobId: Long): List<Long> =
        itemToJob.filterValues { it == jobId }.keys.sorted()

    /** Fresh target for a picked file input, copied here so the service doesn't
     *  depend on the picker's transient content-Uri grant. */
    fun newPendingInputFile(jobId: Long, index: Int, ext: String): File {
        pendingDir.mkdirs()
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        return File(pendingDir, "${jobId}_$index$suffix")
    }

    fun pendingFile(fileName: String): File = File(pendingDir, fileName)

    /** Persist the submission spec, list the job, and kick the service. The
     *  cached input files named in [submission] must already be in place. */
    suspend fun submit(job: ComfyJob, submission: PendingSubmission): Unit =
        withContext(Dispatchers.IO) {
            pendingDir.mkdirs()
            specFile(job.id).writeText(json.encodeToString(submission))
            mutate { it + job }
            ComfyGenerationService.start(context)
        }

    fun update(jobId: Long, transform: (ComfyJob) -> ComfyJob) {
        mutate { list -> list.map { if (it.id == jobId) transform(it) else it } }
    }

    /** Drop a (terminal) job row from the list, e.g. dismissing a failure. */
    fun remove(jobId: Long) {
        mutate { list -> list.filterNot { it.id == jobId } }
        deletePendingFiles(jobId)
        itemToJob.values.removeAll { it == jobId }
    }

    /** Prune finished/failed/cancelled jobs from the list. */
    fun clearFinished() {
        mutate { list -> list.filterNot { it.status.isTerminal } }
    }

    fun loadPending(jobId: Long): PendingSubmission? = runCatching {
        json.decodeFromString<PendingSubmission>(specFile(jobId).readText())
    }.getOrNull()

    /** Remove the spec and any cached input files once a job is submitted or dead. */
    fun deletePendingFiles(jobId: Long) {
        specFile(jobId).delete()
        pendingDir.listFiles()?.forEach {
            if (it.name.startsWith("${jobId}_")) it.delete()
        }
    }

    /** Restart the service when non-terminal jobs survived a process death. */
    fun resumeIfNeeded() {
        if (activeJobs().isNotEmpty()) {
            // FGS starts can be disallowed if the process somehow comes up in the
            // background; the jobs then resume on the next foreground launch.
            runCatching { ComfyGenerationService.start(context) }
        }
    }

    // ---- internals ----

    private fun specFile(jobId: Long): File = File(pendingDir, "$jobId.json")

    private fun mutate(transform: (List<ComfyJob>) -> List<ComfyJob>) {
        _jobs.update(transform)
        persistJobs()
    }

    /** Jobs reloaded without a prompt id or a spec can never be resumed. */
    private fun loadJobs(): List<ComfyJob> = runCatching {
        if (!jobsFile.exists()) return@runCatching emptyList()
        json.decodeFromString<List<ComfyJob>>(jobsFile.readText()).map { job ->
            if (job.promptId == null && !specFile(job.id).exists()) {
                job.copy(status = ComfyJobStatus.FAILED, message = "Interrupted before submission")
            } else job
        }
    }.getOrDefault(emptyList())

    private fun persistJobs() {
        // Terminal jobs stay visible in memory for the session but aren't
        // worth resurrecting after a restart.
        val live = activeJobs()
        runCatching {
            root.mkdirs()
            jobsFile.writeText(json.encodeToString(live))
        }
    }
}
