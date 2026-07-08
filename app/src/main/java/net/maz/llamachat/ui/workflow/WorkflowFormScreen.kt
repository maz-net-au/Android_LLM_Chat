package net.maz.llamachat.ui.workflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.maz.llamachat.data.comfy.FieldType
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.net.ProbeStatus
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.DcDropdown
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.components.LocalServerHealth
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.FormField
import net.maz.llamachat.vm.WorkflowFormViewModel

/**
 * The native form generated from a workflow's config: one control per field,
 * defaults from the graph, and a Generate button that hands the job to the
 * background service.
 */
@Composable
fun WorkflowFormScreen(
    vm: WorkflowFormViewModel,
    onSubmitted: (FlowType) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val submitError by vm.submitError.collectAsStateWithLifecycle()
    val health by LocalServerHealth.current.collectAsStateWithLifecycle()
    val comfyUp = health.comfy == ProbeStatus.UP

    LaunchedEffect(state.submitted) {
        if (state.submitted) onSubmitted(state.flowType)
    }

    // One picker pair shared by every file field; the index routes the result.
    var pickTarget by remember { mutableStateOf(-1) }
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (pickTarget >= 0) vm.setFile(pickTarget, uri) }
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (pickTarget >= 0 && uri != null) vm.setFile(pickTarget, uri) }

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = state.workflowName, onBack = onBack, onOpenSettings = onOpenSettings)

        state.loadError?.let { err ->
            Text(
                err,
                color = DcColors.Error,
                fontSize = 14.sp,
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            if (!state.description.isNullOrBlank()) {
                Text(state.description.orEmpty(), color = DcColors.OnSurfaceFaint, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
            }

            state.fields.forEachIndexed { index, field ->
                FieldControl(
                    field = field,
                    onValueChange = { vm.setValue(index, it) },
                    onRandomize = { vm.randomizeSeed(index) },
                    onPickFile = {
                        pickTarget = index
                        when (field.spec.fileKind) {
                            "audio" -> audioPicker.launch(arrayOf("audio/*"))
                            "video" -> mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                            else -> mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!comfyUp) {
                Text(
                    "ComfyUI is not reachable — check Settings",
                    color = DcColors.Error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            submitError?.let {
                Text(it, color = DcColors.Error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Button(
                onClick = vm::generate,
                enabled = comfyUp && !state.submitting && state.fields.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.Primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    if (state.submitting) "Starting…" else "Generate",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FieldControl(
    field: FormField,
    onValueChange: (String) -> Unit,
    onRandomize: () -> Unit,
    onPickFile: () -> Unit,
) {
    when {
        field.configError != null -> {
            Column {
                Text(field.label, color = DcColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(field.configError, color = DcColors.Error, fontSize = 13.sp)
            }
            return
        }
        field.type == FieldType.ENUM -> DcDropdown(
            label = field.label,
            value = field.value,
            options = field.options,
            onSelect = onValueChange,
            modifier = Modifier.fillMaxWidth(),
        )
        field.type == FieldType.FILE -> FilePickerRow(field, onPickFile)
        field.type == FieldType.BOOL -> BoolSwitchRow(field, onValueChange)
        field.type == FieldType.SEED -> SeedRow(field, onValueChange, onRandomize)
        else -> DcTextField(
            label = field.label,
            value = field.value,
            onValueChange = onValueChange,
            keyboardType = when (field.type) {
                FieldType.INT -> KeyboardType.Number
                FieldType.FLOAT -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
            singleLine = field.type != FieldType.STRING,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SupportingText(field)
}

@Composable
private fun BoolSwitchRow(field: FormField, onValueChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            field.label,
            color = DcColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(
            checked = field.value.toBoolean(),
            onCheckedChange = { onValueChange(it.toString()) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DcColors.Primary,
            ),
        )
    }
}

@Composable
private fun SeedRow(
    field: FormField,
    onValueChange: (String) -> Unit,
    onRandomize: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DcTextField(
            label = field.label,
            value = field.value,
            onValueChange = onValueChange,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onRandomize,
            colors = ButtonDefaults.buttonColors(
                containerColor = DcColors.SurfaceTint,
                contentColor = DcColors.OnSurface,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text("🎲", fontSize = 16.sp)
        }
    }
}

@Composable
private fun SupportingText(field: FormField) {
    val error = field.error
    when {
        error != null -> Text(
            error,
            color = DcColors.Error,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        !field.spec.description.isNullOrBlank() -> Text(
            field.spec.description.orEmpty(),
            color = DcColors.OnSurfaceFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FilePickerRow(field: FormField, onPickFile: () -> Unit) {
    Column {
        Text(field.label, color = DcColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (field.fileUri != null && field.spec.fileKind != "audio") {
                AsyncImage(
                    model = field.fileUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DcColors.SurfaceTint),
                )
                Spacer(Modifier.width(12.dp))
            }
            Button(
                onClick = onPickFile,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.SurfaceTint,
                    contentColor = DcColors.OnSurface,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (field.fileUri == null) "Choose…" else "Change…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
