package net.maz.llamachat.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.RelativeTime
import net.maz.llamachat.data.model.Conversation
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ConnStatus
import net.maz.llamachat.vm.HomeViewModel

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onManageCharacters: () -> Unit,
    onOpenServer: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Re-probe the saved server each time the conversations screen is shown.
    LaunchedEffect(Unit) { vm.refreshConnection() }

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
            ConnectionChip(status = state.connection, onClick = onOpenServer)
            IconButton(onClick = onManageCharacters) {
                Icon(Icons.Filled.ManageAccounts, contentDescription = "Characters", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

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
private fun ConnectionChip(status: ConnStatus, onClick: () -> Unit) {
    val (dot, label) = when (status) {
        ConnStatus.CONNECTED -> Color(0xFF66BB6A) to "Online"
        ConnStatus.CONNECTING -> Color(0xFFFFCA28) to "Connecting"
        ConnStatus.OFFLINE -> Color(0xFFEF5350) to "Offline"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
