package net.maz.llamachat.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.data.model.CastMember
import net.maz.llamachat.data.model.ChatMessage
import net.maz.llamachat.data.model.SceneImageMeta
import net.maz.llamachat.ui.components.AppBarMenuItem
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.ChatViewModel
import java.io.File

/**
 * Per-conversation cast management: the primary character (from the shared catalog,
 * marked "(primary)") plus self-contained per-conversation members. Each member gets a
 * persona field, an editable LLM-maintained state, and a reference image used only when
 * generating that member's state update. Adding is either "New character" (name + persona)
 * or "From catalog…" (copies a library character in as a fresh per-conversation member).
 */
@Composable
fun CastScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val conv = state.conversation
    val context = LocalContext.current
    val app = context.applicationContext as LlamaChatApp

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // --- reference image pickers (per member) ---------------------------------
    var refTarget by remember { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) refTarget?.let { vm.setRefImageFromUri(it, uri) }
        refTarget = null
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // sweep older captures
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(context, "net.maz.llamachat.fileprovider", file)
        cameraLauncher.launch(cameraUri!!)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, "Camera access denied", Toast.LENGTH_SHORT).show()
    }
    fun takePhoto(name: String) {
        refTarget = name
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    var galleryTarget by remember { mutableStateOf<String?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let { galleryTarget?.let { n -> vm.setRefImageFromUri(n, it) } } }
    fun pickGallery(name: String) {
        galleryTarget = name
        galleryLauncher.launch("image/*")
    }

    // --- dialogs ---------------------------------------------------------------
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var stateTarget: CastMember? by remember { mutableStateOf(null) }
    var removeTarget: CastMember? by remember { mutableStateOf(null) }

    val character = conv?.character
    val primaryName = conv?.characterName.orEmpty()
    // The primary's per-conversation state lives on a cast member whose name matches the
    // catalog character; its persona is catalog-managed and not editable here.
    val primaryMember: CastMember? = conv?.cast?.firstOrNull { it.name == primaryName }
    val members = conv?.cast?.filterNot { it.name == primaryName } ?: emptyList()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(
            title = "Characters",
            onBack = onBack,
            menuItems = buildList {
                add(AppBarMenuItem(Icons.Filled.Add, "New character", DcColors.OnSurfaceVariant, DcColors.OnSurface) { adding = true })
                add(AppBarMenuItem(Icons.Filled.Person, "From catalog…", DcColors.OnSurfaceVariant, DcColors.OnSurface) { importing = true })
            },
        )

        if (state.updatingCastName != null) {
            val updatingName = state.updatingCastName!!
            CastUpdatingBanner(name = updatingName, pendingCount = state.castSummariesPending.size)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (primaryName.isNotBlank()) {
                item(key = "primary") {
                    CastMemberCard(
                        name = primaryName,
                        isPrimary = true,
                        member = primaryMember,
                        persona = character?.context.orEmpty(),
                        personaEditable = false,
                        messageColor = vm.speakerColor(primaryName, conv!!),
                        onMessageColorPick = { vm.setCastColor(primaryName, it) },
                        updating = state.updatingCastName == primaryName,
                        refFile = primaryMember?.refImage?.let { app.attachmentStore.fileFor(vm.convId, it) },
                        onPersonaChange = {},
                        onUpdateLlm = { vm.updateCastState(primaryName) },
                        onTakePhoto = { takePhoto(primaryName) },
                        onPickGallery = { pickGallery(primaryName) },
                        onFromScene = { messageId -> vm.setRefImageFromScene(primaryName, messageId) },
                        onClearRef = { vm.clearRefImage(primaryName) },
                        onEditState = { stateTarget = primaryMember ?: CastMember(primaryName) },
                        sceneMessages = conv?.messages.orEmpty(),
                        store = app.attachmentStore,
                        convId = vm.convId,
                    )
                }
            }
            items(members, key = { it.name }) { member ->
                val catalogChar = Catalog.characters.firstOrNull { it.name == member.name }
                CastMemberCard(
                    name = member.name,
                    isPrimary = false,
                    member = member,
                    persona = member.context,
                    personaEditable = true,
                    messageColor = vm.speakerColor(member.name, conv!!),
                    onMessageColorPick = { vm.setCastColor(member.name, it) },
                    avatarColor = catalogChar?.color ?: Catalog.defaultColor,
                    updating = state.updatingCastName == member.name,
                    refFile = member.refImage?.let { app.attachmentStore.fileFor(vm.convId, it) },
                    onPersonaChange = { vm.updateCastMember(member.name, member.name, it) },
                    onUpdateLlm = { vm.updateCastState(member.name) },
                    onTakePhoto = { takePhoto(member.name) },
                    onPickGallery = { pickGallery(member.name) },
                    onFromScene = { messageId -> vm.setRefImageFromScene(member.name, messageId) },
                    onClearRef = { vm.clearRefImage(member.name) },
                    onEditState = { stateTarget = member },
                    onExport = {
                        val n = vm.exportToCatalog(member.name)
                        toast(if (n != null) "Exported to catalog" else "Couldn't export")
                    },
                    onRemove = { removeTarget = member },
                    sceneMessages = conv?.messages.orEmpty(),
                    store = app.attachmentStore,
                    convId = vm.convId,
                )
            }
        }
    }

    // --- add: new character ----------------------------------------------------
    if (adding) {
        NewCharacterDialog(
            takenNames = setOf(primaryName) + members.map { it.name }.toSet(),
            onDismiss = { adding = false },
            onCreate = { name, persona ->
                adding = false
                toast(if (vm.addCastMember(name, persona)) "Added $name" else "That name is already in the cast")
            },
        )
    }

    // --- add: from catalog -----------------------------------------------------
    if (importing) {
        val available = Catalog.characters.filterNot { it.name == primaryName || members.any { m -> m.name == it.name } }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Import from catalog") },
            text = {
                if (available.isEmpty()) {
                    Text("Every catalog character is already in this cast.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        available.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        importing = false
                                        toast(if (vm.importFromCatalog(c.name)) "Added ${c.name}" else "Couldn't add ${c.name}")
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(initial = c.name, color = c.color, size = 28.dp, fontSize = 13.sp)
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
                                    Text(c.subtitle(), fontSize = 12.sp, color = DcColors.OnSurfaceMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }

    // --- edit state ------------------------------------------------------------
    stateTarget?.let { member ->
        StateEditDialog(
            name = member.name,
            initial = member.state,
            onDismiss = { stateTarget = null },
            onSave = { text ->
                vm.setCastState(member.name, text)
                stateTarget = null
            },
        )
    }

    // --- remove confirm --------------------------------------------------------
    removeTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${member.name}?") },
            text = { Text("Removes the character from this conversation only. The character library is untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeCastMember(member.name)
                    removeTarget = null
                }) { Text("Remove", color = DcColors.Error) }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------

/** One cast member row: avatar + name, persona, the state block with "Update with LLM",
 *  and the reference image controls (camera / gallery / scene images). */
@Composable
private fun CastMemberCard(
    name: String,
    isPrimary: Boolean,
    member: CastMember?,
    persona: String,
    personaEditable: Boolean,
    messageColor: Color,
    onMessageColorPick: (Color) -> Unit,
    avatarColor: Color = Catalog.defaultColor,
    updating: Boolean,
    refFile: File?,
    onPersonaChange: (String) -> Unit,
    onUpdateLlm: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onFromScene: (Long) -> Unit,
    onClearRef: () -> Unit,
    onEditState: () -> Unit,
    onExport: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    sceneMessages: List<ChatMessage>,
    store: net.maz.llamachat.data.attach.AttachmentStore,
    convId: Long,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sceneListOpen by remember { mutableStateOf(false) }
    // Finished scene images in this conversation, newest last (as stored).
    val scenes = sceneMessages.filter { it.isSceneImage && it.sceneImage?.status == SceneImageMeta.STATUS_DONE }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DcColors.SurfaceTint)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(initial = name, color = avatarColor, size = 38.dp, fontSize = 16.sp, bordered = true)
            Spacer(Modifier.size(10.dp))
            Text(
                if (isPrimary) "$name (primary)" else name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = DcColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            if (onRemove != null || onExport != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Actions", tint = DcColors.OnSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onExport != null) {
                            DropdownMenuItem(text = { Text("Export to catalog") }, onClick = { menuOpen = false; onExport() })
                        }
                        if (onRemove != null) {
                            DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
                        }
                    }
                }
            }
        }

        if (!personaEditable && persona.isNotBlank()) {
            Text(
                persona,
                fontSize = 12.sp,
                color = DcColors.OnSurfaceMedium,
                maxLines = 3,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (personaEditable) {
            DcTextField(
                label = "Persona",
                value = persona,
                onValueChange = onPersonaChange,
                placeholder = "Who is this character? (blank = none)",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }

        // --- message colour ----------------------------------------------------
        Text(
            "Message colour",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = DcColors.Primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        ColorSwatchRow(selected = messageColor, onPick = onMessageColorPick)

        // --- state -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("State", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DcColors.Primary)
            Spacer(Modifier.weight(1f))
            if (updating) {
                CircularProgressIndicator(color = DcColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text("Updating…", fontSize = 12.sp, color = DcColors.OnSurfaceMedium)
            } else {
                TextButton(onClick = onUpdateLlm) {
                    Text("Update with LLM", fontSize = 12.sp, color = DcColors.Primary)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DcColors.Surface)
                .clickable(onClick = onEditState)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                member?.state?.takeIf { it.isNotBlank() } ?: "No state yet — tap to edit or update with the LLM.",
                fontSize = 12.sp,
                color = if (member?.state.isNullOrBlank()) DcColors.OnSurfaceFaint else DcColors.OnSurfaceMedium,
                maxLines = 4,
            )
        }

        // --- reference image ---------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reference image", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DcColors.Primary)
            Spacer(Modifier.weight(1f))
            if (refFile != null && refFile.exists()) {
                Box {
                    AsyncImage(
                        model = refFile,
                        contentDescription = "Reference image for $name",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    IconButton(onClick = onClearRef, modifier = Modifier.align(Alignment.TopEnd).size(18.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            } else {
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = DcColors.Primary)
                        Spacer(Modifier.size(4.dp))
                        Text("Add", fontSize = 12.sp, color = DcColors.Primary)
                    }
                }
            }
        }

        // The "Add" button and the × both toggle [menuOpen]; the menu offers the three
        // sources (and Remove when an image is set).
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Take photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = { menuOpen = false; onTakePhoto() })
            DropdownMenuItem(text = { Text("Choose from gallery") }, onClick = { menuOpen = false; onPickGallery() })
            if (scenes.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("From scene images (${scenes.size})") },
                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                    onClick = { menuOpen = false; sceneListOpen = true },
                )
            }
        }

        if (sceneListOpen) {
            ScenePickDialog(
                scenes = scenes,
                files = scenes.map { it.id to (it.attachments.firstOrNull()?.let { a -> store.fileFor(convId, a) } ?: File("")) },
                onDismiss = { sceneListOpen = false },
                onPick = { messageId -> sceneListOpen = false; onFromScene(messageId) },
            )
        }
    }
}

