package net.maz.llamachat.ui.gallery

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.db.GalleryItemEntity
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.GalleryViewModel

/** Generated media, tabbed by flow type, with in-flight jobs listed on top. */
@Composable
fun GalleryScreen(
    vm: GalleryViewModel,
    onOpenItem: (Long, FlowType?) -> Unit,
    onRegenerate: (GalleryItemEntity) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Which thumbnail's long-press menu is open, and the item queued for a delete
    // confirmation. Both keyed by id so they survive list reordering.
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<GalleryItemEntity?>(null) }

    // MediaStore export is permissionless from API 29; 26–28 needs the legacy write
    // permission granted at the moment of export, so hold the item until it's granted.
    var pendingExport by remember { mutableStateOf<GalleryItemEntity?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingExport?.let(vm::export)
        pendingExport = null
    }
    fun save(item: GalleryItemEntity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingExport = item
            permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.export(item)
        }
    }

    // The grid has no room for the inline export note the viewer shows, so toast it.
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearExportMessage()
        }
    }

    val tabs: List<FlowType?> = listOf(null) + FlowType.entries

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = "Gallery", onBack = onBack, onOpenSettings = onOpenSettings)

        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
            containerColor = DcColors.Surface,
            contentColor = DcColors.Primary,
            edgePadding = 8.dp,
        ) {
            tabs.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { vm.selectTab(t) },
                    text = {
                        Text(
                            t?.label ?: "All",
                            fontSize = 13.sp,
                            color = if (tab == t) DcColors.Primary else DcColors.OnSurfaceFaint,
                        )
                    },
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing generated yet", color = DcColors.OnSurfaceFaint, fontSize = 15.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    Thumbnail(
                        vm = vm,
                        item = item,
                        menuOpen = menuFor == item.id,
                        canRegenerate = vm.canRegenerate(item),
                        onClick = { onOpenItem(item.id, tab) },
                        onLongPress = { menuFor = item.id },
                        onDismissMenu = { menuFor = null },
                        onSave = { menuFor = null; save(item) },
                        onRegenerate = { menuFor = null; onRegenerate(item) },
                        onDelete = { menuFor = null; confirmDelete = item },
                    )
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = DcColors.Surface,
            title = { Text("Delete this item?", color = DcColors.OnSurface) },
            text = {
                Text(
                    "The file will be removed from the app. Exported copies are kept.",
                    color = DcColors.OnSurfaceMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; vm.delete(item) }) {
                    Text("Delete", color = DcColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = DcColors.OnSurfaceVariant)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumbnail(
    vm: GalleryViewModel,
    item: GalleryItemEntity,
    menuOpen: Boolean,
    canRegenerate: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onSave: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(DcColors.SurfaceTint)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            item.mimeType.startsWith("image/") -> AsyncImage(
                model = vm.fileFor(item),
                contentDescription = item.workflowName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.mimeType.startsWith("video/") -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(vm.fileFor(item))
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .build(),
                    contentDescription = item.workflowName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = DcColors.OnSurfaceFaint,
                    modifier = Modifier.size(20.dp).align(Alignment.BottomEnd).padding(4.dp),
                )
            }
            else -> Icon(
                Icons.Filled.GraphicEq,
                contentDescription = item.workflowName,
                tint = DcColors.Primary,
                modifier = Modifier.size(36.dp),
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("Save", fontSize = 14.sp, color = DcColors.OnSurface) },
                leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                onClick = onSave,
            )
            if (canRegenerate) {
                DropdownMenuItem(
                    text = { Text("Regenerate", fontSize = 14.sp, color = DcColors.OnSurface) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = onRegenerate,
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", fontSize = 14.sp, color = DcColors.Error) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DcColors.Error, modifier = Modifier.size(19.dp)) },
                onClick = onDelete,
            )
        }
    }
}
