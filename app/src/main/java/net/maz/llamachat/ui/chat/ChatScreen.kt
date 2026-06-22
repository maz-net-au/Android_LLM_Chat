package net.maz.llamachat.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.Role
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.components.MarkdownText
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ChatViewModel

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onEditDetails: (Long) -> Unit,
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val conv = state.conversation

    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    val character = conv?.character
    val messages = conv?.messages ?: emptyList()
    val lastAssistantIndex = messages.indexOfLast { it.role == Role.ASSISTANT }
    val lastIsAssistant = messages.lastOrNull()?.role == Role.ASSISTANT
    val showActions = state.streaming || lastIsAssistant

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        ChatAppBar(
            characterName = character?.name ?: "",
            characterColor = character?.color ?: DcColors.Primary,
            modelShort = Catalog.shortModel(conv?.model ?: ""),
            menuOpen = state.chatMenuOpen,
            onBack = onBack,
            onToggleMenu = vm::toggleChatMenu,
            onDismissMenu = vm::closeChatMenu,
            onEditDetails = { conv?.let { onEditDetails(it.id) } },
            onRegenerate = vm::regenerate,
            onClear = vm::clearMessages,
            onDelete = vm::deleteConversation,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                EmptyChat(character?.name ?: "your assistant")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 8.dp),
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                        MessageItem(
                            message = message,
                            editing = state.editingMsgId == message.id,
                            selected = state.selectedMsgId == message.id,
                            isLastAssistant = index == lastAssistantIndex,
                            streamingCaret = state.streaming && index == messages.lastIndex && message.role == Role.ASSISTANT,
                            editText = state.editText,
                            onTap = { vm.toggleSelected(message.id) },
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
                regenerateEnabled = !state.streaming && lastIsAssistant,
                onStop = vm::stop,
                onRegenerate = vm::regenerate,
            )
        }

        InputBar(
            input = state.input,
            sendEnabled = state.input.isNotBlank() && !state.streaming,
            onInputChange = vm::setInput,
            onSend = vm::send,
        )
    }
}

@Composable
private fun ChatAppBar(
    characterName: String,
    characterColor: Color,
    modelShort: String,
    menuOpen: Boolean,
    onBack: () -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onEditDetails: () -> Unit,
    onRegenerate: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DcColors.Primary).height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Avatar(initial = characterName, color = characterColor, size = 34.dp, fontSize = 15.sp, bordered = true, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(characterName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(modelShort, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = onToggleMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                ChatMenuItem(Icons.Filled.EditNote, "Edit chat details", DcColors.OnSurfaceVariant, DcColors.OnSurface, onEditDetails)
                ChatMenuItem(Icons.Filled.Refresh, "Regenerate reply", DcColors.OnSurfaceVariant, DcColors.OnSurface, onRegenerate)
                ChatMenuItem(Icons.Filled.DeleteSweep, "Clear messages", DcColors.OnSurfaceVariant, DcColors.OnSurface, onClear)
                ChatMenuItem(Icons.Filled.Delete, "Delete conversation", DcColors.Error, DcColors.Error, onDelete)
            }
        }
    }
}

@Composable
private fun ChatMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, color = textColor) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    editing: Boolean,
    selected: Boolean,
    isLastAssistant: Boolean,
    streamingCaret: Boolean,
    editText: String,
    onTap: () -> Unit,
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
            modifier = if (editing) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (editing) {
                EditBox(editText, onEditTextChange, onSaveEdit, onCancelEdit)
            } else {
                MessageBubble(message, isUser, streamingCaret, onTap)
                if (!isUser && message.variantCount > 1) {
                    VariantNav(message, onPrevVariant, onNextVariant)
                }
                if (selected) {
                    SelectedActions(isLastAssistant, onStartEdit, onContinue, onDelete)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isUser: Boolean, streamingCaret: Boolean, onTap: () -> Unit) {
    val shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(0.dp)
    val bg = if (isUser) DcColors.PrimaryContainer else Color.Transparent
    Column(
        modifier = Modifier
            .background(bg, shape)
            .clickable(onClick = onTap)
            .padding(if (isUser) PaddingUser else PaddingAssistant),
    ) {
        if (message.text.isNotEmpty()) {
            MarkdownText(message.text)
        }
        if (streamingCaret) {
            BlinkingCaret()
        }
    }
}

private val PaddingUser = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val PaddingAssistant = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp)

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
private fun SelectedActions(isLastAssistant: Boolean, onEdit: () -> Unit, onContinue: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.padding(top = 5.dp)) {
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
private fun ActionRow(streaming: Boolean, regenerateEnabled: Boolean, onStop: () -> Unit, onRegenerate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        PillButton("Stop", Icons.Filled.Stop, enabled = streaming, borderColor = DcColors.OnSurface.copy(alpha = 0.2f), contentColor = DcColors.OnSurface.copy(alpha = 0.75f), onClick = onStop)
        Spacer(Modifier.width(8.dp))
        PillButton("Regenerate", Icons.Filled.Refresh, enabled = regenerateEnabled, borderColor = DcColors.Primary, contentColor = DcColors.Primary, onClick = onRegenerate)
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, borderColor: Color, contentColor: Color, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(18.dp))
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
private fun InputBar(input: String, sendEnabled: Boolean, onInputChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().border(0.dp, Color.Transparent).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(DcColors.SurfaceTint, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = DcColors.OnSurface, lineHeight = 21.sp),
                cursorBrush = SolidColor(DcColors.Primary),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Message…", color = DcColors.OnSurfaceFaint, fontSize = 15.sp)
                    inner()
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(if (sendEnabled) DcColors.Primary else Color(0xFFCFC7E3), CircleShape)
                .clickable(enabled = sendEnabled, onClick = onSend),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun EmptyChat(characterName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).background(DcColors.PrimaryContainer, CircleShape),
        ) {
            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(32.dp))
        }
        Text("Say hello to $characterName", fontSize = 15.sp, color = DcColors.OnSurfaceMedium, modifier = Modifier.padding(top = 12.dp))
        Text("Your messages stay on this device", fontSize = 13.sp, color = DcColors.OnSurfaceFaint, modifier = Modifier.padding(top = 2.dp))
    }
}
