package com.novarehab.video.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStateTest {
    @Test
    fun stabilizedStatesAreExplicit() {
        assertEquals(CallState.IDLE, CallState.valueOf("IDLE"))
        assertEquals(CallState.RINGING, CallState.valueOf("RINGING"))
        assertEquals(CallState.ACTIVE, CallState.valueOf("ACTIVE"))
        assertEquals(CallState.BUSY, CallState.valueOf("BUSY"))
        assertEquals(CallState.MISSED, CallState.valueOf("MISSED"))
    }
}
