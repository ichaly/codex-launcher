package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.http.HttpTriggerService
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class LaunchCodexAction : AnAction(DEFAULT_TEXT, DEFAULT_DESCRIPTION, null), DumbAware {

    companion object {
        private const val CODEX_COMMAND = "codex"
        private const val NOTIFICATION_TITLE = "Codex UI"
        private const val DEFAULT_TEXT = "Launch Codex UI"
        private const val DEFAULT_DESCRIPTION = "Open a Codex UI terminal"
        private const val ACTIVE_TEXT = "Launch New Codex UI"
        private const val ACTIVE_DESCRIPTION = "Open another Codex UI terminal"
        private val DEFAULT_ICON = IconLoader.getIcon("/icons/codex.svg", LaunchCodexAction::class.java)
        private val ACTIVE_ICON = IconLoader.getIcon("/icons/codex_active.svg", LaunchCodexAction::class.java)
    }

    private val logger = logger<LaunchCodexAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            logger.warn("No project context available for Codex UI launch")
            return
        }

        val terminalManager = project.service<CodexTerminalManager>()
        launchCodex(project, terminalManager)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun launchCodex(project: Project, terminalManager: CodexTerminalManager) {
        val baseDir = project.basePath ?: System.getProperty("user.home")
        logger.info("Launching Codex UI in directory: $baseDir")

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort()
            if (port == 0) {
                logger.warn("HTTP service port is not available")
                notify(project, "HTTP service is not properly initialized", NotificationType.WARNING)
                return
            }

            val settings = project.service<CodexLauncherSettings>()
            val command = buildCommand(settings.getArgs(port, baseDir))
            terminalManager.launch(baseDir, command)
            logger.info("Codex UI command executed successfully: $command")
        } catch (t: Throwable) {
            logger.error("Failed to launch Codex UI", t)
            notify(project, "Failed to launch Codex UI: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun buildCommand(args: String): String {
        return buildString {
            append(CODEX_COMMAND)
            if (args.isNotBlank()) {
                append(' ')
                append(args)
            }
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexUI")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.error("Failed to show notification: $content", error)
        }
    }

    private fun determineToolbarState(project: Project?): ToolbarState {
        if (project == null) {
            return ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }

        val manager = project.service<CodexTerminalManager>()
        return if (manager.hasCodexTerminal()) {
            ToolbarState(ACTIVE_ICON, ACTIVE_TEXT, ACTIVE_DESCRIPTION)
        } else {
            ToolbarState(DEFAULT_ICON, DEFAULT_TEXT, DEFAULT_DESCRIPTION)
        }
    }

    private data class ToolbarState(val icon: Icon, val text: String, val description: String)
}
