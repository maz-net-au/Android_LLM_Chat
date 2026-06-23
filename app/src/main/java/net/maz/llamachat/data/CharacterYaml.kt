package net.maz.llamachat.data

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer

/**
 * Reads and writes the text-generation-webui character YAML format:
 *
 * ```yaml
 * name: Lydia
 * greeting: "..."        # optional
 * context: |             # the system prompt
 *   ...
 * ```
 *
 * Only these three fields are used; other keys (TGW has a few historical ones)
 * are ignored on import. `{{char}}` / `{{user}}` placeholders are preserved
 * verbatim and substituted later at send time.
 */
object CharacterYaml {

    data class Parsed(val name: String, val context: String, val greeting: String?)

    /** Parse one character document. Returns null if it has no usable name. */
    fun parse(text: String): Parsed? {
        val map = runCatching { Yaml().load<Any?>(text) as? Map<*, *> }.getOrNull() ?: return null
        val name = (map["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val context = (map["context"] as? String)?.trim().orEmpty()
        val greeting = (map["greeting"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return Parsed(name, context, greeting)
    }

    /** Serialize a character to TGW-compatible YAML. */
    fun dump(name: String, context: String, greeting: String?): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            splitLines = false
        }
        // Emit multi-line scalars as literal blocks (readable, like TGW exports)
        // while leaving keys and short single-line values plain.
        val representer = object : Representer(options) {
            override fun representScalar(tag: Tag, value: String, style: DumperOptions.ScalarStyle?): Node {
                val chosen = if (value.contains('\n')) DumperOptions.ScalarStyle.LITERAL else style
                return super.representScalar(tag, value, chosen)
            }
        }
        // LinkedHashMap preserves a stable, human-friendly field order.
        val doc = LinkedHashMap<String, Any>()
        doc["name"] = name
        if (!greeting.isNullOrBlank()) doc["greeting"] = greeting
        doc["context"] = context
        return Yaml(representer, options).dump(doc)
    }
}
