package net.maz.llamachat.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    onOpenItem: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()

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
                    Thumbnail(vm, item) { onOpenItem(item.id) }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(vm: GalleryViewModel, item: GalleryItemEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(DcColors.SurfaceTint)
            .clickable(onClick = onClick),
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
    }
}
