package net.maz.llamachat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.maz.llamachat.ui.LlamaChatNavHost
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.ui.theme.LlamaChatTheme

class MainActivity : ComponentActivity() {

    // Result is ignored: if the user declines, generation still runs in the
    // foreground service — only its (Cancel-bearing) notification stays hidden.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent { App() }
    }

    /** Android 13+ needs runtime consent to show the generation notification. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun App() {
    LlamaChatTheme {
        // On a phone the app fills the screen (the prototype's 412×880 frame is
        // just its design canvas). safeDrawing pads the union of the system bars
        // and the IME, so content never sits under the status/nav bars and the
        // bottom input on any screen lifts above the on-screen keyboard.
        Surface(modifier = Modifier.fillMaxSize(), color = DcColors.Surface) {
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
