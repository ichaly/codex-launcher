package com.github.x0x0b.codexlauncher.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PublicMethodLookupTest {

    @Test
    fun findPublicMethod_nonPublicImplementation_invokesThroughPublicInterface() {
        val target: CommandTarget = HiddenCommandTarget()
        val method = CommandTarget::class.java.findPublicMethod("execute", String::class.java)

        assertNotNull(method)
        method!!.invoke(target, "codex")

        assertEquals("codex", target.executedCommand)
    }

    interface CommandTarget {
        var executedCommand: String?

        fun execute(command: String)
    }

    private class HiddenCommandTarget : CommandTarget {
        override var executedCommand: String? = null

        override fun execute(command: String) {
            executedCommand = command
        }
    }
}
