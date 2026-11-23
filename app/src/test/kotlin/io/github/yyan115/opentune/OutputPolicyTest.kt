package io.github.yyan115.opentune

import io.github.yyan115.opentune.playback.OutputPolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Whether a phone may use the output for 24-bit songs. A phone that failed the check stays on 16-bit until its system changes. */
class OutputPolicyTest {
    private class Memory(var value: String? = null) : OutputPolicyState.Store {
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }

    @Test fun aNewPhoneMayUseTheOutput() {
        assertTrue(OutputPolicyState(Memory(), "build-1").floatAllowed)
    }

    @Test fun aFailureBlocksTheOutputAndIsRemembered() {
        val memory = Memory()
        val policy = OutputPolicyState(memory, "build-1")
        policy.block("size")
        assertFalse(policy.floatAllowed)
        assertEquals("size", policy.blockedReason)
        val again = OutputPolicyState(memory, "build-1").also { it.restore() }
        assertFalse(again.floatAllowed)
        assertEquals("size", again.blockedReason)
    }

    @Test fun aNewSystemBuildChecksAgain() {
        val memory = Memory()
        OutputPolicyState(memory, "build-1").block("size")
        val later = OutputPolicyState(memory, "build-2").also { it.restore() }
        assertTrue(later.floatAllowed)
        assertNull(memory.value)
    }

    @Test fun theListenerHearsOfTheBlockOnce() {
        val policy = OutputPolicyState(Memory(), "build-1")
        val heard = mutableListOf<String>()
        policy.onBlocked = { heard += it }
        policy.block("size"); policy.block("size again")
        assertEquals(listOf("size"), heard)
    }
}
