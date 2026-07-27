package com.yahigod.homestashtv.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLedgerTest {
    @Test
    fun `ledger codec preserves command ids and expiry`() {
        val entries = listOf(
            LedgerEntry("one", 1000),
            LedgerEntry("two", 2000),
        )

        assertEquals(entries, decodeEntries(encodeEntries(entries)))
    }

    @Test
    fun `claim contract executes an id only once`() {
        val ledger = FakeCommandLedger()

        assertTrue(ledger.claim("one", 5000, 1000))
        assertFalse(ledger.claim("one", 5000, 1001))
        assertTrue(ledger.claim("two", 5000, 1002))
    }
}

internal class FakeCommandLedger : CommandLedger {
    private val ids = mutableSetOf<String>()

    override fun claim(
        commandId: String,
        expiresAtMs: Long,
        nowMs: Long,
    ): Boolean = ids.add(commandId)
}
