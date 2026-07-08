package net.maz.llamachat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.net.ProbeStatus
import net.maz.llamachat.data.net.ServerHealth
import net.maz.llamachat.ui.theme.DcColors

/** Live server health, provided once at the app root so every bar can show the dot. */
val LocalServerHealth = staticCompositionLocalOf<StateFlow<ServerHealth>> {
    error("LocalServerHealth not provided")
}

/** An action collapsed into the app bar's overflow menu. */
class AppBarMenuItem(
    val icon: ImageVector,
    val label: String,
    val iconTint: Color,
    val textColor: Color,
    val onClick: () -> Unit,
)

/**
 * The app-wide top bar: back button, title (or custom [titleContent]), the server
 * status dot, and an overflow menu holding every other action for the screen.
 */
@Composable
fun DcAppBar(
    title: String = "",
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    menuItems: List<AppBarMenuItem> = emptyList(),
    titleContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DcColors.Primary)
            .height(56.dp)
            .padding(start = if (onBack == null) 16.dp else 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        if (titleContent != null) {
            titleContent()
        } else {
            Text(
                title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 0.dp else 10.dp),
            )
        }
        ServerStatusIndicator(onOpenSettings = onOpenSettings)
        if (menuItems.isNotEmpty()) {
            OverflowMenu(menuItems)
        }
    }
}

@Composable
private fun OverflowMenu(items: List<AppBarMenuItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, fontSize = 14.sp, color = item.textColor) },
                    leadingIcon = { Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(19.dp)) },
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}

fun statusDotColor(overall: ServerHealth.Overall): Color = when (overall) {
    ServerHealth.Overall.ALL_UP -> Color(0xFF66BB6A)
    ServerHealth.Overall.DEGRADED, ServerHealth.Overall.UNKNOWN -> Color(0xFFFFCA28)
    ServerHealth.Overall.DOWN -> Color(0xFFEF5350)
}

private fun statusDotColor(status: ProbeStatus): Color = when (status) {
    ProbeStatus.UP -> Color(0xFF66BB6A)
    ProbeStatus.UNKNOWN -> Color(0xFFFFCA28)
    ProbeStatus.DOWN -> Color(0xFFEF5350)
}

/**
 * The combined-health dot shown in every app bar. Tapping it re-probes and opens
 * a per-server breakdown, with a shortcut to the settings screen when the host
 * screen provides one.
 */
@Composable
private fun ServerStatusIndicator(onOpenSettings: (() -> Unit)?) {
    val health by LocalServerHealth.current.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as LlamaChatApp
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable {
                    app.healthMonitor.refreshNow()
                    open = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusDotColor(health.overall)),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ServerStatusRow("llama-server", health.llama)
            ServerStatusRow("ComfyUI", health.comfy)
            if (onOpenSettings != null) {
                HorizontalDivider(color = DcColors.Divider)
                DropdownMenuItem(
                    text = { Text("Server settings", fontSize = 14.sp, color = DcColors.OnSurface) },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = { open = false; onOpenSettings() },
                )
            }
        }
    }
}

@Composable
fun ServerStatusRow(
    name: String,
    status: ProbeStatus,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .then(if (trailing != null) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusDotColor(status)))
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 14.sp, color = DcColors.OnSurface, modifier = Modifier.width(110.dp))
        Text(
            when (status) {
                ProbeStatus.UP -> "Online"
                ProbeStatus.DOWN -> "Offline"
                ProbeStatus.UNKNOWN -> "Checking…"
            },
            fontSize = 13.sp,
            color = DcColors.OnSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
