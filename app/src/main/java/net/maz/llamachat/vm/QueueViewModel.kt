package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.ComfyGenerationService
import net.maz.llamachat.data.comfy.ComfyJob

/** Backs the Queue & History screen: the full job ledger plus its row actions. */
class QueueViewModel(private val app: LlamaChatApp) : ViewModel() {

    /** Every tracked job, newest first. */
    val jobs: StateFlow<List<ComfyJob>> = app.comfyJobs.jobs
        .map { list -> list.sortedByDescending { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** First gallery item this job produced this session, or null. */
    fun firstOutput(jobId: Long): Long? = app.comfyJobs.outputsForJob(jobId).firstOrNull()

    /** Stop an in-flight job; it stays in the list as a cancelled row. */
    fun cancel(jobId: Long) = ComfyGenerationService.cancelJob(app, jobId)

    /** Drop a row from the list; cancels first if it is still active. */
    fun remove(job: ComfyJob) {
        if (job.status.isTerminal) app.comfyJobs.remove(job.id)
        else ComfyGenerationService.removeJob(app, job.id)
    }

    companion object {
        fun factory(app: LlamaChatApp) = viewModelFactory {
            initializer { QueueViewModel(app) }
        }
    }
}