// ---------------------------------------------------------------------------

/** One row of the message-colour swatches: tap to pick, check marks the current one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(selected: Color, onPick: (Color) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Catalog.palette.forEach { swatch ->
            val isSelected = swatch.toArgb() == selected.toArgb()
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (isSelected) 2.5.dp else 0.dp,
                        color = if (isSelected) DcColors.OnSurface else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(swatch) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Name + persona for a fresh per-conversation member. */
@Composable
private fun NewCharacterDialog(
    takenNames: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, persona: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    val clean = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New character") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DcTextField(label = "Name", value = name, onValueChange = { name = it }, placeholder = "e.g. Mara")
                DcTextField(
                    label = "Persona",
                    value = persona,
                    onValueChange = { persona = it },
                    placeholder = "Who are they? (optional)",
                    singleLine = false,
                )
                if (clean.isNotEmpty() && clean in takenNames) {
                    Text("That name is already in this cast.", fontSize = 12.sp, color = DcColors.Error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = clean.isNotEmpty() && clean !in takenNames,
                onClick = { onCreate(clean, persona) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Free-form editor for a member's state notes. */
@Composable
private fun StateEditDialog(
    name: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${name}'s state") },
        text = {
            DcTextField(
                label = "State",
                value = text,
                onValueChange = { text = it },
                placeholder = "Relationships, injuries, possessions… (blank = none)",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick a finished scene image in this conversation to use as the reference. */
@Composable
private fun ScenePickDialog(
    scenes: List<ChatMessage>,
    files: List<Pair<Long, File>>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use a scene image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scenes.forEachIndexed { i, msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(msg.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = files[i].second,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            msg.sceneImage?.focus?.ifBlank { "Scene" } ?: "Scene",
                            fontSize = 13.sp,
                            color = DcColors.OnSurface,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
