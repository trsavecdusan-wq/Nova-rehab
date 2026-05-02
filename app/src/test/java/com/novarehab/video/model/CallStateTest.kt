package com.novarehab.video.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CallStateTest {
    @Test
    fun phaseOneStatesAreExplicit() {
        assertEquals(CallState.IDLE, CallState.valueOf("IDLE"))
        assertEquals(CallState.CALLING, CallState.valueOf("CALLING"))
        assertEquals(CallState.ACCEPTED, CallState.valueOf("ACCEPTED"))
        assertEquals(CallState.REJECTED, CallState.valueOf("REJECTED"))
        assertEquals(CallState.ERROR, CallState.valueOf("ERROR"))
    }
}
