package net.maz.llamachat.ui.chat

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Full-screen zoomable view of one scene image. A bottom-right menu holds the same
 * actions as the in-chat long-press: regenerate from the same prompt, a fresh
 * description, or an edited one — each appends a NEW image to the chat (the original
 * is kept) and pops back — plus Delete, which removes this image and pops back.
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
    val currentFile = message.attachments.firstOrNull()
        ?.let { app.attachmentStore.fileFor(vm.convId, it) }

    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editPrompt by remember { mutableStateOf(false) }

    // Any regenerate appends a NEW image to the chat, so leave the viewer for it.
    fun regenerate(reuse: Boolean, edited: String?) {
        vm.regenerateScene(currentId, reusePrompt = reuse, editedPrompt = edited)
        onBack()
    }

    // Saving is permissionless from API 29; 26–28 needs the legacy write permission
    // at the moment of export, so hold the image until it's granted.
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<Long?>(null) }
    fun toastSaveResult(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingSave?.let { vm.saveSceneImage(it, ::toastSaveResult) }
        else toastSaveResult("Storage access denied")
        pendingSave = null
    }
    fun save() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingSave = currentId
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.saveSceneImage(currentId, ::toastSaveResult)
        }
    }

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

        // Everything this screen can do hangs off one small menu in the bottom-right
        // corner, so the image itself keeps the space.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(44.dp)
                        .background(DcColors.SurfaceTint, RoundedCornerShape(10.dp)),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Image actions",
                        tint = DcColors.OnSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                SceneImageMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    hasPrompt = meta.prompt.isNotBlank(),
                    hasImage = currentFile?.exists() == true,
                    // Only a vision model can look at what it's handed.
                    canShare = state.canAttachImage && !state.streaming &&
                        !state.impersonating && !state.summarizing,
                    onSamePrompt = { regenerate(reuse = true, edited = null) },
                    onNewDescription = { regenerate(reuse = false, edited = null) },
                    onEditDescription = { editPrompt = true },
                    // The reply lands in the chat, so go watch it.
                    onShare = { vm.shareSceneImage(currentId); onBack() },
                    onSave = { save() },
                    onDelete = { confirmDelete = true },
                )
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

    if (editPrompt) {
        SceneEditPromptDialog(
            prompt = meta.prompt,
            onDismiss = { editPrompt = false },
            onGenerate = { edited ->
                editPrompt = false
                regenerate(false, edited)
            },
        )
    }
}
