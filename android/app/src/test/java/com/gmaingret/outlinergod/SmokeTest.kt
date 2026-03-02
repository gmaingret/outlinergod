package com.gmaingret.outlinergod

import org.junit.Test
import org.junit.Assert.*

class SmokeTest {
    @Test
    fun smoke_applicationId_isCorrect() {
        assertEquals("com.gmaingret.outlinergod", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun smoke_unitTestFramework_isAvailable() {
        assertEquals(2, 1 + 1)
    }
}
