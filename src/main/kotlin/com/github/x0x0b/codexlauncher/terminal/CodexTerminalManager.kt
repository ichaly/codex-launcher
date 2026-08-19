package com.github.x0x0b.codexlauncher.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content

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
        val terminalManager = terminalManager()
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
            val terminalManager = terminalManager()
            locateCodexTerminals(terminalManager).isNotEmpty()
        } catch (t: Throwable) {
            logger.warn("Failed to inspect Codex UI terminal availability", t)
            false
        }
    }

    fun typeIntoCodexTerminal(text: String): Boolean {
        return try {
            val terminalManager = terminalManager()
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

    private fun locateCodexTerminals(manager: Any): List<CodexTerminal> = try {
        invokeManagerMethod(manager, "getTerminalWidgets")
            .let { it as? Iterable<*> ?: emptyList<Any>() }
            .asSequence().mapNotNull { widget ->
            val terminalWidget = widget as? TerminalWidget ?: return@mapNotNull null
            val content = managerContainer(manager, terminalWidget) ?: return@mapNotNull null
            val isCodex = isCodexTerminalContent(content)
            if (!isCodex) {
                return@mapNotNull null
            }
            CodexTerminal(terminalWidget, content)
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
        manager: Any,
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

                invokeMethod(toolWindow, "activate", Runnable {
                    try {
                        invokeMethod(terminal.widget, "requestFocus")
                    } catch (focusError: Throwable) {
                        logger.warn("Failed to request focus for Codex UI terminal", focusError)
                    }
                }, true)
            } catch (focusError: Throwable) {
                logger.warn("Failed to focus existing Codex UI terminal", focusError)
            }
        }
    }

    private fun resolveTerminalToolWindow(manager: Any) = invokeManagerMethod(manager, "getToolWindow")
        ?.let { it as? com.intellij.openapi.wm.ToolWindow }
        ?: fallbackTerminalToolWindow()

    private fun fallbackTerminalToolWindow(): com.intellij.openapi.wm.ToolWindow? {
        val managerClass = Class.forName("com.intellij.openapi.wm.ToolWindowManager")
        val companion = managerClass.getField("Companion").get(null)
        val manager = invokeMethod(companion, "getInstance", project)
        return manager?.let { invokeMethod(it, "getToolWindow", "Terminal") }
            as? com.intellij.openapi.wm.ToolWindow
    }

    private fun nextCodexTerminalName(manager: Any): String {
        val count = locateCodexTerminals(manager).size
        return if (count == 0) "Codex UI" else "Codex UI (${count + 1})"
    }

    private fun markCodexTerminal(manager: Any, widget: TerminalWidget, displayName: String): Content? {
        return try {
            managerContainer(manager, widget)?.also { content ->
                content.putUserData(CODEX_TERMINAL_KEY, true)
                content.displayName = displayName
            }
        } catch (t: Throwable) {
            logger.warn("Failed to tag Codex UI terminal metadata", t)
            null
        }
    }

    private fun clearCodexMetadata(manager: Any, widget: TerminalWidget) {
        try {
            managerContainer(manager, widget)?.let { content ->
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
            invokeTerminalMethod(widget, "sendCommandToExecute", plan.command)
            true
        } catch (throwable: Throwable) {
            logger.warn("Failed to execute Codex command", throwable)
            runCatching { plan.cleanupOnFailure() }
            false
        }
    }

    private fun createTerminalWidget(
        manager: Any,
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
        val connector = findTerminalMethod(widget, "getTtyConnector")?.let { method ->
            runCatching { method.invoke(widget) }.getOrNull()
        }
        if (connector != null) {
            val writeMethod = connector.javaClass.methods.firstOrNull { method ->
                method.name == "write" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }
            if (writeMethod != null) {
                return runCatching {
                    writeMethod.invoke(connector, text)
                    true
                }.getOrElse {
                    logger.warn("Failed to write to Codex UI terminal connector", it)
                    false
                }
            } else {
                logger.warn("Terminal connector does not expose a writable channel")
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

    private fun invokeTerminalMethod(widget: TerminalWidget, methodName: String, argument: String) {
        val method = findTerminalMethod(widget, methodName)
            ?: error("Terminal method unavailable: $methodName")
        method.invoke(widget, argument)
    }

    private fun findTerminalMethod(widget: TerminalWidget, methodName: String) =
        widget.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.contentEquals(arrayOf(String::class.java))
        }

    private fun terminalManager(): Any {
        val managerClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
        val getInstance = managerClass.getMethod("getInstance", Project::class.java)
        return getInstance.invoke(null, project)
    }

    private fun managerContainer(manager: Any, widget: TerminalWidget): Content? =
        invokeManagerMethod(manager, "getContainer", widget)
            ?.let { it.javaClass.getMethod("getContent").invoke(it) as? Content }

    private fun invokeManagerMethod(manager: Any, methodName: String, vararg arguments: Any?): Any? {
        return invokeMethod(manager, methodName, *arguments)
    }

    private fun invokeMethod(target: Any, methodName: String, vararg arguments: Any?): Any? {
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount == arguments.size &&
                candidate.parameterTypes.withIndex().all { (index, type) ->
                    val argument = arguments[index]
                    argument == null || type.isAssignableFrom(argument.javaClass)
                }
        } ?: error("Terminal manager method unavailable: $methodName")
        return method.invoke(target, *arguments)
    }
}
