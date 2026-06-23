package net.maz.llamachat.ui.newconv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.data.model.Catalog
import net.maz.llamachat.ui.components.Avatar
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.NewConversationViewModel

@Composable
fun NewConversationScreen(
    vm: NewConversationViewModel,
    onBack: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(state.startedId) {
        state.startedId?.let(onStarted)
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        // App bar
        Row(
            modifier = Modifier.fillMaxWidth().background(DcColors.Primary).height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (state.editing) "Edit details" else "New conversation",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        ) {
            SectionLabel("TITLE")
            DcTextField(
                label = "",
                value = state.title,
                onValueChange = vm::setTitle,
                placeholder = "Untitled conversation",
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            )

            SectionLabel("YOUR NAME")
            DcTextField(
                label = "",
                value = state.userName,
                onValueChange = vm::setUserName,
                placeholder = "user",
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            )

            SectionLabel("CHARACTER")
            CharacterSelector(vm, selectedName = state.character)

            Spacer(Modifier.height(22.dp))
            SectionLabel("PARAMETER PRESET")
            PresetSelector(vm, selectedName = state.preset)

            Spacer(Modifier.height(22.dp))
            SectionLabel("MODEL")
            ModelSelector(vm, selectedModel = state.model)
        }

        // Bottom action bar
        Box(Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Button(
                onClick = vm::start,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DcColors.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Text(
                    if (state.editing) "Save changes" else "Start chat",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 0.9.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = DcColors.Primary,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun CharacterSelector(vm: NewConversationViewModel, selectedName: String) {
    var open by remember { mutableStateOf(false) }
    val selected = Catalog.character(selectedName)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DcColors.SurfaceTint, RoundedCornerShape(6.dp))
                .border(1.dp, DcColors.Outline, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(initial = selected.name, color = selected.color, size = 36.dp, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(selected.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface)
                Text(selected.subtitle(), fontSize = 12.sp, color = DcColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.characters.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(initial = c.name, color = c.color, size = 32.dp, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(c.subtitle(), fontSize = 12.sp, color = DcColors.OnSurfaceMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    },
                    onClick = { vm.selectCharacter(c.name); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetSelector(vm: NewConversationViewModel, selectedName: String) {
    var open by remember { mutableStateOf(false) }
    val selected = Catalog.preset(selectedName)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DcColors.SurfaceTint, RoundedCornerShape(6.dp))
                .border(1.dp, DcColors.Outline, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(selected.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DcColors.OnSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            vm.presets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name, fontSize = 14.sp) },
                    onClick = { vm.selectPreset(p.name); open = false },
                )
            }
        }
    }
    // Parameter chips
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        selected.chips().forEach { (k, v) ->
            Row(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .background(DcColors.PrimaryContainer, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(k, fontSize = 12.sp, color = DcColors.PrimaryDark.copy(alpha = 0.7f))
                Spacer(Modifier.width(4.dp))
                Text(v, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DcColors.PrimaryDark)
            }
        }
    }
}

@Composable
private fun ModelSelector(vm: NewConversationViewModel, selectedModel: String) {
    var open by remember { mutableStateOf(false) }
    val models = vm.models
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DcColors.SurfaceTint, RoundedCornerShape(6.dp))
                .border(1.dp, DcColors.Outline, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                Catalog.shortModel(selectedModel),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = DcColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = DcColors.OnSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(Catalog.shortModel(model), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { vm.selectModel(model); open = false },
                )
            }
        }
    }
}
