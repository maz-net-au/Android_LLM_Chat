package net.maz.llamachat.data.gen

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.MainActivity
import net.maz.llamachat.R
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.SettingsRepository.Settings
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.comfy.FieldType
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.PatchOp
import net.maz.llamachat.data.comfy.PendingSubmission
import net.maz.llamachat.data.comfy.WorkflowPatcher
import net.maz.llamachat.data.model.SceneImageMeta
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Foreground service that produces one scene image: it asks the (dedicated) model to
 * describe the scene as a text-to-image prompt, then patches that prompt (and a fresh
 * seed) into the configured ComfyUI workflow and hands the job to [comfyJobs] — the
 * [net.maz.llamachat.data.comfy.ComfyGenerationService] does the polling and downloads
 * the result into the placeholder message as an attachment.
 *
 * Unlike summarization this is NOT single-flight: several scene images can be in
 * flight at once, tracked per message id, so a regenerate never cancels an earlier one.
 * Progress/failure is written onto the message's `sceneImage.status` in Room; the live
 * describe phase is mirrored in [SceneImageController].
 */
class SceneImageService : Service() {

    private val app get() = application as LlamaChatApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** messageId -> the coroutine describing/submitting it. */
    private val jobs = ConcurrentHashMap<Long, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat()
                val convId = intent.getLongExtra(EXTRA_CONV_ID, -1L)
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                val reuse = intent.getBooleanExtra(EXTRA_REUSE_PROMPT, false)
                start(convId, messageId, reuse)
            }
            ACTION_CANCEL -> {
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                jobs[messageId]?.cancel() ?: maybeFinish()
            }
            else -> maybeFinish()
        }
        return START_NOT_STICKY
    }

    private fun start(convId: Long, messageId: Long, reuse: Boolean) {
        if (convId < 0 || messageId < 0) {
            maybeFinish()
            return
        }
        if (jobs.containsKey(messageId)) return // already running for this placeholder
        val launched = scope.launch { run(convId, messageId, reuse) }
        jobs[messageId] = launched
        launched.invokeOnCompletion {
            jobs.remove(messageId, launched)
            mainHandler.post { if (jobs.isEmpty()) finish() }
        }
    }

    private suspend fun run(convId: Long, messageId: Long, reuse: Boolean) {
        val conv = app.conversationRepository.get(convId) ?: return
        val msg = conv.messages.firstOrNull { it.id == messageId } ?: return
        val meta = msg.sceneImage ?: return
        val s = app.settingsRepository.current()

        val prompt = try {
            if (reuse) meta.prompt.ifBlank { describe(convId, messageId, meta.focus, s) }
            else describe(convId, messageId, meta.focus, s)
        } catch (c: CancellationException) {
            markFailed(convId, messageId, "Cancelled")
            throw c
        } catch (e: Exception) {
            markFailed(convId, messageId, e.message ?: "Could not describe the scene")
            return
        }
        if (prompt.isBlank()) {
            markFailed(convId, messageId, "The model returned an empty description")
            return
        }
        try {
            submit(convId, messageId, prompt, s)
        } catch (c: CancellationException) {
            markFailed(convId, messageId, "Cancelled")
            throw c
        } catch (e: Exception) {
            markFailed(convId, messageId, e.message ?: "Could not start the image generation")
        }
    }

    /** Stream the LLM description; tracked live in the controller for the "Describing…" state. */
    private suspend fun describe(convId: Long, messageId: Long, focus: String, s: Settings): String {
        val controller = app.sceneImages
        val conv = app.conversationRepository.get(convId) ?: return ""
        val request = ChatRequestBuilder.sceneDescription(conv, focus, s)
        controller.begin(messageId)
        return try {
            val sb = StringBuilder()
            app.llamaClient.streamChat(s.ip, s.port, request).collect { sb.append(it) }
            stripThink(sb.toString()).trim()
        } finally {
            controller.end(messageId)
        }
    }

    /** Patch prompt + a fresh seed into the workflow and enqueue the chat-destination job. */
    private suspend fun submit(convId: Long, messageId: Long, prompt: String, s: Settings) {
        val workflowId = s.sceneWorkflowId
        require(workflowId >= 0) { "No scene-image workflow is configured (Settings ▸ Scene images)" }
        val node = s.scenePromptNodeTitle
        val input = s.scenePromptInput
        require(node.isNotBlank() && input.isNotBlank()) {
            "No prompt field is configured for the scene-image workflow"
        }
        val graph = app.workflowStore.loadGraph(workflowId).getOrElse {
            throw IllegalStateException(it.message ?: "The scene-image workflow is unavailable")
        }
        val config = app.workflowStore.loadConfig(workflowId).getOrElse {
            throw IllegalStateException(it.message ?: "The scene-image workflow config is unavailable")
        }
        val workflowName = app.workflowStore.workflows.value
            .firstOrNull { it.id == workflowId }?.name ?: config.name

        // Prompt into the chosen field, plus a fresh seed into every seed field: this
        // makes regenerate vary AND defeats ComfyUI's identical-graph result cache.
        val ops = mutableListOf(PatchOp(node, input, JsonPrimitive(prompt)))
        config.fields.filter { it.type == FieldType.SEED.wire }.forEach {
            ops += PatchOp(it.nodeTitle, it.input, JsonPrimitive(randomSeed()))
        }
        val patched = WorkflowPatcher.patch(graph, ops).getOrElse {
            throw IllegalStateException(it.message ?: "Could not apply the prompt to the workflow")
        }
        val outputNodeId = WorkflowPatcher.nodeIdByTitle(patched, config.output.nodeTitle).getOrElse {
            throw IllegalStateException(it.message ?: "The workflow has no output node")
        }

        val jobId = IdGen.next()
        // Record the prompt + job on the message before handing off, so the ledger and
        // the placeholder stay consistent even across a process death.
        patchMeta(convId, messageId) {
            it.copy(
                prompt = prompt,
                jobId = jobId,
                workflowId = workflowId,
                status = SceneImageMeta.STATUS_GENERATING,
            )
        }
        val job = ComfyJob(
            id = jobId,
            flowType = FlowType.TEXT_TO_IMAGE.key,
            workflowName = workflowName,
            createdAt = System.currentTimeMillis(),
            outputNodeId = outputNodeId,
            outputField = config.output.field,
            workflowId = workflowId,
            destination = ComfyJob.DEST_CHAT,
            convId = convId,
            messageId = messageId,
        )
        app.comfyJobs.submit(job, PendingSubmission(jobId, patched, emptyList()))
    }

    private suspend fun markFailed(convId: Long, messageId: Long, error: String) {
        patchMeta(convId, messageId) {
            it.copy(status = SceneImageMeta.STATUS_FAILED, error = error.take(300))
        }
    }

    /** Read-modify-write the scene-image meta on one message, if it's still there. */
    private suspend fun patchMeta(convId: Long, messageId: Long, transform: (SceneImageMeta) -> SceneImageMeta) {
        val conv = app.conversationRepository.get(convId) ?: return
        if (conv.messages.none { it.id == messageId && it.sceneImage != null }) return
        app.conversationRepository.save(
            conv.copy(
                messages = conv.messages.map { m ->
                    if (m.id == messageId && m.sceneImage != null) m.copy(sceneImage = transform(m.sceneImage))
                    else m
                },
            ),
        )
    }

    // ---- foreground notification -------------------------------------------

    private fun startForegroundCompat() {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Scene images",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while a scene image is being described"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PENDING_FLAGS,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_generating)
            .setContentTitle("Creating scene image…")
            .setContentText("Describing the scene")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    private fun maybeFinish() {
        mainHandler.post { if (jobs.isEmpty()) finish() }
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
        private const val CHANNEL_ID = "scene_image"
        private const val NOTIF_ID = 1004

        private const val ACTION_START = "net.maz.llamachat.action.START_SCENE_IMAGE"
        private const val ACTION_CANCEL = "net.maz.llamachat.action.CANCEL_SCENE_IMAGE"
        private const val EXTRA_CONV_ID = "convId"
        private const val EXTRA_MESSAGE_ID = "messageId"
        private const val EXTRA_REUSE_PROMPT = "reusePrompt"

        /** Kept within JSON's safe-integer range so any tool can read the seed back. */
        private const val MAX_SEED = 0x1F_FFFF_FFFF_FFFFL
        private fun randomSeed(): Long = Random.nextLong(0, MAX_SEED)

        private val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /**
         * Generate the scene image for an already-persisted placeholder [messageId].
         * [reusePrompt] skips the describe step and re-runs the stored prompt (new seed).
         */
        fun start(context: Context, convId: Long, messageId: Long, reusePrompt: Boolean) {
            val intent = Intent(context, SceneImageService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONV_ID, convId)
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_REUSE_PROMPT, reusePrompt)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, messageId: Long) {
            val intent = Intent(context, SceneImageService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
            runCatching { context.startService(intent) }
        }
    }
}
