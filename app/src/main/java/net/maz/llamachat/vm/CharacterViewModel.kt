package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.CharacterRepository
import net.maz.llamachat.data.model.Character

/** Backs the character library screens: list, create/edit, delete, import/export. */
class CharacterViewModel(
    private val repo: CharacterRepository,
) : ViewModel() {

    val characters = repo.characters

    fun get(name: String): Character? = repo.get(name)

    /** Create or update a character. [originalName] is null when creating. */
    fun save(
        originalName: String?,
        name: String,
        context: String,
        greeting: String?,
        description: String,
    ) {
        viewModelScope.launch {
            repo.upsert(originalName, name.trim(), context, greeting, description.trim())
        }
    }

    fun delete(name: String) {
        viewModelScope.launch { repo.delete(name) }
    }

    /** YAML for one character, for sharing/exporting to a file. */
    fun exportYaml(name: String): String? = repo.exportYaml(name)

    /** Import one or more YAML documents; reports how many were added. */
    fun import(yamlDocs: List<String>, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.import(yamlDocs)) }
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer { CharacterViewModel(app.characterRepository) }
        }
    }
}
