package com.github.x0x0b.codexlauncher.actions

import com.github.x0x0b.codexlauncher.terminal.CodexTerminalManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileSystemItem

class SendRangeToCodexAction : AnAction(
    "Add to Codex UI",
    "Send the current selection, file, or Project View items to the Codex UI terminal",
    IconLoader.getIcon("/icons/codex_active.svg", SendRangeToCodexAction::class.java)
), DumbAware {

    companion object {
        private const val NOTIFICATION_TITLE = "Codex UI"
        private const val PROJECT_VIEW_POPUP_PREFIX = "ProjectViewPopup"
    }

    private val logger = logger<SendRangeToCodexAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = resolveVirtualFile(e)
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        val payload = InsertPayloadResolver.resolve(
            project = project,
            editor = editor,
            file = virtualFile,
            files = virtualFiles
        )

        if (payload == null) {
            notify(project, "Unable to determine selection or file context", NotificationType.INFORMATION)
            return
        }

        val insertText = InsertPayloadResolver.formatInsertText(payload)
        val terminalManager = project.service<CodexTerminalManager>()
        if (!terminalManager.hasCodexTerminal()) {
            notify(project, "Launch Codex UI first to send ranges", NotificationType.INFORMATION)
            return
        }

        if (!terminalManager.typeIntoCodexTerminal(insertText)) {
            notify(project, "Failed to send range to Codex UI terminal", NotificationType.WARNING)
            return
        }

        logger.info("Sent context to Codex UI terminal: $insertText")
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasCodexTerminal = project.service<CodexTerminalManager>().hasCodexTerminal()

        if (e.place.startsWith(PROJECT_VIEW_POPUP_PREFIX)) {
            e.presentation.isEnabledAndVisible = hasCodexTerminal
            return
        }

        if (!hasCodexTerminal) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val virtualFile = resolveVirtualFile(e)
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

        val hasFileContext = virtualFile != null
        val hasProjectSelection = !virtualFiles.isNullOrEmpty()
        val inContextBar = e.place == "EditorContextBar"

        val visible = hasFileContext || hasProjectSelection
        e.presentation.isVisible = visible
        e.presentation.isEnabled = visible && (!inContextBar || editor?.selectionModel?.hasSelection() == true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun notify(project: Project, content: String, type: NotificationType) {
        runCatching {
            val group = NotificationGroupManager.getInstance().getNotificationGroup("CodexUI")
            group.createNotification(NOTIFICATION_TITLE, content, type).notify(project)
        }.onFailure { error ->
            logger.warn("Failed to display notification: $content", error)
        }
    }

    private fun resolveVirtualFile(e: AnActionEvent): VirtualFile? {
        return e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile
            ?: (e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiFileSystemItem)?.virtualFile
    }
}
