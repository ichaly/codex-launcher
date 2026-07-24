package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.http.HttpTriggerService
import com.github.x0x0b.codexlauncher.settings.CodexLauncherSettings
import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtilCore
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
        launchCodex(project, terminalManager, e)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val state = determineToolbarState(e.project)
        e.presentation.icon = state.icon
        e.presentation.text = state.text
        e.presentation.description = state.description
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun launchCodex(project: Project, terminalManager: CodexTerminalManager, event: AnActionEvent) {
        val baseDir = project.basePath ?: System.getProperty("user.home")

        try {
            val httpService = ApplicationManager.getApplication().service<HttpTriggerService>()
            val port = httpService.getActualPort().takeIf { it > 0 }
            val settings = project.service<CodexLauncherSettings>()
            if (port == null && (settings.state.enableNotification || settings.state.openFileOnChange)) {
                logger.warn("HTTP service port is not available")
                notify(
                    project,
                    "Codex UI will launch, but notifications and automatic file opening are unavailable",
                    NotificationType.WARNING
                )
            }

            val workingDirectory = if (settings.state.useSelectedModuleDirectory) {
                resolveModuleDirectory(project, event, baseDir)
            } else {
                baseDir
            }
            val command = buildCommand(settings.getArgs(port, workingDirectory))
            terminalManager.launch(baseDir, command)
            logger.info("Codex UI launched with working directory: $workingDirectory")
        } catch (t: Throwable) {
            logger.error("Failed to launch Codex UI", t)
            notify(project, "Failed to launch Codex UI: ${t.message}", NotificationType.ERROR)
        }
    }

    private fun resolveModuleDirectory(
        project: Project,
        event: AnActionEvent,
        projectDirectory: String
    ): String {
        val selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val module = event.getData(LangDataKeys.MODULE)
            ?: selectedFile?.let { ModuleUtilCore.findModuleForFile(it, project) }
            ?: return projectDirectory
        val contentRoots = ModuleRootManager.getInstance(module).contentRoots
        return contentRoots
            .firstOrNull { root -> selectedFile != null && VfsUtilCore.isAncestor(root, selectedFile, false) }
            ?.path
            ?: contentRoots.firstOrNull()?.path
            ?: projectDirectory
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
