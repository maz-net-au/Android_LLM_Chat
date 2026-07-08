package net.maz.llamachat.ui.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.comfy.ComfyJobStatus
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors

/**
 * The image-gen queue and history: every tracked job, newest first. Active jobs
 * can be cancelled; finished ones can be regenerated (reopening the form with a
 * fresh seed); any row can be removed from the list.
 */
@Composable
fun QueueScreen(
    vm: net.maz.llamachat.vm.QueueViewModel,
    onRegenerate: (ComfyJob) -> Unit,
    onOpenOutput: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = "Queue & History", onBack = onBack, onOpenSettings = onOpenSettings)

        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No generations yet", color = DcColors.OnSurfaceFaint, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(jobs, key = { it.id }) { job ->
                    val output = if (job.status == ComfyJobStatus.DONE) vm.firstOutput(job.id) else null
                    JobCard(
                        job = job,
                        onOpen = output?.let { { onOpenOutput(it) } },
                        onCancel = { vm.cancel(job.id) },
                        onRegenerate = { onRegenerate(job) },
                        onRemove = { vm.remove(job) },
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    job: ComfyJob,
    onOpen: (() -> Unit)?,
    onCancel: () -> Unit,
    onRegenerate: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DcColors.SurfaceTint)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!job.status.isTerminal) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = DcColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    job.workflowName,
                    color = DcColors.OnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    statusLine(job),
                    color = if (job.status == ComfyJobStatus.FAILED) DcColors.Error else DcColors.OnSurfaceFaint,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = DcColors.OnSurfaceFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        val showCancel = !job.status.isTerminal
        val showRegenerate = job.status == ComfyJobStatus.DONE && job.canRegenerate
        if (showCancel || showRegenerate) {
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showCancel) {
                    TintButton("Cancel", onClick = onCancel)
                }
                if (showRegenerate) {
                    TintButton("Regenerate", icon = Icons.Filled.Refresh, onClick = onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun TintButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DcColors.Surface,
            contentColor = DcColors.Primary,
        ),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusLine(job: ComfyJob): String = when (job.status) {
    ComfyJobStatus.FAILED -> job.message ?: "Failed"
    else -> job.status.label
}
