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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.MainActivity
import net.maz.llamachat.R

/**
 * Foreground service that compacts the older part of a conversation into a fresh
 * summary, surviving the app being backgrounded or the ChatViewModel being cleared.
 *
 * Unlike [GenerationService] this writes nothing until it succeeds: on completion it
 * sets the conversation's summary and locks (with `<think>` stripped) every message
 * that was old enough to fold in — captured by id up front, so messages that arrive
 * mid-run aren't mistakenly locked. Cancel or failure changes nothing. Progress and
 * completion are published via [SummarizationController] for the UI to react to.
 */
class SummarizationService : Service() {

    private val app get() = application as LlamaChatApp
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val running = job
                if (running != null) running.cancel() else finish()
            }
            ACTION_START -> {
                startForegroundCompat(intent.getStringExtra(EXTRA_TITLE).orEmpty())
                start(intent.getLongExtra(EXTRA_CONV_ID, -1L))
            }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun start(convId: Long) {
        if (convId < 0) {
            finish()
            return
        }
        job?.cancel() // single-flight
        val launched = scope.launch { summarize(convId) }
        job = launched
        launched.invokeOnCompletion {
            mainHandler.post { if (job === launched) finish() }
        }
    }

    private suspend fun summarize(convId: Long) {
        val controller = app.summarization
        val repo = app.conversationRepository
        val client = app.llamaClient

        val conv = repo.get(convId) ?: run { finish(); return }
        val keepFrom = SummarizationConfig.keepFrom(conv.messages.size)
        if (keepFrom <= 0) return // nothing old enough to compact
        // Capture which messages to lock now, so a message sent mid-run stays unlocked.
        // Scene images aren't part of the transcript, so leave them alone (keeps Delete working).
        val lockIds = conv.messages.take(keepFrom).filterNot { it.isSceneImage }.map { it.id }.toSet()
        val s = app.settingsRepository.current()
        val request = ChatRequestBuilder.summarize(conv, keepFrom, s)

        controller.begin(convId)
        val sb = StringBuilder()
        try {
            client.streamChat(s.ip, s.port, request).collect { delta ->
                sb.append(delta)
                controller.update(convId, sb.toString())
            }
        } catch (c: CancellationException) {
            controller.finish(convId, SummaryPhase.FAILED)
            throw c
        } catch (_: Exception) {
            controller.finish(convId, SummaryPhase.FAILED)
            return
        }

        val summary = stripThink(sb.toString())
        if (summary.isBlank()) {
            controller.finish(convId, SummaryPhase.FAILED)
            return
        }
        applySummary(convId, summary, lockIds)
        controller.finish(convId, SummaryPhase.DONE)
    }

    /** Persist the new summary and lock (with reasoning stripped) the folded messages. */
    private suspend fun applySummary(convId: Long, summary: String, lockIds: Set<Long>) {
        val conv = app.conversationRepository.get(convId) ?: return
        app.conversationRepository.save(
            conv.copy(
                updatedAt = System.currentTimeMillis(),
                summary = summary,
                messages = conv.messages.map { m ->
                    if (m.id in lockIds) m.withText(stripThink(m.text)).copy(locked = true) else m
                },
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
                    "Summarization",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while a conversation is being summarized"
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
            Intent(this, SummarizationService::class.java).setAction(ACTION_CANCEL),
            PENDING_FLAGS,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_generating)
            .setContentTitle("Summarizing conversation…")
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
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "summarization"
        private const val NOTIF_ID = 1002

        private const val ACTION_START = "net.maz.llamachat.action.START_SUMMARIZATION"
        private const val ACTION_CANCEL = "net.maz.llamachat.action.CANCEL_SUMMARIZATION"
        private const val EXTRA_CONV_ID = "convId"
        private const val EXTRA_TITLE = "title"

        private val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun start(context: Context, convId: Long, title: String) {
            val intent = Intent(context, SummarizationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONV_ID, convId)
                putExtra(EXTRA_TITLE, title)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, SummarizationService::class.java).setAction(ACTION_CANCEL)
            runCatching { context.startService(intent) }
        }
    }
}
