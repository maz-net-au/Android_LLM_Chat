package net.maz.llamachat.ui.gallery

import android.media.MediaPlayer
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.db.GalleryItemEntity
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.ZoomableImage
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.GalleryViewModel

/**
 * Full-screen view of one gallery item with Save (export to the system
 * media collections) and Delete. Video plays via a plain [VideoView], audio
 * via a minimal [MediaPlayer] toggle — both adequate for local files without
 * pulling Media3 into the pinned toolchain.
 */
@Composable
fun ViewerScreen(
    vm: GalleryViewModel,
    itemId: Long,
    onRegenerate: (ComfyJob) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val item by produceState<GalleryItemEntity?>(null, itemId) { value = vm.getItem(itemId) }
    val regenJob = remember(item) { item?.let { vm.regenerableJob(it.id) } }
    val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    // MediaStore is permissionless from API 29; 26–28 needs the legacy write
    // permission granted at the moment of export.
    val current = item
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) current?.let(vm::export) }

    DisposableEffect(Unit) {
        onDispose { vm.clearExportMessage() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        DcAppBar(title = current?.workflowName ?: "", onBack = onBack, onOpenSettings = onOpenSettings)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                current == null -> Text("Not found", color = Color.White, fontSize = 15.sp)
                current.mimeType.startsWith("image/") -> ZoomableImage(
                    model = vm.fileFor(current),
                    contentDescription = current.workflowName,
                )
                current.mimeType.startsWith("video/") -> VideoPlayer(vm.fileFor(current))
                else -> AudioPlayer(vm.fileFor(current))
            }
        }

        exportMessage?.let {
            Text(
                it,
                color = if (it.startsWith("Saved")) DcColors.Primary else DcColors.Error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (current != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                regenJob?.let { job ->
                    Button(
                        onClick = { onRegenerate(job) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DcColors.SurfaceTint,
                            contentColor = DcColors.Primary,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Regenerate", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                vm.export(current)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DcColors.Primary,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?") },
            text = { Text("The file will be removed from the app. Exported copies are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(current) { onBack() }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun VideoPlayer(file: File) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.path)
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { it.isLooping = false; start() }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun AudioPlayer(file: File) {
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val player = remember {
        MediaPlayer().apply {
            runCatching {
                setDataSource(file.path)
                prepare()
            }
        }
    }
    DisposableEffect(Unit) {
        player.setOnCompletionListener { playing = false }
        ready = true
        onDispose { player.release() }
    }
    IconButton(
        onClick = {
            if (playing) player.pause() else player.start()
            playing = !playing
        },
        enabled = ready,
        modifier = Modifier.size(88.dp),
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(72.dp),
        )
    }
}
