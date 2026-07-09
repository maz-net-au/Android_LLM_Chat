package net.maz.llamachat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.model.SceneImageMeta
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.ZoomableImage
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ChatViewModel

/**
 * Full-screen zoomable view of one scene image with Delete and Regenerate.
 * Regenerate asks whether to write a fresh description or re-run the saved prompt
 * with a new seed; either appends a NEW image to the chat (the original is kept)
 * and returns here. Delete removes this image and pops back.
 */
@Composable
fun SceneImageViewerScreen(
    vm: ChatViewModel,
    messageId: Long,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as LlamaChatApp
    val conversation = state.conversation
    val message = conversation?.messages?.firstOrNull { it.id == messageId }
    val meta = message?.sceneImage
    val file = message?.attachments?.firstOrNull()?.let { app.attachmentStore.fileFor(vm.convId, it) }

    // Pop only once the chat has loaded and the message is genuinely gone (deleted) —
    // not on the first frame, before the conversation flow has emitted.
    LaunchedEffect(conversation != null, message == null) {
        if (conversation != null && message == null) onBack()
    }
    if (message == null || meta == null) {
        // Loading (or just deleted): a plain black screen with a working back button.
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            DcAppBar(title = "Scene image", onBack = onBack, onOpenSettings = onOpenSettings)
        }
        return
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var regenChoice by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        DcAppBar(title = "Scene image", onBack = onBack, onOpenSettings = onOpenSettings)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (file != null && file.exists()) {
                ZoomableImage(model = file, contentDescription = "Scene image: ${meta.focus}")
            } else {
                Text(
                    if (meta.status == SceneImageMeta.STATUS_DONE) "Image unavailable" else "No image yet",
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
        }

        if (meta.focus.isNotBlank()) {
            Text(
                "Focus: ${meta.focus}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = { regenChoice = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.SurfaceTint,
                    contentColor = DcColors.Primary,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Regenerate", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.SurfaceTint,
                    contentColor = DcColors.Error,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Text("Delete", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = DcColors.Surface,
            title = { Text("Delete this image?", color = DcColors.OnSurface) },
            text = { Text("The image is removed from the chat.", color = DcColors.OnSurfaceMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSceneMessage(messageId)
                    onBack()
                }) { Text("Delete", color = DcColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = DcColors.OnSurfaceVariant) }
            },
        )
    }

    if (regenChoice) {
        AlertDialog(
            onDismissRequest = { regenChoice = false },
            containerColor = DcColors.Surface,
            title = { Text("Regenerate scene image", color = DcColors.OnSurface) },
            text = {
                Text(
                    "Add another image to the chat. Write a fresh description, or re-run the " +
                        "saved one with a new seed?",
                    color = DcColors.OnSurfaceMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    regenChoice = false
                    vm.regenerateScene(messageId, reusePrompt = false)
                    onBack()
                }) { Text("New description", color = DcColors.Primary) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        regenChoice = false
                        vm.regenerateScene(messageId, reusePrompt = true)
                        onBack()
                    },
                    enabled = meta.prompt.isNotBlank(),
                ) { Text("Same prompt, new seed", color = DcColors.OnSurface) }
            },
        )
    }
}
