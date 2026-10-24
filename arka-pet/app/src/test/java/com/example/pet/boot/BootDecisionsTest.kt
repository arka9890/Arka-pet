package com.example.pet.boot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootDecisionsTest {
    @Test
    fun shouldAutoStart_trueOnlyWhenEnabledAndInstalled() {
        assertTrue(BootDecisions.shouldAutoStart(true, 1))
        assertTrue(BootDecisions.shouldAutoStart(true, 3))
        assertFalse(BootDecisions.shouldAutoStart(false, 1))
        assertFalse(BootDecisions.shouldAutoStart(true, 0))
        assertFalse(BootDecisions.shouldAutoStart(false, 0))
    }
}