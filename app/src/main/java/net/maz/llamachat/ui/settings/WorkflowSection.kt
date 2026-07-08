package net.maz.llamachat.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.SettingsViewModel

/**
 * "Media generation" settings section: install a workflow zip
 * (api_workflow.json + workflow_config.json), install the shared base enums
 * file, and manage the installed workflow list.
 */
@Composable
fun WorkflowSection(vm: SettingsViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val baseEnumCount by vm.baseEnumCount.collectAsStateWithLifecycle()

    // SAF providers disagree on the mime for zips; accept the usual aliases.
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importWorkflow)
    }
    val enumsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBaseEnums)
    }

    Text(
        "Media generation",
        color = DcColors.Primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
    Spacer(Modifier.height(12.dp))

    SectionButton("Install workflow (.zip)") {
        zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    }
    Spacer(Modifier.height(10.dp))
    SectionButton("Install base enums (.json)") {
        enumsPicker.launch(arrayOf("application/json"))
    }
    Text(
        if (baseEnumCount == 0) "No base enum lists installed"
        else "$baseEnumCount enum list${if (baseEnumCount == 1) "" else "s"} installed",
        color = DcColors.OnSurfaceFaint,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp),
    )

    state.workflowStatus?.let { status ->
        Text(
            status,
            color = if (state.workflowStatusIsError) DcColors.Error else DcColors.Primary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    state.pendingImport?.let { parsed ->
        InstallDialog(
            name = state.importName,
            description = parsed.config.description,
            onNameChange = vm::setImportName,
            onConfirm = vm::confirmImport,
            onDismiss = vm::cancelImport,
        )
    }
}

/** Name + flow-type chooser shown after a zip validates. */
@Composable
private fun InstallDialog(
    name: String,
    description: String?,
    onNameChange: (String) -> Unit,
    onConfirm: (FlowType) -> Unit,
    onDismiss: () -> Unit,
) {
    var flowType by remember { mutableStateOf(FlowType.TEXT_TO_IMAGE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install workflow") },
        text = {
            Column {
                if (!description.isNullOrBlank()) {
                    Text(description, color = DcColors.OnSurfaceFaint, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                }
                DcTextField(
                    label = "Name",
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Shown under", color = DcColors.OnSurfaceFaint, fontSize = 12.sp)
                FlowType.entries.forEach { ft ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { flowType = ft },
                    ) {
                        RadioButton(selected = flowType == ft, onClick = { flowType = ft })
                        Spacer(Modifier.width(4.dp))
                        Text(ft.label, color = DcColors.OnSurface, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(flowType) }, enabled = name.isNotBlank()) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Full-width secondary action button matching the settings look. */
@Composable
private fun SectionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DcColors.SurfaceTint,
            contentColor = DcColors.OnSurface,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
