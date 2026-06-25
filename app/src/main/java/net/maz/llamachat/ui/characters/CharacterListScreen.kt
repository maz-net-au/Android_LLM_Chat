package net.maz.llamachat.ui.characters

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.CharacterViewModel

@Composable
fun CharacterListScreen(
    vm: CharacterViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCreate: () -> Unit,
    onGenerate: () -> Unit,
) {
    val characters by vm.characters.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Export: remember which character is pending while the file picker is open.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val docs = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        vm.import(docs) { count ->
            Toast.makeText(
                context,
                if (count > 0) "Imported $count character${if (count == 1) "" else "s"}"
                else "No characters imported",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-yaml"),
    ) { uri: Uri? ->
        val name = pendingExport
        pendingExport = null
        if (uri == null || name == null) return@rememberLauncherForActivityResult
        val yaml = vm.exportYaml(name)
        if (yaml != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(yaml.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "Exported $name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DcColors.Primary).height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("Characters", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onGenerate) {
                Icon(Icons.Filled.Casino, contentDescription = "Generate a character", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.FileUpload, contentDescription = "Import", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(characters, key = { it.name }) { c ->
                    CharacterRow(
                        name = c.name,
                        subtitle = c.subtitle(),
                        color = c.color,
                        onClick = { onEdit(c.name) },
                        onExport = {
                            pendingExport = c.name
                            exportLauncher.launch("${c.name}.yaml")
                        },
                        onDelete = { confirmDelete = c.name },
                    )
                    HorizontalDivider(color = DcColors.Divider)
                }
            }
            FloatingActionButton(
                onClick = onCreate,
                containerColor = DcColors.Primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).size(56.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New character", modifier = Modifier.size(28.dp))
            }
        }
    }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete $name?") },
            text = { Text("This removes the character. Existing conversations keep their messages but lose this persona.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(name); confirmDelete = null }) {
                    Text("Delete", color = DcColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CharacterRow(
    name: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initial = name, color = color, size = 42.dp, fontSize = 17.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle.ifBlank { "No system prompt" },
                fontSize = 13.sp,
                color = DcColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Export", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(19.dp)) },
                    onClick = { menuOpen = false; onExport() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", fontSize = 14.sp, color = DcColors.Error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = DcColors.Error, modifier = Modifier.size(19.dp)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
