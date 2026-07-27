package com.yahigod.homestashtv.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun `backoff doubles to a bounded maximum and resets`() {
        val policy = ReconnectPolicy(initialDelayMs = 1000, maximumDelayMs = 8000)

        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 8000L), List(5) {
            policy.nextDelayMs()
        })
        policy.reset()
        assertEquals(1000L, policy.nextDelayMs())
    }
}
