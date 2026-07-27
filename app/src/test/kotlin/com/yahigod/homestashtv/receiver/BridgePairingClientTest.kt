package com.yahigod.homestashtv.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BridgePairingClientTest {
    @Test
    fun `normalizes HTTP bridge addresses and creates websocket URL`() {
        assertEquals(
            "http://bridge.test:8791",
            normalizeBridgeUrl(" http://bridge.test:8791/ "),
        )
        assertEquals(
            "wss://bridge.test/api/v1/receivers/connect",
            websocketUrl("https://bridge.test"),
        )
    }

    @Test
    fun `rejects credentials paths and unsupported schemes`() {
        listOf(
            "ftp://bridge.test",
            "http://user:pass@bridge.test",
            "http://bridge.test/path",
        ).forEach {
            assertThrows(PairingException::class.java) {
                normalizeBridgeUrl(it)
            }
        }
    }
}
