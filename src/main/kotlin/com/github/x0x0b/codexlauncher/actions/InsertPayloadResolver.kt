package com.github.x0x0b.codexlauncher.actions

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object InsertPayloadResolver {

    private val logger = logger<InsertPayloadResolver>()

    fun resolve(
        project: Project,
        editor: Editor? = null,
        file: VirtualFile? = null,
        files: Array<VirtualFile>? = null
    ): InsertPayload? {
        val editorManager = FileEditorManager.getInstance(project)
        val selectedFiles = files?.takeIf { it.isNotEmpty() }
        if (editor == null && selectedFiles != null) {
            val focusedFile = file
            val references = normalizeProjectSelection(selectedFiles, focusedFile)
                .mapNotNull { selectedFile -> resolveRelativePath(project, selectedFile) }

            return references.takeIf { it.isNotEmpty() }?.let { InsertPayload(it, null) }
        }

        val targetFile = file ?: editorManager.selectedFiles.firstOrNull() ?: return null
        val relativePath = resolveRelativePath(project, targetFile) ?: return null

        val targetEditor = editor ?: editorManager.selectedTextEditor
        val lineRange = targetEditor?.let { resolveSelectionLineRange(it) }

        return InsertPayload(listOf(relativePath), lineRange)
    }

    fun formatInsertText(payload: InsertPayload): String {
        return payload.relativePaths.joinToString(separator = " ", postfix = " ") { relativePath ->
            buildString {
                append(relativePath)
                payload.lineRange?.let { range ->
                    append(':')
                    append(range.start)
                    range.end?.takeIf { it != range.start }?.let { end ->
                        append('-')
                        append(end)
                    }
                }
            }
        }
    }

    private fun resolveRelativePath(project: Project, file: VirtualFile): String? {
        val rawPath = file.canonicalPath ?: file.presentableUrl ?: file.path
        val basePath = project.basePath ?: return rawPath
        return runCatching {
            val base = Path.of(basePath).normalize()
            val target = Path.of(rawPath).normalize()
            if (target.startsWith(base)) base.relativize(target).toString() else rawPath
        }.getOrElse {
            logger.warn("Failed to compute relative path for $rawPath", it)
            rawPath
        }
    }

    private fun resolveSelectionLineRange(editor: Editor): LineRange? {
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection() || selectionModel.selectedText.isNullOrEmpty()) {
            return null
        }

        return toLineRange(editor.document, selectionModel.selectionStart, selectionModel.selectionEnd)
    }

    private fun toLineRange(document: Document, startOffset: Int, endOffset: Int): LineRange? {
        if (startOffset < 0 || endOffset < startOffset) {
            return null
        }

        val startLine = runCatching { document.getLineNumber(startOffset) }.getOrElse {
            logger.warn("Failed to resolve start line", it)
            return null
        }

        val adjustedEnd = when {
            endOffset <= startOffset -> startOffset
            endOffset == document.textLength -> endOffset
            else -> endOffset - 1
        }

        val endLine = runCatching { document.getLineNumber(adjustedEnd.coerceAtLeast(startOffset)) }.getOrElse {
            logger.warn("Failed to resolve end line", it)
            startLine
        }

        val start = startLine + 1
        val end = (endLine + 1).takeIf { it > start }
        return LineRange(start, end)
    }

    private fun normalizeProjectSelection(
        selectedFiles: Array<VirtualFile>,
        focusedFile: VirtualFile?
    ): List<VirtualFile> {
        val all = LinkedHashMap<String, VirtualFile>()
        selectedFiles.forEach { all[it.path] = it }

        if (focusedFile != null && focusedFile.isDirectory) {
            all.putIfAbsent(focusedFile.path, focusedFile)
        }

        val kept = mutableListOf<VirtualFile>()
        for (candidate in all.values.sortedBy { it.path.length }) {
            val candidatePath = candidate.path.trimEnd('/')
            val hasSelectedParent = kept.any { parent ->
                val parentPath = parent.path.trimEnd('/')
                candidatePath == parentPath || candidatePath.startsWith("$parentPath/")
            }
            if (!hasSelectedParent) {
                kept.add(candidate)
            }
        }

        return kept
    }
}

data class InsertPayload(val relativePaths: List<String>, val lineRange: LineRange?) {
    val relativePath: String
        get() = relativePaths.first()
}

data class LineRange(val start: Int, val end: Int?)
