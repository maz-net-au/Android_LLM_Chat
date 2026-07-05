package net.maz.llamachat.ui.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.RelativeTime
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.InstalledWorkflow
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.WorkflowPickerViewModel

/** Workflows installed for one [FlowType]; tapping one opens its form. */
@Composable
fun WorkflowPickerScreen(
    vm: WorkflowPickerViewModel,
    flowType: FlowType,
    onSelect: (Long) -> Unit,
    onOpenGallery: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val workflows by vm.workflows.collectAsStateWithLifecycle()
    val activeJobs by vm.activeJobCount.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = flowType.label, onBack = onBack, onOpenSettings = onOpenSettings)

        if (activeJobs > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenGallery)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = DcColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "$activeJobs job${if (activeJobs == 1) "" else "s"} in progress",
                    color = DcColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (workflows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "No workflows installed for ${flowType.label}",
                    color = DcColors.OnSurfaceFaint,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DcColors.Primary,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Add one in Settings", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(workflows, key = { it.id }) { wf ->
                    WorkflowRow(wf) { onSelect(wf.id) }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRow(wf: InstalledWorkflow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DcColors.SurfaceTint)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(wf.name, color = DcColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "Installed ${RelativeTime.format(wf.installedAt)}",
                color = DcColors.OnSurfaceFaint,
                fontSize = 12.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DcColors.OnSurfaceFaint,
        )
    }
}
