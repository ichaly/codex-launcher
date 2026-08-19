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
 * Project-level service responsible for managing Codex UI terminals.
 * Encapsulates lookup, launch, focus, and text injection logic so actions stay thin.
 */
@Service(Service.Level.PROJECT)
class CodexTerminalManager(private val project: Project) {

    companion object {
        private val CODEX_TERMINAL_KEY = Key.create<Boolean>("codex.ui.codexTerminal")
    }

    private val logger = logger<CodexTerminalManager>()
    private val scriptFactory = CommandScriptFactory(project)

    private data class CodexTerminal(val widget: TerminalWidget, val content: Content)

    /**
     * Launches a new Codex UI terminal for the given command.
     * @throws Throwable when terminal creation or command execution fails.
     */
    fun launch(baseDir: String, command: String) {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        val terminalName = nextCodexTerminalName(terminalManager)

        var widget: TerminalWidget? = null
        try {
            widget = createTerminalWidget(terminalManager, baseDir, terminalName)
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
            logger.warn("Failed to inspect Codex UI terminal availability", t)
            false
        }
    }

    fun typeIntoCodexTerminal(text: String): Boolean {
        return try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val terminals = locateCodexTerminals(terminalManager)
            val selectedContent = resolveTerminalToolWindow(terminalManager)
                ?.contentManager
                ?.selectedContent
            val target = terminals.firstOrNull { it.content === selectedContent }
                ?: terminals.firstOrNull()
                ?: return false

            val sent = typeText(target.widget, text)
            if (sent) {
                focusCodexTerminal(terminalManager, target)
            } else {
                logger.warn("Failed to type into Codex UI terminal tab: ${target.content.displayName}")
            }
            sent
        } catch (t: Throwable) {
            logger.warn("Failed to type into Codex UI terminal", t)
            false
        }
    }

    private fun locateCodexTerminals(manager: TerminalToolWindowManager): List<CodexTerminal> = try {
        manager.getTerminalWidgets().asSequence().mapNotNull { widget ->
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
            displayName == "Codex UI" ||
            displayName.startsWith("Codex UI ") ||
            displayName.startsWith("Codex UI(") ||
            displayName.startsWith("Codex UI:")
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
                    logger.warn("Terminal tool window is not available for focusing Codex UI")
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
                        logger.warn("Failed to request focus for Codex UI terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex UI terminal", focusError)
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
        return if (count == 0) "Codex UI" else "Codex UI (${count + 1})"
    }

    private fun markCodexTerminal(manager: TerminalToolWindowManager, widget: TerminalWidget, displayName: String): Content? {
        return try {
            manager.getContainer(widget)?.content?.also { content ->
                content.putUserData(CODEX_TERMINAL_KEY, true)
                content.displayName = displayName
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex UI terminal metadata", t)
            null
        }
    }

    private fun clearCodexMetadata(manager: TerminalToolWindowManager, widget: TerminalWidget) {
        try {
            manager.getContainer(widget)?.content?.let { content ->
                content.putUserData(CODEX_TERMINAL_KEY, null)
            }
        } catch (t: Throwable) {
            logger.warn("Failed to clear Codex UI terminal metadata", t)
        }
    }

    private fun sendCommandToTerminal(
        widget: TerminalWidget,
        command: String
    ): Boolean {
        val plan = scriptFactory.buildPlan(command) ?: return false

        return try {
            widget.sendCommandToExecute(plan.command)
            true
        } catch (throwable: Throwable) {
            logger.warn("Failed to execute Codex command", throwable)
            runCatching { plan.cleanupOnFailure() }
            false
        }
    }

    private fun createTerminalWidget(
        manager: TerminalToolWindowManager,
        baseDir: String,
        terminalName: String
    ): TerminalWidget {
        val booleanType = Boolean::class.javaPrimitiveType
            ?: error("Boolean primitive type is unavailable")
        val methods = manager.javaClass.methods
        val newSession = methods.firstOrNull { method ->
            method.name == "createNewSession" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java, List::class.java, booleanType, booleanType)
            )
        }
        val shellWidget = methods.firstOrNull { method ->
            method.name == "createShellWidget" && method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java, booleanType, booleanType)
            )
        }
        val method = newSession ?: shellWidget ?: error("Terminal session creation API is unavailable")
        val arguments = if (method === newSession) {
            arrayOf<Any?>(baseDir, terminalName, null, true, true)
        } else {
            arrayOf<Any?>(baseDir, terminalName, true, true)
        }
        return method.invoke(manager, *arguments) as TerminalWidget
    }

    private fun typeText(widget: TerminalWidget, text: String): Boolean {
        val connector = runCatching { widget.ttyConnector }.getOrNull()
        if (connector != null) {
            return runCatching {
                connector.write(text)
                true
            }.getOrElse {
                logger.warn("Failed to write to Codex UI terminal connector", it)
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
                logger.warn("Failed to invoke typeText on Codex UI terminal", it)
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
                logger.warn("Failed to invoke pasteText on Codex UI terminal", it)
                false
            }
        }

        return false
    }
}
