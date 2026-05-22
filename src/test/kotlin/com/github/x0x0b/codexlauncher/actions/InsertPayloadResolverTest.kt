package com.github.x0x0b.codexlauncher.actions

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.openapi.application.ApplicationManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.swing.SwingUtilities

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InsertPayloadResolverTest {

    private lateinit var myFixture: CodeInsightTestFixture

    @BeforeAll
    fun setUp() {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createLightFixtureBuilder(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR, "InsertPayloadResolverTest")
        val fixture = builder.fixture
        myFixture = factory.createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
        myFixture.setUp()
    }

    @AfterAll
    fun tearDown() {
        runInEdt {
            try {
                myFixture.tearDown()
            } catch (e: Exception) {
                // Ignore teardown errors in tests if any, or log them
            }
        }
    }

    @Test
    fun testSelectionLineRangeIsResolved() {
        runInEdt {
            val fileText = """
                class Foo {
                    fun bar() {
                        <selection>val x = 1
                        val y = 2</selection>
                    }
                }
            """.trimIndent()

            myFixture.configureByText("Foo.kt", fileText)

            val project = myFixture.project
            val editor = myFixture.editor
            val virtualFile = myFixture.file.virtualFile

            val payload = InsertPayloadResolver.resolve(
                project = project,
                editor = editor,
                file = virtualFile
            )

            assertNotNull(payload, "Payload should be resolved for a selected range")
            payload!!

            assertTrue(payload.relativePath.endsWith("Foo.kt"), "Relative path should end with file name")

            val range = payload.lineRange
            assertNotNull(range, "Line range must be present when selection exists")
            range!!

            // Selection spans the two val-lines inside bar()
            assertEquals(3, range.start)
            assertEquals(4, range.end)
        }
    }

    @Test
    fun testNoSelectionProducesNoLineRange() {
        runInEdt {
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

            val project = myFixture.project
            val editor = myFixture.editor
            val virtualFile = myFixture.file.virtualFile

            val payload = InsertPayloadResolver.resolve(
                project = project,
                editor = editor,
                file = virtualFile
            )

            assertNotNull(payload, "Payload should be resolved when caret is inside a class")
            payload!!

            // Without an explicit selection we now only send the file path,
            // so there should be no line range.
            val range = payload.lineRange
            assertNull(range, "Line range must be null when there is no selection")
        }
    }

    private fun runInEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            ApplicationManager.getApplication().invokeAndWait(block)
        }
    }

    @Test
    fun testProjectFileSelectionProducesMultipleReferences() {
        runInEdt {
            val first = myFixture.tempDirFixture.createFile("src/Foo.kt", "class Foo")
            val second = myFixture.tempDirFixture.createFile("src/Bar.kt", "class Bar")

            val payload = InsertPayloadResolver.resolve(
                project = myFixture.project,
                files = arrayOf(first, second)
            )

            assertNotNull(payload, "Payload should be resolved for Project View files")
            payload!!

            assertEquals(2, payload.relativePaths.size)
            assertTrue(payload.relativePaths[0].endsWith("src/Foo.kt"))
            assertTrue(payload.relativePaths[1].endsWith("src/Bar.kt"))
            assertNull(payload.lineRange, "Project View payloads should not include line ranges")
            assertEquals(
                "${payload.relativePaths[0]} ${payload.relativePaths[1]} ",
                InsertPayloadResolver.formatInsertText(payload)
            )
        }
    }

    @Test
    fun testProjectDirectorySelectionOmitsSelectedDescendants() {
        runInEdt {
            val dir = myFixture.tempDirFixture.findOrCreateDir("directory-selection")
            val child = myFixture.tempDirFixture.createFile("directory-selection/Foo.kt", "class Foo")

            val payload = InsertPayloadResolver.resolve(
                project = myFixture.project,
                file = dir,
                files = arrayOf(child)
            )

            assertNotNull(payload, "Payload should be resolved for Project View directories")
            payload!!

            assertEquals(1, payload.relativePaths.size)
            assertTrue(payload.relativePath.endsWith("directory-selection"))
            assertFalse(payload.relativePath.contains("Foo.kt"), "Directory selection should not duplicate descendants")
        }
    }
}
