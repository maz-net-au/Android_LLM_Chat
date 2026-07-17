package net.maz.llamachat.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
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
@OptIn(ExperimentalFoundationApi::class)
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

    if (conversation == null) {
        // Still loading: a plain black screen with a working back button.
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            DcAppBar(title = "Scene image", onBack = onBack, onOpenSettings = onOpenSettings)
        }
        return
    }

    // Every scene image in this chat is the swipe context: paging left/right moves
    // between them. The list is live, so deleting one reveals its neighbour.
    val sceneMessages = conversation.messages.filter { it.sceneImage != null }
    if (sceneMessages.isEmpty()) {
        // The last scene image was deleted — leave the viewer.
        LaunchedEffect(Unit) { onBack() }
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            DcAppBar(title = "Scene image", onBack = onBack, onOpenSettings = onOpenSettings)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = sceneMessages.indexOfFirst { it.id == messageId }.coerceAtLeast(0),
        pageCount = { sceneMessages.size },
    )
    val message = sceneMessages.getOrNull(pagerState.currentPage)
    if (message == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val meta = message.sceneImage!!
    val currentId = message.id

    var confirmDelete by remember { mutableStateOf(false) }
    var regenChoice by remember { mutableStateOf(false) }
    // Non-null shows the "edit the description" dialog, holding the in-progress text.
    var editingPrompt by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        DcAppBar(title = "Scene image", onBack = onBack, onOpenSettings = onOpenSettings)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val pageMessage = sceneMessages[page]
            val pageMeta = pageMessage.sceneImage!!
            val pageFile = pageMessage.attachments.firstOrNull()
                ?.let { app.attachmentStore.fileFor(vm.convId, it) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pageFile != null && pageFile.exists()) {
                    ZoomableImage(model = pageFile, contentDescription = "Scene image: ${pageMeta.focus}")
                } else {
                    Text(
                        if (pageMeta.status == SceneImageMeta.STATUS_DONE) "Image unavailable" else "No image yet",
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                }
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

        if (meta.prompt.isNotBlank()) {
            Text(
                "Prompt",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
            Text(
                meta.prompt,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                modifier = Modifier
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
                    // Drop this image; the pager reveals a neighbour, or the empty-list
                    // guard pops the viewer when it was the last scene image.
                    vm.deleteSceneMessage(currentId)
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
                Column {
                    Text(
                        "Add another image to the chat.",
                        color = DcColors.OnSurfaceMedium,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    RegenOption(
                        "New description",
                        "The model writes a fresh description from the same focus.",
                    ) {
                        regenChoice = false
                        vm.regenerateScene(currentId, reusePrompt = false)
                        onBack()
                    }
                    RegenOption(
                        "Same prompt, new seed",
                        "Re-run the saved description with a different seed.",
                        enabled = meta.prompt.isNotBlank(),
                    ) {
                        regenChoice = false
                        vm.regenerateScene(currentId, reusePrompt = true)
                        onBack()
                    }
                    RegenOption(
                        "Edit the description",
                        "Tweak the saved description yourself, then generate.",
                        enabled = meta.prompt.isNotBlank(),
                    ) {
                        regenChoice = false
                        editingPrompt = meta.prompt
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { regenChoice = false }) {
                    Text("Cancel", color = DcColors.OnSurfaceVariant)
                }
            },
        )
    }

    editingPrompt?.let { current ->
        AlertDialog(
            onDismissRequest = { editingPrompt = null },
            containerColor = DcColors.Surface,
            title = { Text("Edit description", color = DcColors.OnSurface) },
            text = {
                Column {
                    Text(
                        "This exact text is sent to generate a new image.",
                        fontSize = 13.sp,
                        color = DcColors.OnSurfaceMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(DcColors.SurfaceTint, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = current,
                            onValueChange = { editingPrompt = it },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                color = DcColors.OnSurface,
                                lineHeight = 21.sp,
                            ),
                            cursorBrush = SolidColor(DcColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = current.trim()
                        editingPrompt = null
                        vm.regenerateScene(currentId, editedPrompt = text)
                        onBack()
                    },
                    enabled = current.isNotBlank(),
                ) { Text("Generate", color = DcColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { editingPrompt = null }) {
                    Text("Cancel", color = DcColors.OnSurfaceVariant)
                }
            },
        )
    }
}

/** One stacked choice in the regenerate dialog: a bold title over a muted one-line hint. */
@Composable
private fun RegenOption(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            title,
            color = if (enabled) DcColors.Primary else DcColors.OnSurfaceFaint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(subtitle, color = DcColors.OnSurfaceFaint, fontSize = 12.sp)
    }
}
