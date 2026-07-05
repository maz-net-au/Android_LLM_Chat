package net.maz.llamachat.data.comfy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.MainActivity
import net.maz.llamachat.R
import net.maz.llamachat.data.SettingsRepository.Settings
import net.maz.llamachat.data.net.ComfyFileRef
import net.maz.llamachat.data.net.ComfyHistoryResult

/**
 * Foreground service that drives every active ComfyUI job: submits pending
 * specs (uploading their file inputs first), then polls the server — one
 * `GET /queue` classifies all jobs per tick, with `GET /history/{id}` only for
 * ids that left the queue — downloads finished outputs into the gallery, and
 * summarizes the lot in a single ongoing notification. ComfyUI queues
 * server-side, so any number of jobs can be in flight; the service exits when
 * none are left. Mirrors the GenerationService teardown discipline: every path
 * ends in [finish].
 */
class ComfyGenerationService : Service() {

    private val app get() = application as LlamaChatApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loop: Job? = null

    /** Consecutive polls where a submitted prompt was in neither the queue nor
     *  history — a few in a row means ComfyUI forgot it (restart). */
    private val historyMisses = mutableMapOf<Long, Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat()
                startLoopIfNeeded()
            }
            ACTION_CANCEL_JOB -> {
                val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
                scope.launch { cancelJob(jobId) }.invokeOnCompletion { maybeFinish() }
            }
            ACTION_CANCEL_ALL -> {
                scope.launch {
                    app.comfyJobs.activeJobs().forEach { cancelJob(it.id) }
                }.invokeOnCompletion { maybeFinish() }
            }
            else -> maybeFinish()
        }
        // Submitted jobs survive in jobs.json and resume via resumeIfNeeded();
        // an OS restart of a dead service has nothing extra to offer.
        return START_NOT_STICKY
    }

    private fun startLoopIfNeeded() {
        if (loop?.isActive == true) return
        val launched = scope.launch { supervise() }
        loop = launched
        launched.invokeOnCompletion {
            mainHandler.post { if (loop === launched) finish() }
        }
    }

    /** Finish from a non-loop entry point, but never yank an active loop's
     *  foreground notification out from under it. */
    private fun maybeFinish() {
        mainHandler.post { if (loop?.isActive != true) finish() }
    }

    // ---- the supervising loop ------------------------------------------------

    private suspend fun supervise() {
        val controller = app.comfyJobs
        while (true) {
            if (controller.activeJobs().isEmpty()) return
            val s = app.settingsRepository.current()

            controller.activeJobs()
                .filter { it.promptId == null && it.status == ComfyJobStatus.QUEUED }
                .forEach { submit(it, s) }

            // One queue snapshot classifies every submitted job; a failed fetch
            // (server briefly unreachable) skips the tick rather than failing jobs.
            val queue = app.comfyClient.queue(s.ip, s.comfyPort).getOrNull()
            if (queue != null) {
                for (job in controller.activeJobs()) {
                    val pid = job.promptId ?: continue
                    when {
                        pid in queue.runningIds -> {
                            historyMisses.remove(job.id)
                            if (job.status != ComfyJobStatus.RUNNING) {
                                controller.update(job.id) { it.copy(status = ComfyJobStatus.RUNNING) }
                            }
                        }
                        pid in queue.pendingIds -> historyMisses.remove(job.id)
                        else -> checkHistory(job, pid, s)
                    }
                }
            }

            updateNotification()
            delay(POLL_INTERVAL_MS)
        }
    }

    /** Upload cached inputs, patch their server names into the graph, submit. */
    private suspend fun submit(job: ComfyJob, s: Settings) {
        val controller = app.comfyJobs
        val spec = controller.loadPending(job.id)
        if (spec == null) {
            fail(job.id, "Submission data went missing")
            return
        }
        var graph = spec.graph
        for (input in spec.fileInputs) {
            val file = controller.pendingFile(input.fileName)
            val uploaded = app.comfyClient
                .uploadInput(s.ip, s.comfyPort, file, input.mime, input.fileName)
                .getOrElse { fail(job.id, it.message ?: "Upload failed"); return }
            graph = WorkflowPatcher
                .patch(graph, listOf(PatchOp(input.nodeTitle, input.input, JsonPrimitive(uploaded.inputPath))))
                .getOrElse { fail(job.id, it.message ?: "Patch failed"); return }
        }
        // The user may have cancelled during the uploads.
        if (currentJob(job.id)?.status != ComfyJobStatus.QUEUED) {
            controller.deletePendingFiles(job.id)
            return
        }
        val resp = app.comfyClient.queuePrompt(s.ip, s.comfyPort, graph, controller.clientId)
            .getOrElse { fail(job.id, it.message ?: "Submit failed"); return }
        controller.update(job.id) { it.copy(promptId = resp.promptId) }
        controller.deletePendingFiles(job.id)
    }

    private suspend fun checkHistory(job: ComfyJob, promptId: String, s: Settings) {
        when (val h = app.comfyClient.history(s.ip, s.comfyPort, promptId).getOrNull() ?: return) {
            is ComfyHistoryResult.Completed -> {
                historyMisses.remove(job.id)
                download(job, h.outputs, s)
            }
            is ComfyHistoryResult.Error -> fail(job.id, h.message)
            ComfyHistoryResult.Pending -> {
                val misses = (historyMisses[job.id] ?: 0) + 1
                historyMisses[job.id] = misses
                if (misses >= MAX_HISTORY_MISSES) {
                    fail(job.id, "Job lost — ComfyUI may have restarted")
                }
            }
        }
    }

    /** Pull every output file into the gallery; the job is DONE only once all
     *  rows are inserted (a failed download leaves no partial row). */
    private suspend fun download(job: ComfyJob, outputs: kotlinx.serialization.json.JsonObject, s: Settings) {
        val controller = app.comfyJobs
        controller.update(job.id) { it.copy(status = ComfyJobStatus.DOWNLOADING) }
        updateNotification()

        val refs = (outputs[job.outputNodeId]?.jsonObject?.get(job.outputField) as? JsonArray)
            ?.mapNotNull { el ->
                val obj = el.jsonObject
                val filename = obj["filename"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ComfyFileRef(
                    filename = filename,
                    subfolder = obj["subfolder"]?.jsonPrimitive?.contentOrNull ?: "",
                    type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "output",
                )
            }
        if (refs.isNullOrEmpty()) {
            fail(job.id, "Workflow produced no output at '${job.outputNodeId}'.${job.outputField}")
            return
        }
        for (ref in refs) {
            val (item, dest) = app.galleryRepository.store.newItem(job.flowType, job.workflowName, ref.filename)
            app.comfyClient.download(s.ip, s.comfyPort, ref, dest)
                .getOrElse { fail(job.id, it.message ?: "Download failed"); return }
            app.galleryRepository.insert(item)
        }
        controller.update(job.id) { it.copy(status = ComfyJobStatus.DONE) }
    }

    /**
     * Cancel one job wherever it is: unsubmitted specs are just dropped, queued
     * prompts are deleted server-side, and a *running* prompt gets `/interrupt`
     * — only after `/queue` confirms it's the one executing, so cancelling a
     * queued job can never kill a different running one.
     */
    private suspend fun cancelJob(jobId: Long) {
        val controller = app.comfyJobs
        val job = currentJob(jobId) ?: return
        if (job.status.isTerminal) return
        val pid = job.promptId
        if (pid != null) {
            val s = app.settingsRepository.current()
            val queue = app.comfyClient.queue(s.ip, s.comfyPort).getOrNull()
            when {
                queue == null -> Unit // unreachable server; just drop it locally
                pid in queue.pendingIds -> app.comfyClient.deleteQueued(s.ip, s.comfyPort, pid)
                pid in queue.runningIds -> app.comfyClient.interrupt(s.ip, s.comfyPort)
            }
        }
        controller.update(jobId) { it.copy(status = ComfyJobStatus.CANCELLED) }
        controller.deletePendingFiles(jobId)
        historyMisses.remove(jobId)
        updateNotification()
    }

    private fun currentJob(jobId: Long): ComfyJob? =
        app.comfyJobs.jobs.value.firstOrNull { it.id == jobId }

    private fun fail(jobId: Long, message: String) {
        historyMisses.remove(jobId)
        app.comfyJobs.update(jobId) {
            it.copy(status = ComfyJobStatus.FAILED, message = message.take(300))
        }
        app.comfyJobs.deletePendingFiles(jobId)
    }

    // ---- foreground notification ----------------------------------------------

    private fun startForegroundCompat() {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(summaryText()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private var lastSummary: String? = null

    private fun updateNotification() {
        val text = summaryText()
        if (text == lastSummary) return
        lastSummary = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun summaryText(): String {
        val active = app.comfyJobs.activeJobs()
        val single = active.singleOrNull()
        if (single != null) return "${single.workflowName} — ${single.status.label.lowercase()}"
        val parts = listOf(
            ComfyJobStatus.RUNNING to "running",
            ComfyJobStatus.QUEUED to "queued",
            ComfyJobStatus.DOWNLOADING to "downloading",
        ).mapNotNull { (status, word) ->
            val n = active.count { it.status == status }
            if (n == 0) null else "$n $word"
        }
        return if (parts.isEmpty()) "Finishing up…" else parts.joinToString(" · ")
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Media generation",
                    NotificationManager.IMPORTANCE_LOW, // quiet: no sound/peek
                ).apply {
                    description = "Shown while ComfyUI generations run in the background"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PENDING_FLAGS,
        )
        val cancelAll = PendingIntent.getService(
            this, 1,
            Intent(this, ComfyGenerationService::class.java).setAction(ACTION_CANCEL_ALL),
            PENDING_FLAGS,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_generating)
            .setContentTitle("Generating media…")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Cancel all", cancelAll)
            .build()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "comfy"
        private const val NOTIF_ID = 1003
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_HISTORY_MISSES = 3

        private const val ACTION_START = "net.maz.llamachat.action.START_COMFY"
        private const val ACTION_CANCEL_JOB = "net.maz.llamachat.action.CANCEL_COMFY_JOB"
        private const val ACTION_CANCEL_ALL = "net.maz.llamachat.action.CANCEL_COMFY_ALL"
        private const val EXTRA_JOB_ID = "jobId"

        private val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Ensure the service is running and supervising all active jobs. */
        fun start(context: Context) {
            val intent = Intent(context, ComfyGenerationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Cancel one job. Safe to call regardless of the job's state. */
        fun cancelJob(context: Context, jobId: Long) {
            val intent = Intent(context, ComfyGenerationService::class.java)
                .setAction(ACTION_CANCEL_JOB)
                .putExtra(EXTRA_JOB_ID, jobId)
            runCatching { context.startService(intent) }
        }

        /** Cancel every active job and let the service wind down. */
        fun cancelAll(context: Context) {
            val intent = Intent(context, ComfyGenerationService::class.java).setAction(ACTION_CANCEL_ALL)
            runCatching { context.startService(intent) }
        }
    }
}
