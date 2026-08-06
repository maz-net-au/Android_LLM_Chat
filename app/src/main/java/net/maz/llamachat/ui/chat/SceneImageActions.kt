package net.maz.llamachat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.theme.DcColors

/**
 * The scene-image action menu, shared by the in-chat long-press and the full-screen
 * viewer. The three regenerate options each append a NEW image to the chat (the
 * original is kept); "Edit description" is expected to open [SceneEditPromptDialog].
 *
 * @param hasPrompt false when no description was saved — the options that need one
 *   (same prompt / edit) are disabled.
 * @param hasImage false when the image's bytes aren't on disk — nothing to save.
 */
@Composable
fun SceneImageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasPrompt: Boolean,
    hasImage: Boolean,
    onSamePrompt: () -> Unit,
    onNewDescription: () -> Unit,
    onEditDescription: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SceneMenuItem("Same prompt", Icons.Filled.Refresh, enabled = hasPrompt) {
            onDismiss(); onSamePrompt()
        }
        SceneMenuItem("New description", Icons.Filled.AutoAwesome) {
            onDismiss(); onNewDescription()
        }
        SceneMenuItem("Edit description", Icons.Filled.Edit, enabled = hasPrompt) {
            onDismiss(); onEditDescription()
        }
        SceneMenuItem("Save image", Icons.Filled.SaveAlt, enabled = hasImage) {
            onDismiss(); onSave()
        }
        SceneMenuItem("Delete", Icons.Filled.Delete, tint = DcColors.Error) {
            onDismiss(); onDelete()
        }
    }
}

/** One row of [SceneImageMenu]: icon + label, greyed out together when disabled. */
@Composable
private fun SceneMenuItem(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                fontSize = 14.sp,
                color = if (enabled) (tint ?: DcColors.OnSurface) else DcColors.OnSurfaceFaint,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) (tint ?: DcColors.OnSurfaceVariant) else DcColors.OnSurfaceFaint,
                modifier = Modifier.size(19.dp),
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * "Edit description": the saved prompt in an editable box, sent verbatim to generate
 * another image. [onGenerate] receives the trimmed text.
 */
@Composable
fun SceneEditPromptDialog(
    prompt: String,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
) {
    var text by rememberSaveable(prompt) { mutableStateOf(prompt) }
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
                        value = text,
                        onValueChange = { text = it },
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
                onClick = { onGenerate(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Generate", color = DcColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DcColors.OnSurfaceVariant) }
        },
    )
}
