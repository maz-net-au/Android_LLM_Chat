package net.maz.llamachat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.ComfyJobStatus
import net.maz.llamachat.data.model.Attachment
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.data.model.SceneImageMeta
import net.maz.llamachat.ui.components.AppBarMenuItem
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.MarkdownText
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ChatViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onEditDetails: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSceneImage: (Long) -> Unit = {},
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val conv = state.conversation
    val context = LocalContext.current
    val app = context.applicationContext as LlamaChatApp
    // The launcher's ephemeral "Image to Text" scratch chat: no chat management
    // (edit details / summarize / delete) and a camera-flavoured empty state.
    val quickImage = vm.convId == Conversation.QUICK_IMAGE_ID
    val clipboard = LocalClipboardManager.current

    // --- image attachment pickers -------------------------------------------

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(vm::attachImage) }

    val galleryPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) galleryLauncher.launch("image/*")
        else Toast.makeText(context, "Photo access denied", Toast.LENGTH_SHORT).show()
    }

    fun pickGallery() {
        // Full media permission (not the limited photo picker) so the pick isn't
        // restricted to a hand-selected subset of photos.
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch("image/*")
        } else {
            galleryPermLauncher.launch(perm)
        }
    }

    // The capture target must survive not just recomposition but activity
    // recreation — launching the camera app routinely destroys this activity
    // (orientation, memory), and a plain `remember` would come back null and
    // silently drop the capture.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        cameraUri = null
        // attachImage imports (downscales) the capture into app storage; the cache
        // file is swept on the next capture rather than deleted here, since the
        // import reads it asynchronously.
        if (ok && uri != null) vm.attachImage(uri)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // sweep older captures
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "net.maz.llamachat.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "Camera access denied", Toast.LENGTH_SHORT).show()
    }

    fun takePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- voice recording ------------------------------------------------------

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Granted mid-press: the user just lifts and holds again to record.
        if (!granted) Toast.makeText(context, "Microphone access denied", Toast.LENGTH_SHORT).show()
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording()
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Export the conversation to a user-picked JSON file (a backup before summarizing).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val text = vm.backupJson()
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }.onSuccess {
            Toast.makeText(context, "Conversation exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    // A finished summarization switches to the details screen (where the summary is shown
    // and editable), then clears the one-shot flag so returning here doesn't re-navigate.
    LaunchedEffect(state.summarizeDoneId) {
        state.summarizeDoneId?.let { onEditDetails(it); vm.consumeSummarizeDone() }
    }

    val character = conv?.character
    val messages = conv?.messages ?: emptyList()
    val userName = conv?.userName ?: "You"
    val characterName = conv?.characterName ?: ""
    val firstUnlockedIndex = messages.indexOfFirst { !it.locked }
    // Scene images are local-only placeholders, not part of the transcript — the reply
    // actions (Regenerate/Impersonate) target the last *real* assistant message.
    val lastAssistantIndex = messages.indexOfLast { it.role == Role.ASSISTANT && !it.isSceneImage }
    val lastRealRole = messages.lastOrNull { !it.isSceneImage }?.role
    val lastIsAssistant = lastRealRole == Role.ASSISTANT
    // A chat left on a bare user turn (e.g. the model returned an empty reply that was
    // discarded, or a reply never landed) still needs a way forward — offer Regenerate,
    // which appends a fresh assistant turn and generates a reply for it.
    val lastIsUser = lastRealRole == Role.USER
    val showActions = state.streaming || lastIsAssistant || lastIsUser

    // Focus prompt for a new scene image.
    var sceneFocus by rememberSaveable { mutableStateOf<String?>(null) }
    sceneFocus?.let { focus ->
        SceneFocusDialog(
            value = focus,
            onValueChange = { sceneFocus = it },
            onDismiss = { sceneFocus = null },
            onGenerate = { vm.generateSceneImage(focus); sceneFocus = null },
        )
    }

    val listState = rememberLazyListState()
    // Scene images are spoilered by default; a tap reveals them. Kept in-memory for the
    // screen's lifetime so revealed images stay open while scrolling, but re-hide when the
    // conversation is left and reopened.
    val revealedScenes = remember { mutableStateMapOf<Long, Boolean>() }
    // Re-pin to the bottom of the latest message on new content (so a streaming
    // reply's tail stays visible as it grows), as the keyboard slides in/out (the
    // IME bottom inset animates), and when the last message is selected — its inline
    // action buttons appear below it and would otherwise sit under the Stop/Regenerate
    // row. Selecting older messages doesn't re-scroll (it would jump jarringly).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val lastSelected = state.selectedMsgId != null && state.selectedMsgId == messages.lastOrNull()?.id
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, imeBottom, lastSelected) {
        // A large scroll offset lands at the end of the content, keeping the tail
        // of a long (taller-than-viewport) last message in view rather than its top.
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
    }

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            titleContent = {
                Avatar(
                    initial = character?.name ?: "",
                    color = character?.color ?: DcColors.Primary,
                    size = 34.dp,
                    fontSize = 15.sp,
                    bordered = true,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(character?.name ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Catalog.shortModel(conv?.model ?: ""), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            menuItems = buildList {
                if (!quickImage) {
                    add(AppBarMenuItem(Icons.Filled.EditNote, "Edit chat details", DcColors.OnSurfaceVariant, DcColors.OnSurface) { conv?.let { onEditDetails(it.id) } })
                    if (state.canSummarize) {
                        add(AppBarMenuItem(Icons.Filled.Compress, "Summarize & continue", DcColors.OnSurfaceVariant, DcColors.OnSurface, vm::summarize))
                    }
                }
                add(AppBarMenuItem(Icons.Filled.FileDownload, "Export conversation", DcColors.OnSurfaceVariant, DcColors.OnSurface) {
                    exportLauncher.launch("${(conv?.title ?: "conversation").ifBlank { "conversation" }}.json")
                })
                if (!quickImage) {
                    add(AppBarMenuItem(Icons.Filled.Delete, "Delete conversation", DcColors.Error, DcColors.Error, vm::deleteConversation))
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChat(character?.name ?: "your assistant", quickImage = quickImage)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                        // Mark where the summarized (locked) history ends and the live
                        // transcript resumes, but only when a summary actually stands in
                        // for those older messages.
                        if (index == firstUnlockedIndex && firstUnlockedIndex > 0 &&
                            conv?.summary?.isNotBlank() == true
                        ) {
                            SummarizedDivider()
                        }
                        if (message.isSceneImage) {
                            SceneImageItem(
                                message = message,
                                file = message.attachments.firstOrNull()
                                    ?.let { app.attachmentStore.fileFor(vm.convId, it) },
                                describing = message.id in state.describingSceneIds,
                                jobPhase = state.sceneJobPhases[message.id],
                                revealed = revealedScenes[message.id] == true,
                                onReveal = { revealedScenes[message.id] = true },
                                onOpen = { onOpenSceneImage(message.id) },
                                onRetry = { vm.retryScene(message.id) },
                                onDelete = { vm.deleteSceneMessage(message.id) },
                            )
                            return@itemsIndexed
                        }
                        MessageItem(
                            message = message,
                            attachmentFiles = message.attachments.map { it to app.attachmentStore.fileFor(vm.convId, it) },
                            userName = userName,
                            characterName = characterName,
                            locked = message.locked,
                            editing = state.editingMsgId == message.id,
                            selected = state.selectedMsgId == message.id,
                            isLastAssistant = index == lastAssistantIndex,
                            streamingCaret = state.streaming && index == messages.lastIndex && message.role == Role.ASSISTANT,
                            editText = state.editText,
                            onTap = { vm.toggleSelected(message.id) },
                            onCopy = {
                                // Copy what the bubble shows: no "Name:" prefix, and no
                                // <think> reasoning on assistant replies — just the answer.
                                val shown = message.displayText(userName, characterName)
                                val text = if (message.role == Role.ASSISTANT) parseThink(shown).answer else shown
                                clipboard.setText(AnnotatedString(text))
                                // Android 13+ shows its own clipboard confirmation overlay.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStartEdit = { vm.startEdit(message.id) },
                            onEditTextChange = vm::setEditText,
                            onSaveEdit = vm::saveEdit,
                            onCancelEdit = vm::cancelEdit,
                            onContinue = { vm.continueMessage(message.id) },
                            onDelete = vm::deleteLastAssistant,
                            onPrevVariant = { vm.prevVariant(message.id) },
                            onNextVariant = { vm.nextVariant(message.id) },
                        )
                    }
                }
            }
        }

        if (showActions) {
            ActionRow(
                streaming = state.streaming,
                impersonating = state.impersonating,
                regenerateEnabled = !state.streaming && !state.impersonating && (lastIsAssistant || lastIsUser),
                // Impersonation continues the transcript, so it only makes sense for
                // characters that use "Name:" prefixes (roleplay-style chats), and it
                // writes the user's *next* turn — so it needs a preceding assistant reply.
                showImpersonate = character?.usesNamePrefixes == true,
                impersonateEnabled = !state.streaming && !state.impersonating && lastIsAssistant,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
                onImpersonate = vm::impersonate,
            )
        }

        if (state.summarizing) {
            SummarizingBanner(progress = state.summaryProgress, onCancel = vm::stop)
        }

        ContextMeter(tokenCount = state.tokenCount, contextLimit = state.contextLimit)

        InputBar(
            input = state.input,
            // Blank sends are allowed (forces the assistant to take another turn);
            // only an in-flight reply, impersonation or summarization disables the button.
            sendEnabled = !state.streaming && !state.impersonating && !state.summarizing,
            canAttachImage = state.canAttachImage,
            canRecordAudio = state.canRecordAudio,
            sceneImageEnabled = state.sceneImageEnabled,
            recording = state.recording,
            pendingImageFile = state.pendingImage?.let { app.attachmentStore.fileFor(vm.convId, it) },
            onInputChange = vm::setInput,
            onSend = vm::send,
            onPickGallery = ::pickGallery,
            onTakePhoto = ::takePhoto,
            onSceneImage = { sceneFocus = "" },
            onRemovePendingImage = vm::removePendingImage,
            onStartRecording = ::startRecording,
            onStopRecording = { vm.stopRecording() },
        )
    }
}

/**
 * A slim footer showing how full the model's context is: the transcript's token
 * count (measured by the server after each reply) and, once the window size is
 * known, the percentage used. At 90%+ it flips to the error color with a warning
 * glyph — a soft nudge that the oldest turns are about to fall out of context, not
 * a hard block.
 */
@Composable
private fun ContextMeter(tokenCount: Int?, contextLimit: Int?) {
    // Nothing measured yet (fresh chat, or the server hasn't answered) — stay hidden
    // rather than show a placeholder zero.
    if (tokenCount == null) return
    // A non-positive limit means "unknown" — show just the count, no "/ 0" or bogus %.
    val limit = contextLimit?.takeIf { it > 0 }
    val fraction = limit?.let { tokenCount.toFloat() / it }
    val warn = fraction != null && fraction >= 0.9f
    val color = if (warn) DcColors.Error else DcColors.OnSurfaceFaint
    val label = buildString {
        append(formatTokens(tokenCount))
        if (limit != null) {
            append(" / ")
            append(formatTokens(limit))
        }
        append(" tokens")
        if (fraction != null) append(" · ${(fraction * 100).roundToInt()}%")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (warn) {
            Icon(Icons.Filled.Warning, contentDescription = "Context nearly full", tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, color = color, fontSize = 11.sp, fontWeight = if (warn) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Group thousands with commas so large counts stay readable (e.g. "12,345"). */
private fun formatTokens(n: Int): String =
    n.toString().reversed().chunked(3).joinToString(",").reversed()

/** Marks the boundary between the summarized (locked) history above and the live
 *  transcript below, so it's clear those older turns are now folded into the summary. */
@Composable
private fun SummarizedDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(DcColors.Divider))
        Text(
            "summarized",
            fontSize = 11.sp,
            color = DcColors.OnSurfaceFaint,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(DcColors.Divider))
    }
}

/** Shown while a summarization streams in the background: a live, truncated preview of
 *  the summary plus a Cancel. Input and actions are disabled meanwhile. */
@Composable
private fun SummarizingBanner(progress: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .background(DcColors.PrimaryContainer, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            color = DcColors.Primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Summarizing…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
            if (progress.isNotBlank()) {
                Text(
                    progress,
                    fontSize = 12.sp,
                    color = DcColors.OnSurfaceMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TextActionButton("Cancel", DcColors.OnSurfaceVariant, onCancel)
    }
}

/**
 * The text shown in a bubble: the stored text minus the leading "Name:" speaker
 * prefix. Display-only — the stored message keeps the prefix so future API turns
 * still carry it.
 */
private fun ChatMessage.displayText(userName: String, characterName: String): String {
    val prefix = if (role == Role.USER) "$userName: " else "$characterName: "
    return text.removePrefix(prefix)
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    attachmentFiles: List<Pair<Attachment, File>>,
    userName: String,
    characterName: String,
    locked: Boolean,
    editing: Boolean,
    selected: Boolean,
    isLastAssistant: Boolean,
    streamingCaret: Boolean,
    editText: String,
    onTap: () -> Unit,
    onCopy: () -> Unit,
    onStartEdit: () -> Unit,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onPrevVariant: () -> Unit,
    onNextVariant: () -> Unit,
) {
    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = if (editing) Arrangement.Start else if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            // A fraction of the current width (not a fixed dp) so bubbles grow to
            // use the extra room on rotation / larger windows, capped for readability.
            modifier = if (editing) Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth(0.85f).widthIn(max = 600.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (editing) {
                EditBox(editText, onEditTextChange, onSaveEdit, onCancelEdit)
            } else {
                MessageBubble(message.displayText(userName, characterName), attachmentFiles, isUser, streamingCaret, onTap)
                if (!isUser && message.variantCount > 1) {
                    VariantNav(message, onPrevVariant, onNextVariant)
                }
                // Locked (summarized) messages are frozen: no edit/continue/delete.
                if (selected && !locked) {
                    SelectedActions(isLastAssistant, onCopy, onStartEdit, onContinue, onDelete)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    attachmentFiles: List<Pair<Attachment, File>>,
    isUser: Boolean,
    streamingCaret: Boolean,
    onTap: () -> Unit,
) {
    val shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(0.dp)
    val bg = if (isUser) DcColors.PrimaryContainer else Color.Transparent
    // Only assistant replies carry <think> reasoning; user text is shown verbatim.
    val parsed = remember(text, isUser) {
        if (isUser) ThinkParts(hasThink = false, reasoning = "", answer = text, streaming = false)
        else parseThink(text)
    }
    Column(
        modifier = Modifier
            .background(bg, shape)
            .clickable(onClick = onTap)
            .padding(if (isUser) PaddingUser else PaddingAssistant),
    ) {
        attachmentFiles.forEach { (att, file) ->
            when (att.kind) {
                Attachment.KIND_IMAGE -> AsyncImage(
                    model = file,
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(bottom = if (parsed.answer.isNotEmpty()) 6.dp else 0.dp)
                        .widthIn(max = 240.dp)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Attachment.KIND_AUDIO -> AudioPill(
                    file = file,
                    durationMs = att.durationMs,
                    modifier = Modifier.padding(bottom = if (parsed.answer.isNotEmpty()) 6.dp else 0.dp),
                )
            }
        }
        if (parsed.hasThink) {
            // Pulse only while this (last) message is actively streaming its think block;
            // a run that stopped mid-thought shows a static, still-expandable header.
            ThinkingSection(parsed.reasoning, animating = parsed.streaming && streamingCaret)
        }
        if (parsed.answer.isNotEmpty()) {
            MarkdownText(parsed.answer)
        }
        // The pulsing "Thinking…" label already signals activity during the think
        // phase, so only show the caret once the answer is streaming.
        if (streamingCaret && !parsed.streaming) {
            BlinkingCaret()
        }
    }
}

private val PaddingUser = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val PaddingAssistant = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)

/** A voice-message bubble: tap to play/stop, with the clip duration. No seek bar —
 *  clips are short and the model gets the audio either way. */
@Composable
private fun AudioPill(file: File, durationMs: Long, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DcColors.SurfaceTint)
            .clickable {
                if (playing) {
                    runCatching { player.stop() }
                    playing = false
                } else {
                    runCatching {
                        player.reset()
                        player.setDataSource(file.path)
                        player.setOnCompletionListener { playing = false }
                        player.prepare()
                        player.start()
                        playing = true
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Stop playback" else "Play voice message",
            tint = DcColors.Primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        val secs = durationMs / 1_000
        Text("%d:%02d".format(secs / 60, secs % 60), fontSize = 13.sp, color = DcColors.OnSurface)
    }
}

@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "caretAlpha",
    )
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(width = 7.dp, height = 15.dp)
            .background(DcColors.Primary.copy(alpha = if (alpha > 0.5f) 1f else 0f)),
    )
}

@Composable
private fun VariantNav(message: ChatMessage, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        IconButton(onClick = onPrev, enabled = message.activeVariant > 0, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous", tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            "${message.activeVariant + 1} / ${message.variantCount}",
            fontSize = 12.sp,
            color = DcColors.OnSurfaceFaint,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        IconButton(onClick = onNext, enabled = message.activeVariant < message.variantCount - 1, modifier = Modifier.size(26.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next", tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SelectedActions(isLastAssistant: Boolean, onCopy: () -> Unit, onEdit: () -> Unit, onContinue: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
        ActionChip("Copy", Icons.Filled.ContentCopy, DcColors.OnSurfaceMedium, onCopy)
        Spacer(Modifier.width(6.dp))
        ActionChip("Edit", Icons.Filled.Edit, DcColors.OnSurfaceMedium, onEdit)
        if (isLastAssistant) {
            Spacer(Modifier.width(6.dp))
            ActionChip("Continue", Icons.Filled.PlayArrow, DcColors.Primary, onContinue)
            Spacer(Modifier.width(6.dp))
            ActionChip("Delete", Icons.Filled.Delete, DcColors.Error, onDelete)
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(DcColors.SurfaceTint, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = tint)
    }
}

@Composable
private fun EditBox(editText: String, onChange: (String) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DcColors.SurfaceTint, RoundedCornerShape(10.dp))
            .border(1.dp, DcColors.Primary, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        BasicTextField(
            value = editText,
            onValueChange = onChange,
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = DcColors.OnSurface, lineHeight = 22.sp),
            cursorBrush = SolidColor(DcColors.Primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            TextActionButton("Cancel", DcColors.OnSurfaceVariant, onCancel)
            Spacer(Modifier.width(6.dp))
            TextActionButton("Save", DcColors.Primary, onSave)
        }
    }
}

@Composable
private fun TextActionButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ActionRow(
    streaming: Boolean,
    impersonating: Boolean,
    regenerateEnabled: Boolean,
    showImpersonate: Boolean,
    impersonateEnabled: Boolean,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onImpersonate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        PillButton("Stop", Icons.Filled.Stop, enabled = streaming || impersonating, borderColor = DcColors.OnSurface.copy(alpha = 0.2f), contentColor = DcColors.OnSurface.copy(alpha = 0.75f), onClick = onStop)
        if (showImpersonate) {
            Spacer(Modifier.width(8.dp))
            PillButton(
                label = "Impersonate",
                icon = Icons.Filled.Person,
                enabled = impersonateEnabled,
                borderColor = DcColors.OnSurface.copy(alpha = 0.2f),
                contentColor = DcColors.OnSurface.copy(alpha = 0.75f),
                onClick = onImpersonate,
            )
        }
        Spacer(Modifier.width(8.dp))
        PillButton("Regenerate", Icons.Filled.Refresh, enabled = regenerateEnabled, borderColor = DcColors.Primary, contentColor = DcColors.Primary, onClick = onRegenerate)
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, borderColor: Color, contentColor: Color, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .background(DcColors.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, borderColor.copy(alpha = (borderColor.alpha * alpha)), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = contentColor.copy(alpha = alpha))
    }
}

@Composable
private fun InputBar(
    input: String,
    sendEnabled: Boolean,
    canAttachImage: Boolean,
    canRecordAudio: Boolean,
    sceneImageEnabled: Boolean,
    recording: Boolean,
    pendingImageFile: File?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSceneImage: () -> Unit,
    onRemovePendingImage: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    // Mirror the String input in a TextFieldValue so external updates (impersonation
    // streaming, send-clear, prompt restore) can drop the caret at the end — that's
    // what makes the field scroll to reveal the freshly appended tail. Typing keeps
    // its own caret, because then input already equals the field's text (no reset).
    var field by remember { mutableStateOf(TextFieldValue(input)) }
    LaunchedEffect(input) {
        if (input != field.text) field = TextFieldValue(input, TextRange(input.length))
    }
    Row(
        modifier = Modifier.fillMaxWidth().border(0.dp, Color.Transparent).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (canAttachImage || sceneImageEnabled) {
            AttachButton(
                enabled = sendEnabled,
                canAttachImage = canAttachImage,
                sceneImageEnabled = sceneImageEnabled,
                onPickGallery = onPickGallery,
                onTakePhoto = onTakePhoto,
                onSceneImage = onSceneImage,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(DcColors.SurfaceTint, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (pendingImageFile != null) {
                PendingImagePreview(pendingImageFile, onRemovePendingImage)
            }
            if (recording) {
                RecordingIndicator()
            } else {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        if (it.text != input) onInputChange(it.text)
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = DcColors.OnSurface, lineHeight = 21.sp),
                    cursorBrush = SolidColor(DcColors.Primary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (field.text.isEmpty()) Text("Message…", color = DcColors.OnSurfaceFaint, fontSize = 15.sp)
                        inner()
                    },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(if (sendEnabled) DcColors.Primary else DcColors.Primary.copy(alpha = 0.30f), CircleShape)
                .clickable(enabled = sendEnabled, onClick = onSend),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        // Audio and text are mutually exclusive: the mic hides as soon as text is
        // typed (or an image is staged), leaving Send as the only affordance.
        if (canRecordAudio && field.text.isEmpty() && pendingImageFile == null) {
            Spacer(Modifier.width(8.dp))
            MicButton(
                recording = recording,
                enabled = sendEnabled,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
            )
        }
    }
}

/** Hold-to-record mic (shown right of Send on audio-capable models). */
@Composable
private fun MicButton(
    recording: Boolean,
    enabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(if (recording) DcColors.Error else DcColors.SurfaceTint, CircleShape)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        }
                    },
                )
            },
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Hold to record",
            tint = if (recording) Color.White else DcColors.Primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Pulsing dot + elapsed timer shown in place of the text field while recording. */
@Composable
private fun RecordingIndicator() {
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsed++
        }
    }
    val transition = rememberInfiniteTransition(label = "recording")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(10.dp)
                .background(DcColors.Error.copy(alpha = alpha), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text("%d:%02d".format(elapsed / 60, elapsed % 60), fontSize = 15.sp, color = DcColors.OnSurface)
        Spacer(Modifier.width(10.dp))
        Text("Recording… release to send", fontSize = 13.sp, color = DcColors.OnSurfaceFaint)
    }
}

/** The (+) button: image attach (vision models) and/or "Scene image" (when a
 *  text-to-image workflow is configured). */
@Composable
private fun AttachButton(
    enabled: Boolean,
    canAttachImage: Boolean,
    sceneImageEnabled: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onSceneImage: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(DcColors.SurfaceTint, CircleShape)
                .clickable(enabled = enabled) { menuOpen = true },
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Attach", tint = DcColors.Primary, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (canAttachImage) {
                DropdownMenuItem(
                    text = { Text("Choose from gallery", fontSize = 14.sp, color = DcColors.OnSurface) },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = { menuOpen = false; onPickGallery() },
                )
                DropdownMenuItem(
                    text = { Text("Take photo", fontSize = 14.sp, color = DcColors.OnSurface) },
                    leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = { menuOpen = false; onTakePhoto() },
                )
            }
            if (sceneImageEnabled) {
                DropdownMenuItem(
                    text = { Text("Scene image", fontSize = 14.sp, color = DcColors.OnSurface) },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = { menuOpen = false; onSceneImage() },
                )
            }
        }
    }
}

/** Thumbnail of the staged image above the text field, with a remove badge. */
@Composable
private fun PendingImagePreview(file: File, onRemove: () -> Unit) {
    Box(Modifier.padding(bottom = 8.dp)) {
        AsyncImage(
            model = file,
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(18.dp)
                .background(DcColors.OnSurface.copy(alpha = 0.75f), CircleShape)
                .clickable(onClick = onRemove),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = DcColors.Surface, modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * A scene-image placeholder in the transcript: a progress card while the model
 * describes the scene and ComfyUI renders it, an error card with Retry/Delete on
 * failure, or the finished image (tap to open the zoomable viewer). Left-aligned
 * like an assistant message; never sent to the model.
 */
@Composable
private fun SceneImageItem(
    message: ChatMessage,
    file: File?,
    describing: Boolean,
    jobPhase: ComfyJobStatus?,
    revealed: Boolean,
    onReveal: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val meta = message.sceneImage ?: return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(Modifier.fillMaxWidth(0.85f).widthIn(max = 600.dp)) {
            when {
                meta.status == SceneImageMeta.STATUS_DONE && file != null && file.exists() && !revealed -> {
                    SceneSpoilerCover(onReveal = onReveal)
                }
                meta.status == SceneImageMeta.STATUS_DONE && file != null && file.exists() -> {
                    AsyncImage(
                        model = file,
                        contentDescription = "Scene image: ${meta.focus}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpen),
                    )
                }
                meta.status == SceneImageMeta.STATUS_DONE -> SceneImageCard {
                    // Bytes are gone (e.g. an imported backup) — offer regenerate/delete.
                    SceneCardHeader(Icons.Filled.Image, "Image unavailable", DcColors.OnSurfaceMedium)
                    if (meta.focus.isNotBlank()) SceneFocusLabel(meta.focus)
                    SceneCardActions(onRetry, onDelete)
                }
                meta.status == SceneImageMeta.STATUS_FAILED -> SceneImageCard {
                    SceneCardHeader(Icons.Filled.Warning, "Scene image failed", DcColors.Error)
                    Text(
                        meta.error.ifBlank { "Something went wrong." },
                        fontSize = 12.sp,
                        color = DcColors.OnSurfaceFaint,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    SceneCardActions(onRetry, onDelete)
                }
                else -> SceneImageCard {
                    val label = when {
                        describing || meta.status == SceneImageMeta.STATUS_DESCRIBING -> "Describing scene…"
                        jobPhase == ComfyJobStatus.QUEUED -> "Queued"
                        jobPhase == ComfyJobStatus.DOWNLOADING -> "Downloading…"
                        else -> "Generating image…"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = DcColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
                    }
                    if (meta.focus.isNotBlank()) SceneFocusLabel(meta.focus)
                }
            }
        }
    }
}

/** Opaque "tap to reveal" placeholder shown in place of a finished scene image until the
 *  user taps it. A real cover (not a blur) so nothing of the image leaks, and it works on
 *  every supported API level (Modifier.blur is a no-op below API 31). */
@Composable
private fun SceneSpoilerCover(onReveal: () -> Unit) {
    Column(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.SurfaceTint)
            .clickable(onClick = onReveal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = DcColors.OnSurfaceMedium,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("Tap to reveal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
        Text("Scene image", fontSize = 11.sp, color = DcColors.OnSurfaceFaint)
    }
}

@Composable
private fun SceneImageCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DcColors.SurfaceTint, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun SceneCardHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun SceneFocusLabel(focus: String) {
    Text(
        "Focus: $focus",
        fontSize = 12.sp,
        color = DcColors.OnSurfaceFaint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SceneCardActions(onRetry: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.padding(top = 10.dp)) {
        ActionChip("Regenerate", Icons.Filled.Refresh, DcColors.Primary, onRetry)
        Spacer(Modifier.width(6.dp))
        ActionChip("Delete", Icons.Filled.Delete, DcColors.Error, onDelete)
    }
}

/** Asks for the focus of a new scene image before generation starts. */
@Composable
private fun SceneFocusDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scene image", color = DcColors.OnSurface) },
        text = {
            Column {
                Text("What should the image focus on?", fontSize = 13.sp, color = DcColors.OnSurfaceMedium)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(DcColors.SurfaceTint, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = DcColors.OnSurface),
                        cursorBrush = SolidColor(DcColors.Primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (value.isEmpty()) Text("e.g. the campfire, her face…", color = DcColors.OnSurfaceFaint, fontSize = 15.sp)
                            inner()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onGenerate) { Text("Generate", color = DcColors.Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DcColors.OnSurfaceVariant) } },
        containerColor = DcColors.Surface,
    )
}

@Composable
private fun EmptyChat(characterName: String, quickImage: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).background(DcColors.PrimaryContainer, CircleShape),
        ) {
            Icon(
                if (quickImage) Icons.Filled.PhotoCamera else Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = DcColors.Primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            if (quickImage) "Take a photo and ask a question" else "Say hello to $characterName",
            fontSize = 15.sp,
            color = DcColors.OnSurfaceMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        // Text("Your messages stay on this network", fontSize = 13.sp, color = DcColors.OnSurfaceFaint, modifier = Modifier.padding(top = 2.dp))
    }
}
