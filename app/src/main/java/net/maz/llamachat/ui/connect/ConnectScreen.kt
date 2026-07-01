package net.maz.llamachat.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ConnectViewModel

@Composable
fun ConnectScreen(vm: ConnectViewModel, onConnected: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.connected) {
        if (state.connected) {
            onConnected()
            vm.consumedNavigation()
        }
    }

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        ConnectAppBar()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(84.dp)
                        .background(DcColors.PrimaryContainer, CircleShape)
                        .padding(bottom = 0.dp),
                ) {
                    Icon(Icons.Outlined.Dns, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Connect to server", fontSize = 21.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
                Text(
                    "Enter your llama-server address",
                    fontSize = 14.sp,
                    color = DcColors.OnSurfaceMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            DcTextField(
                label = "Server IP address",
                value = state.ip,
                onValueChange = vm::setIp,
                placeholder = "192.168.1.10",
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            DcTextField(
                label = "Port",
                value = state.port,
                onValueChange = vm::setPort,
                placeholder = "8080",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )

            Button(
                onClick = vm::connect,
                enabled = !state.connecting,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DcColors.Primary,
                    contentColor = Color.White,
                    disabledContainerColor = DcColors.Primary.copy(alpha = 0.7f),
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                if (state.connecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp).padding(end = 0.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = if (state.connecting) "Connecting…" else "Connect",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 0.9.sp,
                )
            }

            state.error?.let { error ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = DcColors.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(error, color = DcColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ConnectAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DcColors.Primary)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Memory, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(24.dp))
        Text("llama chat", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp)
    }
}
