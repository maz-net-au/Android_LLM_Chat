package net.maz.llamachat.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors

/** Entry screen: one tile per major feature. Generation tiles are placeholders
 *  until their ComfyUI-backed screens exist. */
@Composable
fun LauncherScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = "llama chat", onOpenSettings = onOpenSettings)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Outlined.Forum, "Chat", enabled = true, onClick = onOpenChat)
                LauncherTile(Icons.Filled.Image, "Image Generation", enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Filled.GraphicEq, "Audio Generation", enabled = false)
                LauncherTile(Icons.Filled.Movie, "Video Generation", enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Filled.Settings, "Settings", enabled = true, onClick = onOpenSettings)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.LauncherTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(18.dp))
            .background(DcColors.SurfaceTint)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            color = DcColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        if (!enabled) {
            Text("Coming soon", color = DcColors.OnSurfaceFaint, fontSize = 11.sp)
        }
    }
}
