package net.maz.llamachat.ui.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.maz.llamachat.ui.components.DcAppBar
import net.maz.llamachat.ui.components.DcTextField
import net.maz.llamachat.ui.theme.DcColors
import net.maz.llamachat.vm.GenPhase
import net.maz.llamachat.vm.GeneratorViewModel

/**
 * "Generate a character" flow. Phase [GenPhase.INPUT] collects optional seeds (or
 * a single Surprise me), [GenPhase.GENERATING] shows progress while the model
 * fills in the sheet, and [GenPhase.REVIEW] lets the user tweak the result —
 * editing in place, Regenerating, or Saving. Nothing is written until Save.
 */
@Composable
fun GeneratorScreen(
    vm: GeneratorViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedName) {
        state.savedName?.let(onSaved)
    }

    // Back from the review returns to the seeds; back from the seeds leaves.
    val handleBack = { if (state.phase == GenPhase.REVIEW) vm.editSeeds() else onBack() }

    Column(Modifier.fillMaxSize().background(DcColors.Surface)) {
        DcAppBar(
            title = if (state.phase == GenPhase.REVIEW) "Review character" else "Generate a character",
            onBack = handleBack,
            onOpenSettings = onOpenSettings,
        )

        when (state.phase) {
            GenPhase.GENERATING -> GeneratingView()
            GenPhase.REVIEW -> ReviewView(vm, state)
            GenPhase.INPUT -> InputView(vm, state)
        }
    }
}

@Composable
private fun InputView(vm: GeneratorViewModel, state: net.maz.llamachat.vm.GeneratorUiState) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        ) {
            Text(
                "Fill in any details you like and let the model invent the rest, or hit Surprise me.",
                fontSize = 13.sp,
                color = DcColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            OutlinedButton(
                onClick = vm::surpriseMe,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Filled.Casino, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Surprise me", color = DcColors.Primary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Spacer(Modifier.height(18.dp))

            val seed = state.seed
            DcTextField(
                label = "GENDER (OPTIONAL)",
                value = seed.gender,
                onValueChange = vm::setGender,
                placeholder = "e.g. female, non-binary, any",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "AGE (OPTIONAL)",
                value = seed.age,
                onValueChange = vm::setAge,
                placeholder = "e.g. late 20s, elderly, ageless",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "PROFESSION OR HOBBY (OPTIONAL)",
                value = seed.profession,
                onValueChange = vm::setProfession,
                placeholder = "e.g. marine biologist, street magician",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "VIBE / NOTES (OPTIONAL)",
                value = seed.vibe,
                onValueChange = vm::setVibe,
                placeholder = "Any tone or detail, e.g. dryly funny, hiding a secret",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )

            state.error?.let {
                Text(it, fontSize = 13.sp, color = DcColors.Error, modifier = Modifier.padding(top = 14.dp))
            }
        }

        Box(Modifier.fillMaxWidth().background(DcColors.Surface).padding(16.dp)) {
            Button(
                onClick = vm::generate,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DcColors.Primary, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate", fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.9.sp)
            }
        }
    }
}

@Composable
private fun GeneratingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DcColors.Primary)
            Spacer(Modifier.height(18.dp))
            Text("Inventing a character…", fontSize = 15.sp, color = DcColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun ReviewView(vm: GeneratorViewModel, state: net.maz.llamachat.vm.GeneratorUiState) {
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        ) {
            DcTextField(
                label = "NAME",
                value = state.name,
                onValueChange = vm::setName,
                placeholder = "Character name",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "DESCRIPTION (OPTIONAL)",
                value = state.description,
                onValueChange = vm::setDescription,
                placeholder = "Short subtitle shown in the picker",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
            )
            DcTextField(
                label = "GREETING (OPTIONAL)",
                value = state.greeting,
                onValueChange = vm::setGreeting,
                placeholder = "First message from the character",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 18.dp),
            )
            DcTextField(
                label = "CONTEXT / SYSTEM PROMPT",
                value = state.context,
                onValueChange = vm::setContext,
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

        Row(
            modifier = Modifier.fillMaxWidth().background(DcColors.Surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = vm::generate,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = DcColors.Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Regenerate", color = DcColors.Primary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
            Button(
                onClick = vm::save,
                enabled = state.name.isNotBlank(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DcColors.Primary, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Text("Save", fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.9.sp)
            }
        }
    }
}
