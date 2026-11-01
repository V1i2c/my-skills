package com.v1i2c.focusharbor.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeTest {
    @Test
    fun `pin verifier accepts the right pin and rejects another`() {
        val verifier = PinHasher.create("4826".toCharArray(), iterations = 100_000)
        assertTrue(PinHasher.verify("4826".toCharArray(), verifier))
        assertFalse(PinHasher.verify("4827".toCharArray(), verifier))
    }

    @Test
    fun `emergency release needs a commitment and completed cooldown`() {
        assertFalse(StrictModeGate.canEmergencyRelease(1_000L, 5L, 300_000L, commitmentMatches = true))
        assertFalse(StrictModeGate.canEmergencyRelease(1_000L, 5L, 302_000L, commitmentMatches = false))
        assertTrue(StrictModeGate.canEmergencyRelease(1_000L, 5L, 301_000L, commitmentMatches = true))
    }
}
