package net.maz.llamachat.data.comfy

/**
 * The five generation flows a workflow package can belong to. [key] is the value
 * persisted (workflow index, gallery rows, jobs) and used in nav routes — never
 * store the enum name or ordinal, so entries can be renamed/reordered safely.
 */
enum class FlowType(val key: String, val label: String) {
    TEXT_TO_IMAGE("t2i", "Text to Image"),
    TEXT_TO_AUDIO("t2a", "Text to Audio"),
    TEXT_TO_VIDEO("t2v", "Text to Video"),
    IMAGE_TO_IMAGE("i2i", "Image to Image"),
    IMAGE_TO_VIDEO("i2v", "Image to Video");

    companion object {
        fun fromKey(key: String): FlowType? = entries.firstOrNull { it.key == key }
    }
}
