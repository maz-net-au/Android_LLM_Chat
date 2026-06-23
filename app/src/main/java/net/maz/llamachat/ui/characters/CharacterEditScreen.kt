package net.maz.llamachat.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.CharacterViewModel

/**
 * Create ([editName] null) or edit a character. The `{{char}}` / `{{user}}`
 * placeholders are kept verbatim and substituted at send time.
 */
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

    val canSave = name.isNotBlank()

    Column(Modifier.fillMaxSize().background(Color.White)) {
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
        }

        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Button(
                onClick = {
                    vm.save(
                        originalName = editName,
                        name = name,
                        context = context,
                        greeting = greeting.ifBlank { null },
                        description = description,
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
