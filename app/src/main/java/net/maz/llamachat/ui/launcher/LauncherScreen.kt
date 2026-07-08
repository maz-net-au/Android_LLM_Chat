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
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors

/** Entry screen: one tile per major feature. The generation tiles each open a
 *  ComfyUI workflow picker for their flow type. Settings is a primary-styled
 *  action pinned to the bottom of the screen. */
@Composable
fun LauncherScreen(
    onOpenChat: () -> Unit,
    onImageToText: () -> Unit,
    onOpenFlow: (FlowType) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = "PrivateAI", onOpenSettings = onOpenSettings)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Outlined.Forum, "Chat", onClick = onOpenChat)
                LauncherTile(Icons.Filled.ImageSearch, "Image to Text", onClick = onImageToText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Filled.Image, FlowType.TEXT_TO_IMAGE.label) {
                    onOpenFlow(FlowType.TEXT_TO_IMAGE)
                }
                LauncherTile(Icons.Filled.Transform, FlowType.IMAGE_TO_IMAGE.label) {
                    onOpenFlow(FlowType.IMAGE_TO_IMAGE)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Filled.Movie, FlowType.TEXT_TO_VIDEO.label) {
                    onOpenFlow(FlowType.TEXT_TO_VIDEO)
                }
                LauncherTile(Icons.Filled.Animation, FlowType.IMAGE_TO_VIDEO.label) {
                    onOpenFlow(FlowType.IMAGE_TO_VIDEO)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LauncherTile(Icons.Filled.GraphicEq, FlowType.TEXT_TO_AUDIO.label) {
                    onOpenFlow(FlowType.TEXT_TO_AUDIO)
                }
                LauncherTile(Icons.Filled.Collections, "Gallery", onClick = onOpenGallery)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenQueue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.SurfaceTint,
                    contentColor = DcColors.Primary,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Queue, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Queue & History", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.Primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Settings", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RowScope.LauncherTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(18.dp))
            .background(DcColors.SurfaceTint)
            .clickable(onClick = onClick)
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
    }
}
