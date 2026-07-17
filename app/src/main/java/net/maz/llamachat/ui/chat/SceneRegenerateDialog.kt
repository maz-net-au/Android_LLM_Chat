package net.maz.llamachat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.theme.DcColors

/**
 * The scene-image "Regenerate" chooser: add another image to the chat from a fresh
 * description, the saved prompt with a new seed, or a hand-edited description. Shared
 * by the in-chat long-press menu and the full-screen viewer; the caller performs the
 * regenerate and any follow-up (e.g. popping the viewer) in [onRegenerate].
 *
 * @param prompt the saved description; when blank the reuse/edit options are disabled.
 * @param onRegenerate (reusePrompt, editedPrompt) — mirrors ChatViewModel.regenerateScene.
 */
@Composable
fun SceneRegenerateDialog(
    prompt: String,
    onDismiss: () -> Unit,
    onRegenerate: (reusePrompt: Boolean, editedPrompt: String?) -> Unit,
) {
    // null = show the choice dialog; non-null = editing the description, holding the text.
    var editing by remember { mutableStateOf<String?>(null) }

    val current = editing
    if (current == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DcColors.Surface,
            title = { Text("Regenerate scene image", color = DcColors.OnSurface) },
            text = {
                Column {
                    Text("Add another image to the chat.", color = DcColors.OnSurfaceMedium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    RegenOption(
                        "New description",
                        "The model writes a fresh description from the same focus.",
                    ) { onRegenerate(false, null) }
                    RegenOption(
                        "Same prompt, new seed",
                        "Re-run the saved description with a different seed.",
                        enabled = prompt.isNotBlank(),
                    ) { onRegenerate(true, null) }
                    RegenOption(
                        "Edit the description",
                        "Tweak the saved description yourself, then generate.",
                        enabled = prompt.isNotBlank(),
                    ) { editing = prompt }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = DcColors.OnSurfaceVariant) }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DcColors.Surface,
            title = { Text("Edit description", color = DcColors.OnSurface) },
            text = {
                Column {
                    Text(
                        "This exact text is sent to generate a new image.",
                        fontSize = 13.sp,
                        color = DcColors.OnSurfaceMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(DcColors.SurfaceTint, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = current,
                            onValueChange = { editing = it },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.sp,
                                color = DcColors.OnSurface,
                                lineHeight = 21.sp,
                            ),
                            cursorBrush = SolidColor(DcColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onRegenerate(false, current.trim()) },
                    enabled = current.isNotBlank(),
                ) { Text("Generate", color = DcColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = DcColors.OnSurfaceVariant) }
            },
        )
    }
}

/** One stacked choice in the regenerate dialog: a bold title over a muted one-line hint. */
@Composable
private fun RegenOption(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            title,
            color = if (enabled) DcColors.Primary else DcColors.OnSurfaceFaint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(subtitle, color = DcColors.OnSurfaceFaint, fontSize = 12.sp)
    }
}
