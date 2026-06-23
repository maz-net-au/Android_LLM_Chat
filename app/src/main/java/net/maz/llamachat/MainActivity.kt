package net.maz.llamachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.maz.llamachat.ui.LlamaChatNavHost
import net.maz.llamachat.ui.theme.LlamaChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Composable
private fun App() {
    LlamaChatTheme {
        // On a phone the app fills the screen (the prototype's 412×880 frame is
        // just its design canvas). safeDrawing pads the union of the system bars
        // and the IME, so content never sits under the status/nav bars and the
        // bottom input on any screen lifts above the on-screen keyboard.
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                LlamaChatNavHost()
            }
        }
    }
}
