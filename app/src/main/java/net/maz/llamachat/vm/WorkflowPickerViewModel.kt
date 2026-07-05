package net.maz.llamachat.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.InstalledWorkflow

/** Backs the per-flow workflow list a launcher tile opens. */
class WorkflowPickerViewModel(
    app: LlamaChatApp,
    flowType: FlowType,
) : ViewModel() {

    val workflows: StateFlow<List<InstalledWorkflow>> = app.workflowStore.workflows
        .map { list -> list.filter { it.flowType == flowType.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Jobs still in flight (any flow type) — drives the "in progress" link row. */
    val activeJobCount: StateFlow<Int> = app.comfyJobs.jobs
        .map { list -> list.count { !it.status.isTerminal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    companion object {
        fun factory(app: LlamaChatApp, flowType: FlowType) = viewModelFactory {
            initializer { WorkflowPickerViewModel(app, flowType) }
        }
    }
}
