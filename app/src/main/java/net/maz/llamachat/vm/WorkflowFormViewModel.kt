package net.maz.llamachat.vm

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.random.Random
import net.maz.llamachat.LlamaChatApp
import net.maz.llamachat.data.IdGen
import net.maz.llamachat.data.comfy.ComfyJob
import net.maz.llamachat.data.comfy.FieldType
import net.maz.llamachat.data.comfy.FlowType
import net.maz.llamachat.data.comfy.JobInput
import net.maz.llamachat.data.comfy.PatchOp
import net.maz.llamachat.data.comfy.PendingFileInput
import net.maz.llamachat.data.comfy.PendingSubmission
import net.maz.llamachat.data.comfy.WorkflowField
import net.maz.llamachat.data.comfy.WorkflowPatcher

/** One rendered control: the config spec plus its live value/validation. */
data class FormField(
    val spec: WorkflowField,
    val type: FieldType,
    /** Resolved enum options (inline > base list); empty for other types. */
    val options: List<String> = emptyList(),
    val value: String = "",
    /** For [FieldType.FILE]: the picked source. */
    val fileUri: Uri? = null,
    /** Blocking problem with the config itself (e.g. missing base enum list). */
    val configError: String? = null,
    /** Validation error from the last Generate attempt; cleared on edit. */
    val error: String? = null,
) {
    val label: String get() = spec.displayLabel
}

data class WorkflowFormUiState(
    val workflowName: String = "",
    val flowType: FlowType = FlowType.TEXT_TO_IMAGE,
    val description: String? = null,
    val fields: List<FormField> = emptyList(),
    /** The workflow couldn't be loaded at all. */
    val loadError: String? = null,
    val submitting: Boolean = false,
    /** Set once the job is handed to the service; the screen navigates away. */
    val submitted: Boolean = false,
)

/**
 * Builds the native form for one installed workflow: fields from
 * workflow_config.json, defaults from the graph's current input values, enum
 * options from the base enums file, and Generate = patch + hand off to the
 * job controller/service.
 */
