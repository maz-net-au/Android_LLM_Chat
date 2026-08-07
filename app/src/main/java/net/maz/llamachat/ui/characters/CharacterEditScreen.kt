package net.maz.llamachat.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.vm.CharacterViewModel

/**
 * Create ([editName] null) or edit a character. The `{{char}}` / `{{user}}`
 * placeholders are kept verbatim and substituted at send time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterEditScreen(
    vm: CharacterViewModel,
    editName: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val existing = remember(editName) { editName?.let { vm.get(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var greeting by remember { mutableStateOf(existing?.greeting ?: "") }
    var context by remember { mutableStateOf(existing?.context ?: "") }
    var usesNamePrefixes by remember { mutableStateOf(existing?.usesNamePrefixes ?: true) }
    var color by remember { mutableStateOf(existing?.color ?: Catalog.palette.first()) }
    var showAdjust by remember { mutableStateOf(false) }

    val revise by vm.revise.collectAsState()
    val canSave = name.isNotBlank()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(
            title = if (editName == null) "New character" else "Edit character",
            onBack = onBack,
            onOpenSettings = onOpenSettings,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        ) {
            DcTextField(
                label = "NAME",
                value = name,
                onValueChange = { name = it },
                placeholder = "Character name",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "DESCRIPTION (OPTIONAL)",
                value = description,
                onValueChange = { description = it },
                placeholder = "Short subtitle shown in the picker",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "GREETING (OPTIONAL)",
                value = greeting,
                onValueChange = { greeting = it },
                placeholder = "First message from the assistant",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 18.dp),
            )
            DcTextField(
                label = "CONTEXT / SYSTEM PROMPT",
                value = context,
                onValueChange = { context = it },
                placeholder = "Describe the character. {{char}} and {{user}} are substituted.",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    "{{char}} → this character's name · {{user}} → your name",
                    fontSize = 12.sp,
                    color = DcColors.OnSurfaceFaint,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                TextButton(
                    onClick = { showAdjust = true },
                    enabled = !revise.running,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (revise.running) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = DcColors.Primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = DcColors.Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (revise.running) "Adjusting…" else "Adjust with AI",
                        color = DcColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            revise.error?.let { message ->
                Text(
                    message,
                    fontSize = 12.sp,
                    color = DcColors.Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                "COLOUR",
                color = DcColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Catalog.palette.forEach { swatch ->
                    val selected = swatch.toArgb() == color.toArgb()
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) DcColors.OnSurface else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { color = swatch },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Name prefixes", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
                    Text(
                        "Format turns as a \"Name:\" transcript and force replies to stay in character. Turn off for plain assistant-style chats.",
                        fontSize = 12.sp,
                        color = DcColors.OnSurfaceFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = usesNamePrefixes,
                    onCheckedChange = { usesNamePrefixes = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DcColors.Primary,
                    ),
                )
            }
        }

        Box(Modifier.fillMaxWidth().background(DcColors.Surface).padding(16.dp)) {
            Button(
                onClick = {
                    vm.save(
                        originalName = editName,
                        name = name,
                        context = context,
                        greeting = greeting.ifBlank { null },
                        description = description,
                        usesNamePrefixes = usesNamePrefixes,
                        color = color,
                    )
                    onSaved()
                },
                enabled = canSave,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DcColors.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text("Save", fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.9.sp)
            }
        }
    }

    if (showAdjust) {
        AdjustContextDialog(
            onDismiss = { showAdjust = false },
            onAdjust = { instruction ->
                showAdjust = false
                vm.reviseContext(name, context, instruction) { context = it }
            },
        )
    }
}

/**
 * Collects a free-text instruction ("make it longer", "add a hobby of horse
 * riding") for the LLM rewrite of the context block. The request itself runs in
 * the ViewModel, so the dialog closes as soon as it is sent and the field is
 * replaced in place when the reply lands.
 */
@Composable
private fun AdjustContextDialog(
    onDismiss: () -> Unit,
    onAdjust: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DcColors.Surface,
        title = { Text("Adjust context", color = DcColors.OnSurface) },
        text = {
            Column {
                Text(
                    "Describe the change you want. The character-generation model " +
                        "rewrites the context block and replaces it here.",
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
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Text(
                                    "e.g. make it longer, or give her a hobby of horse riding",
                                    color = DcColors.OnSurfaceFaint,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdjust(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Adjust", color = DcColors.Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DcColors.OnSurfaceVariant) }
        },
    )
}
