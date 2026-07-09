package net.maz.llamachat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.net.ProbeStatus
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.DcDropdown
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.components.ServerStatusRow
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.SettingsViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(title = "Settings", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                "Server",
                color = DcColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            DcTextField(
                label = "Server address",
                value = state.ip,
                onValueChange = vm::setIp,
                placeholder = "192.168.1.42",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            DcTextField(
                label = "llama-server port",
                value = state.llamaPort,
                onValueChange = vm::setLlamaPort,
                placeholder = "8080",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            DcTextField(
                label = "ComfyUI port",
                value = state.comfyPort,
                onValueChange = vm::setComfyPort,
                placeholder = "8188",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = vm::save,
                enabled = state.ip.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.Primary,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (state.saved) "Saved" else "Save", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Status",
                color = DcColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            ServerStatusRow("llama-server", health.llama) {
                UnloadButton(
                    loading = state.unloadingLlama,
                    enabled = health.llama == ProbeStatus.UP && !state.unloadingLlama,
                    onClick = vm::unloadLlama,
                )
            }
            ServerStatusRow("ComfyUI", health.comfy) {
                UnloadButton(
                    loading = state.unloadingComfy,
                    enabled = health.comfy == ProbeStatus.UP && !state.unloadingComfy,
                    onClick = vm::unloadComfy,
                )
            }
            state.unloadStatus?.let { msg ->
                Text(
                    msg,
                    color = if (state.unloadStatusIsError) DcColors.Error else DcColors.OnSurfaceFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "Character generation",
                color = DcColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            DcDropdown(
                label = "Model",
                value = state.characterGenModel,
                options = vm.modelOptions(state.characterGenModel),
                onSelect = vm::setCharacterGenModel,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))
            Text(
                "Image to text",
                color = DcColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            DcDropdown(
                label = "Model",
                value = state.imageToTextModel,
                options = vm.modelOptions(state.imageToTextModel),
                onSelect = vm::setImageToTextModel,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            DcDropdown(
                label = "Character",
                value = state.imageToTextCharacter,
                options = vm.characterOptions(),
                onSelect = vm::setImageToTextCharacter,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            DcDropdown(
                label = "Preset",
                value = state.imageToTextPreset,
                options = vm.presetOptions(),
                onSelect = vm::setImageToTextPreset,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))
            WorkflowSection(vm)
        }
    }
}

@Composable
private fun UnloadButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = DcColors.SurfaceTint,
            contentColor = DcColors.Primary,
            disabledContainerColor = DcColors.SurfaceTint.copy(alpha = 0.5f),
            disabledContentColor = DcColors.OnSurfaceFaint,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            if (loading) "Unloading…" else "Unload",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
