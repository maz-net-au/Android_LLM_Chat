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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.MainActivity
import net.maz.llamachat.R
import net.maz.llamachat.data.ConversationRepository
import net.maz.llamachat.data.SettingsRepository

/**
 * Foreground service that owns an in-flight reply generation so it survives the
 * app being backgrounded or the ChatViewModel being cleared (navigating away).
 *
 * It streams tokens from llama-server, publishes them live via
 * [GenerationController] for the UI, and writes throttled checkpoints (plus a
 * final write) to Room so a partial survives even a process kill. The ongoing
 * notification carries a **Cancel** action; cancelling — like a normal finish, a
 * dropped connection, or an error — always tears everything down (the HTTP
 * stream, the shared state, and the notification itself) so nothing gets stuck.
 */
class GenerationService : Service() {

    private val app get() = application as LlamaChatApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // Failsafe: always end up torn down, even if the stream already
                // finished or silently dropped and left this notification behind.
                val running = job
                if (running != null) running.cancel() else finish()
            }
            ACTION_START -> {
                startForegroundCompat(intent.getStringExtra(EXTRA_TITLE).orEmpty())
                startGeneration(
                    convId = intent.getLongExtra(EXTRA_CONV_ID, -1L),
                    targetId = intent.getLongExtra(EXTRA_TARGET_ID, -1L),
                    includePartial = intent.getBooleanExtra(EXTRA_INCLUDE_PARTIAL, false),
                    forceContinue = intent.getBooleanExtra(EXTRA_FORCE_CONTINUE, false),
                )
            }
            else -> finish()
        }
        // Don't auto-restart after a process kill: the server-side generation is
        // gone and the OpenAI-compatible API has no way to resume a dropped stream.
        return START_NOT_STICKY
    }

    private fun startGeneration(
        convId: Long,
        targetId: Long,
        includePartial: Boolean,
        forceContinue: Boolean,
    ) {
        if (convId < 0 || targetId < 0) {
            finish()
            return
        }
        job?.cancel() // single-flight: a new request supersedes any in-flight one
        val launched = scope.launch {
            generate(convId, targetId, includePartial, forceContinue)
        }
        job = launched
        launched.invokeOnCompletion {
            // Tear the service down once this run ends, unless a newer run replaced
            // it. Service lifecycle calls must happen on the main thread.
            mainHandler.post { if (job === launched) finish() }
        }
    }

    private suspend fun generate(
        convId: Long,
        targetId: Long,
        includePartial: Boolean,
        forceContinue: Boolean,
    ) {
        val controller = app.generation
        val repo = app.conversationRepository
        val settings: SettingsRepository = app.settingsRepository
        val client = app.llamaClient

        val conv = repo.get(convId) ?: return
        val idx = conv.messages.indexOfFirst { it.id == targetId }
        if (idx < 0) return
        val s = settings.current()

        // On "Continue", trim trailing whitespace so an already-complete-looking
        // partial doesn't read as a finished turn (mirrors the old ChatViewModel).
        val base = when {
            !includePartial -> ""
            forceContinue -> conv.messages[idx].text.trimEnd()
            else -> conv.messages[idx].text
        }
        // The transcript "Name:" prefill isn't real content; strip it to tell a
        // genuinely empty reply (model emitted nothing) from a real one.
        val prefix = if (conv.character.usesNamePrefixes) "${conv.characterName}: " else ""
        val request = ChatRequestBuilder.reply(conv, idx, includePartial, forceContinue, s)

        val token = controller.begin(convId, targetId, base)
        try {
            // Some servers occasionally return a stream that completes with no
            // content. Rather than store an empty turn, re-request a few times, then
            // discard it. "Continue" is exempt: it already has content to keep, and
            // discarding would drop the user's existing message.
            var attempt = 0
            while (true) {
                val sb = StringBuilder(base)
                controller.update(token, base) // reset the live overlay for this attempt
                var lastWrite = SystemClock.elapsedRealtime()
                try {
                    client.streamChat(s.ip, s.port, request).collect { delta ->
                        sb.append(delta)
                        controller.update(token, sb.toString())
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWrite >= WRITE_INTERVAL_MS) {
                            persist(repo, convId, targetId, sb.toString())
                            lastWrite = now
                        }
                    }
                } catch (c: CancellationException) {
                    // Stop / Cancel: keep whatever was generated so far. The coroutine
                    // is already cancelled, so the save must run uninterruptibly.
                    withContext(NonCancellable) { persist(repo, convId, targetId, sb.toString()) }
                    throw c
                } catch (e: Exception) {
                    val text = if (sb.length <= base.length) {
                        "⚠️ Couldn't generate a reply: ${e.message ?: "unknown error"}"
                    } else {
                        sb.toString() // a dropped connection still keeps the partial
                    }
                    persist(repo, convId, targetId, text)
                    return
                }

                val emptyReply = !forceContinue && sb.toString().removePrefix(prefix).isBlank()
                when {
                    !emptyReply -> {
                        persist(repo, convId, targetId, sb.toString())
                        return
                    }
                    attempt < MAX_EMPTY_RETRIES -> attempt++ // re-request and try again
                    else -> {
                        discardEmpty(repo, convId, targetId)
                        return
                    }
                }
            }
        } finally {
            controller.end(token)
        }
    }

    /** An empty reply survived every retry: drop it instead of storing a blank turn.
     *  A regenerated variant falls back to the previously shown one; a fresh reply
     *  (its only variant) removes the assistant message outright. */
    private suspend fun discardEmpty(repo: ConversationRepository, convId: Long, targetId: Long) {
        val conv = repo.get(convId) ?: return
        val msg = conv.messages.firstOrNull { it.id == targetId } ?: return
        val messages = if (msg.variants.size > 1) {
            val variants = msg.variants.toMutableList()
            val removeAt = msg.activeVariant.coerceIn(0, variants.size - 1)
            variants.removeAt(removeAt)
            conv.messages.map {
                if (it.id == targetId)
                    it.copy(variants = variants, activeVariant = (removeAt - 1).coerceIn(0, variants.size - 1))
                else it
            }
        } else {
            conv.messages.filter { it.id != targetId }
        }
        repo.save(conv.copy(updatedAt = System.currentTimeMillis(), messages = messages))
    }

    /** Read-modify-write the target message's text back into Room. Re-reads each
     *  time so it picks up (rather than clobbers) any concurrent edit. */
    private suspend fun persist(
        repo: ConversationRepository,
        convId: Long,
        targetId: Long,
        text: String,
    ) {
        val conv = repo.get(convId) ?: return // deleted mid-stream: nothing to save
        repo.save(
            conv.copy(
                updatedAt = System.currentTimeMillis(),
                messages = conv.messages.map { if (it.id == targetId) it.withText(text) else it },
            ),
        )
    }

    // ---- foreground notification -------------------------------------------

    private fun startForegroundCompat(title: String) {
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(title),
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
                    "Reply generation",
                    NotificationManager.IMPORTANCE_LOW, // quiet: no sound/peek
                ).apply {
                    description = "Shown while a reply is generated in the background"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun buildNotification(title: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PENDING_FLAGS,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, GenerationService::class.java).setAction(ACTION_CANCEL),
            PENDING_FLAGS,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_generating)
            .setContentTitle("Generating reply…")
            .setContentText(title.ifBlank { "Tap to open the chat" })
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // the NonCancellable final save still completes
    }

    companion object {
        private const val CHANNEL_ID = "generation"
        private const val NOTIF_ID = 1001
        private const val WRITE_INTERVAL_MS = 150L
        /** Re-request this many times when a stream completes with no content. */
        private const val MAX_EMPTY_RETRIES = 2

        private const val ACTION_START = "net.maz.llamachat.action.START_GENERATION"
        private const val ACTION_CANCEL = "net.maz.llamachat.action.CANCEL_GENERATION"
        private const val EXTRA_CONV_ID = "convId"
        private const val EXTRA_TARGET_ID = "targetId"
        private const val EXTRA_INCLUDE_PARTIAL = "includePartial"
        private const val EXTRA_FORCE_CONTINUE = "forceContinue"
        private const val EXTRA_TITLE = "title"

        private val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Start (or replace) the foreground generation for [targetId] in [convId]. */
        fun start(
            context: Context,
            convId: Long,
            targetId: Long,
            includePartial: Boolean,
            forceContinue: Boolean,
            title: String,
        ) {
            val intent = Intent(context, GenerationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONV_ID, convId)
                putExtra(EXTRA_TARGET_ID, targetId)
                putExtra(EXTRA_INCLUDE_PARTIAL, includePartial)
                putExtra(EXTRA_FORCE_CONTINUE, forceContinue)
                putExtra(EXTRA_TITLE, title)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Cancel the active generation and tear the service down. Safe to call
         *  when nothing is running. */
        fun cancel(context: Context) {
            val intent = Intent(context, GenerationService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}
