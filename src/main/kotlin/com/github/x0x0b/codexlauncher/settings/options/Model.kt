package com.github.x0x0b.codexlauncher.settings.options

import com.intellij.util.xmlb.Converter

/**
 * Model selection for the `--model` argument.
 *
 * `CUSTOM` is a display/persistence marker only. It is not a direct CLI token:
 * - `cliName()` returns an empty string for `CUSTOM`
 * - callers must resolve and validate a separate persisted custom model id before CLI use
 */
enum class Model(
    private val cliName: String,
    private val displayName: String = cliName,
) {
    /** Do not pass --model. */
    DEFAULT("", "Default"),

    GPT_5_6_SOL("gpt-5.6-sol"),
    GPT_5_6_TERRA("gpt-5.6-terra"),
    GPT_5_6_LUNA("gpt-5.6-luna"),

    /** Use customModel from settings. */
    CUSTOM("", "Custom...");

    fun cliName(): String = cliName

    fun toDisplayName(): String = displayName

    override fun toString(): String = toDisplayName()
}

/**
 * Falls back to the Codex CLI default when a workspace contains a model removed
 * from the current picker.
 */
class ModelConverter : Converter<Model>() {
    override fun fromString(value: String): Model =
        runCatching { Model.valueOf(value) }.getOrDefault(Model.DEFAULT)

    override fun toString(value: Model): String = value.name
}
