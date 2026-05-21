package com.github.x0x0b.codexlauncher.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class InsertPayloadResolverTest : BasePlatformTestCase() {

    fun testSelectionLineRangeIsResolved() {
        val fileText = """
            class Foo {
                fun bar() {
                    <selection>val x = 1
                    val y = 2</selection>
                }
            }
        """.trimIndent()

        myFixture.configureByText("Foo.kt", fileText)

        val project = project
        val editor = myFixture.editor
        val virtualFile = myFixture.file.virtualFile

        val payload = InsertPayloadResolver.resolve(
            project = project,
            editor = editor,
            file = virtualFile
        )

        assertNotNull("Payload should be resolved for a selected range", payload)
        payload!!

        assertTrue("Relative path should end with file name", payload.relativePath.endsWith("Foo.kt"))

        val range = payload.lineRange
        assertNotNull("Line range must be present when selection exists", range)
        range!!

        // Selection spans the two val-lines inside bar()
        assertEquals(3, range.start)
        assertEquals(4, range.end)
    }

    fun testNoSelectionProducesNoLineRange() {
        val fileText = """
            class Foo {
                fun bar() {
                    val x = 1
                    val y = 2
                }
            }
        """.trimIndent()

        myFixture.configureByText("Foo.kt", fileText)

        // Place caret inside the method body, no explicit selection
        val offset = myFixture.file.text.indexOf("val x")
        myFixture.editor.caretModel.moveToOffset(offset)

        val project = project
        val editor = myFixture.editor
        val virtualFile = myFixture.file.virtualFile

        val payload = InsertPayloadResolver.resolve(
            project = project,
            editor = editor,
            file = virtualFile
        )

        assertNotNull("Payload should be resolved when caret is inside a class", payload)
        payload!!

        // Without an explicit selection we now only send the file path,
        // so there should be no line range.
        val range = payload.lineRange
        assertNull("Line range must be null when there is no selection", range)
    }

    fun testProjectFileSelectionProducesMultipleReferences() {
        val first = myFixture.tempDirFixture.createFile("src/Foo.kt", "class Foo")
        val second = myFixture.tempDirFixture.createFile("src/Bar.kt", "class Bar")

        val payload = InsertPayloadResolver.resolve(
            project = project,
            files = arrayOf(first, second)
        )

        assertNotNull("Payload should be resolved for Project View files", payload)
        payload!!

        assertEquals(2, payload.relativePaths.size)
        assertTrue(payload.relativePaths[0].endsWith("src/Foo.kt"))
        assertTrue(payload.relativePaths[1].endsWith("src/Bar.kt"))
        assertNull("Project View payloads should not include line ranges", payload.lineRange)
        assertEquals(
            "${payload.relativePaths[0]} ${payload.relativePaths[1]} ",
            InsertPayloadResolver.formatInsertText(payload)
        )
    }

    fun testProjectDirectorySelectionOmitsSelectedDescendants() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("src")
        val child = myFixture.tempDirFixture.createFile("src/Foo.kt", "class Foo")

        val payload = InsertPayloadResolver.resolve(
            project = project,
            file = dir,
            files = arrayOf(child)
        )

        assertNotNull("Payload should be resolved for Project View directories", payload)
        payload!!

        assertEquals(1, payload.relativePaths.size)
        assertTrue(payload.relativePath.endsWith("src"))
        assertFalse("Directory selection should not duplicate descendants", payload.relativePath.contains("Foo.kt"))
    }
}
