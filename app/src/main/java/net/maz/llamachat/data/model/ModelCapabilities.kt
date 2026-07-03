package net.maz.llamachat.data.model

/**
 * What a model can take as input, inferred from its name (llama-server doesn't
 * report modality via /v1/models). Naming convention on the server:
 * `-VL-` = vision, `-AL-` = audio, `-VAL-` = both. Note "-VAL-" does not contain
 * "-AL-" as a substring, so each check names it explicitly.
 */
object ModelCapabilities {
    fun supportsImages(model: String): Boolean =
        model.contains("-VL-", ignoreCase = true) || model.contains("-VAL-", ignoreCase = true)

    fun supportsAudio(model: String): Boolean =
        model.contains("-AL-", ignoreCase = true) || model.contains("-VAL-", ignoreCase = true)
}
