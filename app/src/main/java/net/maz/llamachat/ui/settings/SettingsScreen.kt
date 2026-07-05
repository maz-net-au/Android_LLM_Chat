package net.maz.llamachat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import net.maz.llamachat.ui.components.DcAppBar
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
            ServerStatusRow("llama-server", health.llama)
            ServerStatusRow("ComfyUI", health.comfy)

            Spacer(Modifier.height(28.dp))
            WorkflowSection(vm)
        }
    }
}
