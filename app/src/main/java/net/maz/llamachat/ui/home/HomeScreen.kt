package net.maz.llamachat.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.RelativeTime
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.ui.components.AppBarMenuItem
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.HomeViewModel
import net.maz.llamachat.vm.ImportResult

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onEditConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onManageCharacters: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Which conversation's long-press menu is open (null = none), and which is queued
    // for export/delete once the user picks a file or confirms.
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var pendingExport by remember { mutableStateOf<Conversation?>(null) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }

    // Restore a conversation from a backup JSON file (best-effort, overwrites by id).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(context, "Couldn't read file", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        vm.import(text) { result ->
            val msg = when (result) {
                is ImportResult.Restored ->
                    if (result.missingCharacter != null)
                        "Restored “${result.title}” — character ‘${result.missingCharacter}’ not found"
                    else "Restored “${result.title}”"
                ImportResult.Failed -> "Not a valid conversation backup"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Export a long-pressed conversation to a user-picked JSON file.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val conv = pendingExport
        pendingExport = null
        if (uri == null || conv == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson(conv).toByteArray()) }
        }.onSuccess {
            Toast.makeText(context, "Conversation exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(
            title = "Conversations",
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            menuItems = listOf(
                AppBarMenuItem(Icons.Filled.FileUpload, "Import conversation", DcColors.OnSurfaceVariant, DcColors.OnSurface) {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                },
                AppBarMenuItem(Icons.Filled.ManageAccounts, "Characters", DcColors.OnSurfaceVariant, DcColors.OnSurface, onManageCharacters),
            ),
        )

        Box(Modifier.fillMaxSize()) {
            if (state.conversations.isEmpty()) {
                EmptyConversations()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conv = conv,
                            menuOpen = menuFor == conv.id,
                            onClick = { onOpenConversation(conv.id) },
                            onLongPress = { menuFor = conv.id },
                            onDismissMenu = { menuFor = null },
                            onEdit = { menuFor = null; onEditConversation(conv.id) },
                            onExport = {
                                menuFor = null
                                pendingExport = conv
                                exportLauncher.launch("${conv.title.ifBlank { "conversation" }}.json")
                            },
                            onDelete = { menuFor = null; pendingDelete = conv },
                        )
                        HorizontalDivider(color = DcColors.Divider)
                    }
                }
            }
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = DcColors.Primary,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New conversation", modifier = Modifier.size(28.dp))
            }
        }
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("“${conv.title}” and its messages will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    vm.delete(conv.id)
                    Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
                }) { Text("Delete", color = DcColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conv: Conversation,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (menuOpen) DcColors.SurfaceTint else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(initial = conv.characterName, color = conv.character.color, size = 42.dp, fontSize = 17.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        conv.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = DcColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(RelativeTime.format(conv.updatedAt), fontSize = 12.sp, color = DcColors.OnSurfaceFaint)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(conv.characterName, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = DcColors.Primary, maxLines = 1)
                }
                Text(
                    conv.preview(),
                    fontSize = 13.sp,
                    color = DcColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("Edit details", fontSize = 14.sp, color = DcColors.OnSurface) },
                leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                onClick = onEdit,
            )
            DropdownMenuItem(
                text = { Text("Export conversation", fontSize = 14.sp, color = DcColors.OnSurface) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                onClick = onExport,
            )
            HorizontalDivider(color = DcColors.Divider)
            DropdownMenuItem(
                text = { Text("Delete conversation", fontSize = 14.sp, color = DcColors.Error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DcColors.Error, modifier = Modifier.size(19.dp)) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun EmptyConversations() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Forum, contentDescription = null, tint = Color(0xFFD6CDEC), modifier = Modifier.size(64.dp))
        Text(
            "No conversations yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = DcColors.OnSurfaceMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Tap the + button to start chatting",
            fontSize = 13.sp,
            color = DcColors.OnSurfaceFaint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