class WorkflowFormViewModel(
    private val app: LlamaChatApp,
    private val workflowId: Long,
    /** When regenerating from the queue: the live job whose values seed the form. */
    private val fromJobId: Long = -1L,
    /** When regenerating from the gallery: the persisted item whose stored request
     *  seeds the form. Survives restarts; takes precedence over [fromJobId]. */
    private val fromItemId: Long = -1L,
) : ViewModel() {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(WorkflowFormUiState())
    val state = _state.asStateFlow()

    private var graph: JsonObject? = null
    private var outputNodeTitle = ""
    private var outputField = ""

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val store = app.workflowStore
        val installed = store.workflows.value.firstOrNull { it.id == workflowId }
        if (installed == null) {
            _state.update { it.copy(loadError = "Workflow not found") }
            return
        }
        val config = store.loadConfig(workflowId).getOrElse {
            _state.update { s -> s.copy(loadError = "Could not read the workflow config: ${it.message}") }
            return
        }
        val g = store.loadGraph(workflowId).getOrElse {
            _state.update { s -> s.copy(loadError = "Could not read the workflow graph: ${it.message}") }
            return
        }
        graph = g
        outputNodeTitle = config.output.nodeTitle
        outputField = config.output.field
        val base = store.baseEnums()

        // When regenerating, seed each field from the source request's values (except
        // seeds, which reroll) instead of the graph's baked-in defaults. A gallery
        // item's persisted request wins over a live job, so old items still prefill.
        val sourceInputs: List<JobInput>? = when {
            fromItemId >= 0 -> app.galleryRepository.getById(fromItemId)?.let { item ->
                runCatching { json.decodeFromString<List<JobInput>>(item.inputsJson) }.getOrNull()
            }
            fromJobId >= 0 -> app.comfyJobs.jobs.value.firstOrNull { it.id == fromJobId }?.inputs
            else -> null
        }
        val prefill: Map<Pair<String, String>, String> =
            sourceInputs?.associate { (it.nodeTitle to it.input) to it.value } ?: emptyMap()

        val fields = config.fields.map { spec ->
            val type = FieldType.fromWire(spec.type)
            if (type == null) {
                return@map FormField(spec, FieldType.STRING, configError = "Unknown type '${spec.type}'")
            }
            val options = if (type == FieldType.ENUM) spec.resolveOptions(base) else emptyList()
            val configError = when {
                type == FieldType.ENUM && options.isEmpty() ->
                    "Enum list '${spec.optionsRef}' is not installed"
                else -> null
            }
            val prefilled = prefill[spec.nodeTitle to spec.input]
            val graphDefault = if (type == FieldType.FILE) null
            else WorkflowPatcher.defaultValue(g, spec.nodeTitle, spec.input)?.contentOrNull
            val value = when (type) {
                // Seeds always start fresh so each new form gives a different result.
                FieldType.SEED -> randomSeed()
                // Never blank: fall back to unchecked when the graph has no value.
                FieldType.BOOL -> (prefilled ?: graphDefault?.toBoolean()?.toString() ?: "false")
                else -> prefilled ?: graphDefault.orEmpty()
            }
            FormField(spec, type, options, value = value, configError = configError)
        }
        _state.update {
            it.copy(
                workflowName = installed.name,
                flowType = FlowType.fromKey(installed.flowType) ?: FlowType.TEXT_TO_IMAGE,
                description = config.description,
                fields = fields,
            )
        }
    }

    fun setValue(index: Int, value: String) = _state.update { s ->
        s.copy(fields = s.fields.mapIndexed { i, f ->
            if (i == index) f.copy(value = value, error = null) else f
        })
    }

    /** Roll a new value into a [FieldType.SEED] field. */
    fun randomizeSeed(index: Int) = setValue(index, randomSeed())

    fun setFile(index: Int, uri: Uri?) = _state.update { s ->
        s.copy(fields = s.fields.mapIndexed { i, f ->
            if (i == index) f.copy(fileUri = uri, error = null) else f
        })
    }

    /** Validate, patch the graph, cache file inputs, and enqueue the job. */
    fun generate() {
        val g = graph ?: return
        val validated = validate(_state.value.fields)
        if (validated.any { it.error != null }) {
            _state.update { it.copy(fields = validated) }
            return
        }
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true) }

        viewModelScope.launch {
            val st = _state.value
            val jobId = IdGen.next()
            val result = withContext(Dispatchers.IO) {
                runCatching { buildSubmission(g, st, jobId) }
            }
            result
                .onSuccess { (job, submission) ->
                    app.comfyJobs.submit(job, submission)
                    _state.update { it.copy(submitting = false, submitted = true) }
                }
                .onFailure { e ->
                    app.comfyJobs.deletePendingFiles(jobId) // drop any half-copied inputs
                    _state.update { it.copy(submitting = false) }
                    _submitError.value = e.message ?: "Could not start the generation"
                }
        }
    }

    /** Surfaced under the Generate button. */
    private val _submitError = MutableStateFlow<String?>(null)
    val submitError = _submitError.asStateFlow()

    private fun validate(fields: List<FormField>): List<FormField> = fields.map { f ->
        val mandatory = f.spec.mandatory
        val error = when {
            f.configError != null && mandatory -> f.configError
            f.type == FieldType.FILE -> if (mandatory && f.fileUri == null) "Required" else null
            f.value.isBlank() -> if (mandatory) "Required" else null
            (f.type == FieldType.INT || f.type == FieldType.SEED) &&
                f.value.trim().toLongOrNull() == null -> "Whole number required"
            f.type == FieldType.FLOAT && f.value.trim().toDoubleOrNull() == null -> "Number required"
            else -> null
        }
        f.copy(error = error)
    }

    /** IO: copy picked files into the pending dir and patch typed values in. */
    private fun buildSubmission(
        g: JsonObject,
        st: WorkflowFormUiState,
        jobId: Long,
    ): Pair<ComfyJob, PendingSubmission> {
        val ops = mutableListOf<PatchOp>()
        val fileInputs = mutableListOf<PendingFileInput>()
        val inputs = mutableListOf<JobInput>()
        st.fields.forEachIndexed { index, f ->
            when (f.type) {
                FieldType.FILE -> {
                    val uri = f.fileUri ?: return@forEachIndexed
                    val resolver = app.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
                    val dest = app.comfyJobs.newPendingInputFile(jobId, index, ext)
                    resolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not read the selected file for '${f.label}'")
                    fileInputs += PendingFileInput(f.spec.nodeTitle, f.spec.input, dest.name, mime)
                }
                else -> {
                    val v = f.value.trim()
                    if (v.isEmpty()) return@forEachIndexed // optional, untouched
                    inputs += JobInput(f.spec.nodeTitle, f.spec.input, f.value)
                    ops += PatchOp(
                        f.spec.nodeTitle,
                        f.spec.input,
                        when (f.type) {
                            FieldType.INT, FieldType.SEED -> JsonPrimitive(v.toLong())
                            FieldType.FLOAT -> JsonPrimitive(v.toDouble())
                            FieldType.BOOL -> JsonPrimitive(v.toBoolean())
                            else -> JsonPrimitive(f.value)
                        },
                    )
                }
            }
        }
        val patched = WorkflowPatcher.patch(g, ops).getOrThrow()
        val outputNodeId = WorkflowPatcher.nodeIdByTitle(patched, outputNodeTitle).getOrThrow()
        val job = ComfyJob(
            id = jobId,
            flowType = st.flowType.key,
            workflowName = st.workflowName,
            createdAt = System.currentTimeMillis(),
            outputNodeId = outputNodeId,
            outputField = outputField,
            workflowId = workflowId,
            inputs = inputs,
        )
        return job to PendingSubmission(jobId, patched, fileInputs)
    }

    companion object {
        /** Kept within JSON's safe-integer range (2^53-1) so any tool can read it back. */
        private const val MAX_SEED = 0x1F_FFFF_FFFF_FFFFL

        fun factory(app: LlamaChatApp, workflowId: Long, fromJobId: Long = -1L, fromItemId: Long = -1L) =
            viewModelFactory {
                initializer { WorkflowFormViewModel(app, workflowId, fromJobId, fromItemId) }
            }

        private fun randomSeed(): String = Random.nextLong(0, MAX_SEED).toString()
    }
}
