package com.github.x0x0b.codexlauncher.settings

import com.github.x0x0b.codexlauncher.cli.CodexArgsBuilder
import com.github.x0x0b.codexlauncher.settings.options.Model
import com.github.x0x0b.codexlauncher.settings.options.ModelConverter
import com.github.x0x0b.codexlauncher.settings.options.ModelReasoningEffort
import com.github.x0x0b.codexlauncher.settings.options.WinShell
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag

/**
 * Project-level settings service for Codex UI plugin.
 * 
 * This service manages the persistent configuration including:
 * - Model selection (default, GPT-5.6 family, custom)
 * - Custom model identifier for CUSTOM mode
 * - File opening behavior preferences
 * 
 * Settings are persisted per project (workspace file).
 * 
 * @since 1.0.0
 */

@Service(Service.Level.PROJECT)
@State(name = "CodexUISettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CodexLauncherSettings : PersistentStateComponent<CodexLauncherSettings.State> {
    /**
     * Data class representing the persistent state of the plugin settings.
     * 
     * @property model The selected model for codex
     * @property customModel Custom model identifier when model is set to CUSTOM
     * @property customModelReasoningEffort Custom model reasoning effort when modelReasoningEffort is set to CUSTOM
     * @property openFileOnChange Whether to automatically open files when they change
     * @property enableNotification Whether to enable notifications
     * @property enableFullAccess Whether to bypass approvals and sandboxing
     * @property useSelectedModuleDirectory Whether to pass the selected module directory via --cd
     * @property customArgs Additional CLI arguments appended as-is to the Codex command
     * @property isPowerShell73OrOver Whether using PowerShell 7.3 or later (legacy; use winShell instead)
     * @property winShell Preferred Windows shell selection (Windows only)
     */
    data class State(
        @OptionTag(converter = ModelConverter::class)
        var model: Model = Model.DEFAULT,
        var customModel: String = "",
        var modelReasoningEffort: ModelReasoningEffort = ModelReasoningEffort.DEFAULT,
        var customModelReasoningEffort: String = "",
        var openFileOnChange: Boolean = false,
        var enableNotification: Boolean = false,
        var enableFullAccess: Boolean = false,
        var useSelectedModuleDirectory: Boolean = false,
        var customArgs: String = "",
        var mcpConfigInput: String = "",
        var isPowerShell73OrOver: Boolean = false, // Legacy flag, use winShell instead
        var winShell: WinShell = WinShell.POWERSHELL_LT_73
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        // Migrate legacy isPowerShell73OrOver flag to winShell enum
        if (state.isPowerShell73OrOver) {
            state.winShell = WinShell.POWERSHELL_73_PLUS
            state.isPowerShell73OrOver = false
        }

        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * Builds and returns the command-line arguments for codex based on current settings,
     * including notify command configuration.
     * 
     * @param port HTTP service port for notify command
     * @param workingDirectory Optional selected module or project directory to pass through --cd
     * @return A space-separated string of command-line arguments
     */
    fun getArgs(port: Int, workingDirectory: String? = null): String =
        CodexArgsBuilder.build(state, port, workingDirectory = workingDirectory).joinToString(" ")
}
