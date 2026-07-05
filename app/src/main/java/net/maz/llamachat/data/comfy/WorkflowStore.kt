package net.maz.llamachat.data.comfy

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.maz.llamachat.data.IdGen
import java.io.File
import java.util.zip.ZipInputStream

/** Index entry for one installed workflow package. */
@Serializable
data class InstalledWorkflow(
    val id: Long,
    val name: String,
    /** [FlowType.key] — which launcher tile lists this workflow. */
    val flowType: String,
    val installedAt: Long,
)

/** A validated zip, parsed but not yet installed (the install dialog sits between). */
data class ParsedWorkflowZip(
    val config: WorkflowConfig,
    internal val graphText: String,
    internal val configText: String,
)

/**
 * Owns uploaded ComfyUI workflow packages and the shared base enums file.
 * Metadata lives in a small JSON index (no queries needed, graphs are large),
 * bytes verbatim on disk:
 *
 * ```
 * filesDir/comfy/
 *   base_enums.json
 *   workflows/index.json
 *   workflows/<id>/api_workflow.json
 *   workflows/<id>/workflow_config.json
 * ```
 */
class WorkflowStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val root = File(context.filesDir, "comfy")
    private val workflowsDir = File(root, "workflows")
    private val indexFile = File(workflowsDir, "index.json")
    private val baseEnumsFile = File(root, "base_enums.json")

    private val _workflows = MutableStateFlow(loadIndex())
    val workflows = _workflows.asStateFlow()

    /** Number of named lists in the installed base enums file (settings caption). */
    private val _baseEnumCount = MutableStateFlow(loadBaseEnums().enums.size)
    val baseEnumCount = _baseEnumCount.asStateFlow()

    /**
     * Read and validate a workflow zip without installing it: both JSON entries
     * present (matched by base filename so zips made from a folder work), the
     * graph is a ComfyUI API-format object, and every config field addresses a
     * real, uniquely-titled node input. Failures carry a user-readable message.
     */
    suspend fun parse(uri: Uri): Result<ParsedWorkflowZip> = withContext(Dispatchers.IO) {
        runCatching {
            var graphText: String? = null
            var configText: String? = null
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name.substringAfterLast('/')) {
                            GRAPH_ENTRY -> graphText = zip.readEntry()
                            CONFIG_ENTRY -> configText = zip.readEntry()
                        }
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Could not open the selected file")
            val graphStr = graphText ?: error("Zip is missing $GRAPH_ENTRY")
            val configStr = configText ?: error("Zip is missing $CONFIG_ENTRY")

            val config = runCatching { json.decodeFromString<WorkflowConfig>(configStr) }
                .getOrElse { error("$CONFIG_ENTRY is not valid: ${it.message?.take(200)}") }
            val graph = runCatching { json.parseToJsonElement(graphStr).jsonObject }
                .getOrElse { error("$GRAPH_ENTRY is not a JSON object — export the workflow in API format") }
            validate(config, graph)
            ParsedWorkflowZip(config, graphStr, configStr)
        }
    }

    /** Install a parsed zip under a fresh id. Files land before the index entry,
     *  so a crash mid-install can leave an orphan dir but never a broken entry. */
    suspend fun install(
        parsed: ParsedWorkflowZip,
        flowType: FlowType,
        name: String,
    ): Result<InstalledWorkflow> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = InstalledWorkflow(
                id = IdGen.next(),
                name = name.ifBlank { parsed.config.name },
                flowType = flowType.key,
                installedAt = System.currentTimeMillis(),
            )
            val dir = File(workflowsDir, entry.id.toString()).apply { mkdirs() }
            File(dir, GRAPH_ENTRY).writeText(parsed.graphText)
            File(dir, CONFIG_ENTRY).writeText(parsed.configText)
            persistIndex(_workflows.value + entry)
            entry
        }
    }

    suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) {
        persistIndex(_workflows.value.filterNot { it.id == id })
        File(workflowsDir, id.toString()).deleteRecursively()
    }

    suspend fun loadGraph(id: Long): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            json.parseToJsonElement(File(File(workflowsDir, id.toString()), GRAPH_ENTRY).readText())
                .jsonObject
        }
    }

    suspend fun loadConfig(id: Long): Result<WorkflowConfig> = withContext(Dispatchers.IO) {
        runCatching {
            json.decodeFromString<WorkflowConfig>(
                File(File(workflowsDir, id.toString()), CONFIG_ENTRY).readText()
            )
        }
    }

    /** Replace the base enums file; returns the number of lists it defines. */
    suspend fun installBaseEnums(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Could not open the selected file")
            val parsed = runCatching { json.decodeFromString<BaseEnums>(text) }
                .getOrElse { error("Not a valid base enums file: ${it.message?.take(200)}") }
            root.mkdirs()
            baseEnumsFile.writeText(text)
            _baseEnumCount.value = parsed.enums.size
            parsed.enums.size
        }
    }

    /** The installed base enums, or an empty set when none was uploaded yet. */
    suspend fun baseEnums(): BaseEnums = withContext(Dispatchers.IO) { loadBaseEnums() }

    // ---- internals ----

    /** Install-time validation; throws with a user-readable message. */
    private fun validate(config: WorkflowConfig, graph: JsonObject) {
        graph.forEach { (nodeId, node) ->
            val obj = node as? JsonObject
            if (obj == null || "class_type" !in obj || "inputs" !in obj) {
                error("Node '$nodeId' has no class_type/inputs — export the workflow in API format")
            }
        }
        for (field in config.fields) {
            val type = FieldType.fromWire(field.type)
                ?: error("Field '${field.displayLabel}': unknown type '${field.type}'")
            val nodeId = WorkflowPatcher.nodeIdByTitle(graph, field.nodeTitle)
                .getOrElse { error("Field '${field.displayLabel}': ${it.message}") }
            val inputs = graph[nodeId]?.jsonObject?.get("inputs")?.jsonObject
            when (inputs?.get(field.input)) {
                null -> error("Field '${field.displayLabel}': node '${field.nodeTitle}' has no input '${field.input}'")
                !is JsonPrimitive -> error(
                    "Field '${field.displayLabel}': input '${field.input}' of '${field.nodeTitle}' is a node link, not a value"
                )
                else -> Unit
            }
            if (type == FieldType.ENUM && field.options == null && field.optionsRef == null) {
                error("Field '${field.displayLabel}': enum needs 'options' or 'optionsRef'")
            }
        }
        WorkflowPatcher.nodeIdByTitle(graph, config.output.nodeTitle)
            .getOrElse { error("Output: ${it.message}") }
    }

    private fun ZipInputStream.readEntry(): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) error("Zip entry too large (over ${MAX_ENTRY_BYTES / (1024 * 1024)} MB)")
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun loadIndex(): List<InstalledWorkflow> = runCatching {
        if (!indexFile.exists()) emptyList()
        else json.decodeFromString<List<InstalledWorkflow>>(indexFile.readText())
    }.getOrDefault(emptyList())

    private fun loadBaseEnums(): BaseEnums = runCatching {
        if (!baseEnumsFile.exists()) BaseEnums()
        else json.decodeFromString<BaseEnums>(baseEnumsFile.readText())
    }.getOrDefault(BaseEnums())

    private fun persistIndex(list: List<InstalledWorkflow>) {
        workflowsDir.mkdirs()
        indexFile.writeText(json.encodeToString(list))
        _workflows.value = list
    }

    private companion object {
        const val GRAPH_ENTRY = "api_workflow.json"
        const val CONFIG_ENTRY = "workflow_config.json"
        const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    }
}
