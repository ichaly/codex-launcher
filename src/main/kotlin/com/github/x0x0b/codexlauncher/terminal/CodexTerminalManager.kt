package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory

/**
 * Project-level service responsible for managing Codex terminals.
 * Encapsulates lookup, launch, focus, and text injection logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
class CodexTerminalManager(private val project: Project) {

    companion object {
        private val CODEX_TERMINAL_KEY = Key.create<Boolean>("codex.launcher.codexTerminal")
    }

    private val logger = logger<CodexTerminalManager>()

    private data class CodexTerminal(val widget: TerminalWidget, val content: Content)

    /**
     * Launches a new Codex terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val terminalName = nextCodexTerminalName(terminalManager)

        var widget: TerminalWidget? = null
        try {
            widget = terminalManager.createShellWidget(baseDir, terminalName, true, true)
            val content = markCodexTerminal(terminalManager, widget, terminalName)
            if (!sendCommandToTerminal(widget, command)) {
                throw IllegalStateException("Failed to execute Codex command")
            }
            if (content != null) {
                focusCodexTerminal(terminalManager, CodexTerminal(widget, content))
            }
        } catch (sendError: Throwable) {
            widget?.let { clearCodexMetadata(terminalManager, it) }
            throw sendError
        }
    }

    fun hasCodexTerminal(): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            locateCodexTerminals(terminalManager).isNotEmpty()
        } catch (t: Throwable) {
            logger.warn("Failed to inspect Codex terminal availability", t)
            false
        }
    }

    fun typeIntoCodexTerminal(text: String): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            locateCodexTerminals(terminalManager).fold(false) { sentAny, terminal ->
                typeText(terminal.widget, text).also { sent ->
                    if (!sent) {
                        logger.warn("Failed to type into Codex terminal tab: ${terminal.content.displayName}")
                    }
                } || sentAny
            }
        } catch (t: Throwable) {
            logger.warn("Failed to type into Codex terminal", t)
            false
        }
    }

    private fun locateCodexTerminals(manager: TerminalToolWindowManager): List<CodexTerminal> = try {
        manager.terminalWidgets.asSequence().mapNotNull { widget ->
            val content = manager.getContainer(widget)?.content ?: return@mapNotNull null
            val isCodex = isCodexTerminalContent(content)
            if (!isCodex) {
                return@mapNotNull null
            }
            CodexTerminal(widget, content)
        }.toList()
    } catch (t: Throwable) {
        logger.warn("Failed to inspect existing terminal widgets", t)
        emptyList()
    }

    private fun isCodexTerminalContent(content: Content): Boolean {
        val displayName = content.displayName.trim()
        return content.getUserData(CODEX_TERMINAL_KEY) == true ||
            displayName == "Codex" ||
            displayName.startsWith("Codex ") ||
            displayName.startsWith("Codex(") ||
            displayName.startsWith("Codex:")
    }

    private fun focusCodexTerminal(
        manager: TerminalToolWindowManager,
        terminal: CodexTerminal
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            try {
                val toolWindow = resolveTerminalToolWindow(manager)
                if (toolWindow == null) {
                    logger.warn("Terminal tool window is not available for focusing Codex")
                    return@invokeLater
                }

                val contentManager = toolWindow.contentManager
                if (contentManager.selectedContent != terminal.content) {
                    contentManager.setSelectedContent(terminal.content, true)
                }

                toolWindow.activate({
                    try {
                        terminal.widget.requestFocus()
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for Codex terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(
        manager: TerminalToolWindowManager
    ) = manager.getToolWindow()
        ?: ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

    private fun nextCodexTerminalName(manager: TerminalToolWindowManager): String {
        val count = locateCodexTerminals(manager).size
        return if (count == 0) "Codex" else "Codex (${count + 1})"
    }

    private fun markCodexTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget, displayName: String): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(CODEX_TERMINAL_KEY, true)
                content.displayName = displayName
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex terminal metadata", t)
            null
        }
    }

    private fun clearCodexMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                content.putUserData(CODEX_TERMINAL_KEY, null)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Codex terminal metadata", t)
        }
    }

    private fun sendCommandToTerminal(
        widget: TerminalWidget,
        command: String
    ): Boolean {
        return try {
            widget.sendCommandToExecute(command)
            true
        } catch (t: Throwable) {
            logger.warn("Failed to execute Codex command", t)
            false
        }
    }

    private fun typeText(widget: TerminalWidget, text: String): Boolean {
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector != null) {
            return runCatching {
                connector.write(text)
                true
            }.getOrElse {
                logger.warn("Failed to write to Codex terminal connector", it)
                false
            }
        }

        val methods = widget.javaClass.methods
        val typeMethod = methods.firstOrNull { it.name == "typeText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (typeMethod != null) {
            return runCatching {
                typeMethod.isAccessible = true
                typeMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke typeText on Codex terminal", it)
                false
            }
        }

        val pasteMethod = methods.firstOrNull { it.name == "pasteText" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        if (pasteMethod != null) {
            return runCatching {
                pasteMethod.isAccessible = true
                pasteMethod.invoke(widget, text)
                true
            }.getOrElse {
                logger.warn("Failed to invoke pasteText on Codex terminal", it)
                false
            }
        }

        return false
    }
}
