package net.maz.llamachat.data.comfy

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A single value write: set `graph[node(title)].inputs[input] = value`. */
data class PatchOp(val nodeTitle: String, val input: String, val value: JsonPrimitive)

class PatchException(message: String) : Exception(message)

/**
 * Pure helpers over a ComfyUI API-format graph (JsonObject keyed by node id;
 * each node has `class_type`, `inputs`, and `_meta.title`). Node addressing is
 * by `_meta.title`, which therefore must be unique among referenced nodes —
 * enforced here and at workflow install time.
 */
object WorkflowPatcher {

    /** Id of the node whose `_meta.title` equals [title]; fails on 0 or >1 matches. */
    fun nodeIdByTitle(graph: JsonObject, title: String): Result<String> {
        val matches = graph.entries.filter { (_, node) ->
            (node as? JsonObject)
                ?.get("_meta")?.let { it as? JsonObject }
                ?.get("title")?.jsonPrimitive?.contentOrNull == title
        }
        return when (matches.size) {
            0 -> Result.failure(PatchException("No node titled '$title'"))
            1 -> Result.success(matches.first().key)
            else -> Result.failure(
                PatchException("Multiple nodes titled '$title' — titles referenced by the config must be unique")
            )
        }
    }

    /**
     * Current primitive value of the node's `inputs[input]`, used to seed form
     * defaults. Null when the node/input is missing or the input is a link
     * (a `[nodeId, slot]` array), which has no editable value.
     */
    fun defaultValue(graph: JsonObject, title: String, input: String): JsonPrimitive? {
        val nodeId = nodeIdByTitle(graph, title).getOrNull() ?: return null
        val inputs = graph[nodeId]?.jsonObject?.get("inputs") as? JsonObject ?: return null
        return inputs[input] as? JsonPrimitive
    }

    /** Copy of [graph] with every op applied, or the first addressing error. */
    fun patch(graph: JsonObject, ops: List<PatchOp>): Result<JsonObject> {
        // nodeId -> (input -> value); grouping lets each node be rebuilt once.
        val byNode = mutableMapOf<String, MutableMap<String, JsonPrimitive>>()
        for (op in ops) {
            val nodeId = nodeIdByTitle(graph, op.nodeTitle).getOrElse { return Result.failure(it) }
            val inputs = graph[nodeId]?.jsonObject?.get("inputs") as? JsonObject
                ?: return Result.failure(PatchException("Node '${op.nodeTitle}' has no inputs object"))
            when (inputs[op.input]) {
                null -> return Result.failure(
                    PatchException("Node '${op.nodeTitle}' has no input '${op.input}'")
                )
                !is JsonPrimitive -> return Result.failure(
                    PatchException("Input '${op.input}' of '${op.nodeTitle}' is a node link, not a value")
                )
                else -> byNode.getOrPut(nodeId) { mutableMapOf() }[op.input] = op.value
            }
        }
        val patched = buildJsonObject {
            for ((nodeId, node) in graph) {
                val writes = byNode[nodeId]
                if (writes == null) {
                    put(nodeId, node)
                    continue
                }
                val nodeObj = node.jsonObject
                put(nodeId, buildJsonObject {
                    for ((key, value) in nodeObj) {
                        if (key != "inputs") {
                            put(key, value)
                            continue
                        }
                        put("inputs", buildJsonObject {
                            for ((inKey, inValue) in value.jsonObject) put(inKey, writes[inKey] ?: inValue)
                        })
                    }
                })
            }
        }
        return Result.success(patched)
    }
}
