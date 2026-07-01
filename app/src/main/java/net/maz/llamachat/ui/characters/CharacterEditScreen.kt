package net.maz.llamachat.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
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
) {
    val existing = remember(editName) { editName?.let { vm.get(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var greeting by remember { mutableStateOf(existing?.greeting ?: "") }
    var context by remember { mutableStateOf(existing?.context ?: "") }
    var usesNamePrefixes by remember { mutableStateOf(existing?.usesNamePrefixes ?: true) }
    var color by remember { mutableStateOf(existing?.color ?: Catalog.palette.first()) }

    val canSave = name.isNotBlank()

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DcColors.Primary).height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (editName == null) "New character" else "Edit character",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

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
            Text(
                "{{char}} → this character's name · {{user}} → your name",
                fontSize = 12.sp,
                color = DcColors.OnSurfaceFaint,
                modifier = Modifier.padding(top = 8.dp),
            )

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
}
