package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.db.GalleryItemEntity

/** Backs both the gallery grid and the full-screen viewer. */
class GalleryViewModel(
    private val app: LlamaChatApp,
    initialTab: FlowType?,
) : ViewModel() {

    /** Selected flow-type filter; null = All. */
    private val _tab = MutableStateFlow(initialTab)
    val tab = _tab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<GalleryItemEntity>> = _tab
        .flatMapLatest { app.galleryRepository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot outcome of the last export ("Saved to Pictures/PrivateAI" / error). */
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage = _exportMessage.asStateFlow()

    fun selectTab(flowType: FlowType?) {
        _tab.value = flowType
    }

    fun fileFor(item: GalleryItemEntity): File = app.galleryRepository.store.fileFor(item)

    suspend fun getItem(id: Long): GalleryItemEntity? = app.galleryRepository.getById(id)

    /** The job that produced [itemId], if it's still around and can be regenerated. */
    fun regenerableJob(itemId: Long): ComfyJob? =
        app.comfyJobs.jobForItem(itemId)?.takeIf { it.canRegenerate }

    fun delete(item: GalleryItemEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            app.galleryRepository.delete(item)
            onDeleted()
        }
    }

    fun export(item: GalleryItemEntity) {
        viewModelScope.launch {
            app.galleryRepository.store.exportToMediaStore(item)
                .onSuccess {
                    val dest = when {
                        item.mimeType.startsWith("image/") -> "Pictures"
                        item.mimeType.startsWith("video/") -> "Movies"
                        item.mimeType.startsWith("audio/") -> "Music"
                        else -> "Downloads"
                    }
                    _exportMessage.value = "Saved to $dest/PrivateAI"
                }
                .onFailure { _exportMessage.value = "Save failed: ${it.message}" }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    companion object {
        fun factory(app: LlamaChatApp, initialTab: FlowType? = null) = viewModelFactory {
            initializer { GalleryViewModel(app, initialTab) }
        }
    }
}
