package net.maz.llamachat.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.RelativeTime
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.HomeViewModel

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onManageCharacters: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DcColors.Primary)
                .height(56.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Conversations",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.15.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onManageCharacters) {
                Icon(Icons.Filled.ManageAccounts, contentDescription = "Characters", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        ModelSelector(
            current = Catalog.shortModel(state.currentModel),
            models = state.models,
            selected = state.currentModel,
            onSelect = vm::selectModel,
        )
        HorizontalDivider(color = DcColors.Divider)

        Box(Modifier.fillMaxSize()) {
            if (state.conversations.isEmpty()) {
                EmptyConversations()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.id }) { conv ->
                        ConversationRow(conv) { onOpenConversation(conv.id) }
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
}

@Composable
private fun ModelSelector(
    current: String,
    models: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DcColors.SurfaceTint, RoundedCornerShape(6.dp))
                .border(1.dp, DcColors.Outline, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Model", fontSize = 11.sp, color = DcColors.OnSurfaceVariant)
                Text(current, fontSize = 14.sp, color = DcColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (model == selected) DcColors.Primary else Color.Transparent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(Catalog.shortModel(model), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = { onSelect(model); open = false },
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
