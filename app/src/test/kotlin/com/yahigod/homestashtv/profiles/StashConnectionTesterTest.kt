package com.yahigod.homestashtv.profiles

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StashConnectionTesterTest {
    @Test
    fun `normalizes complete HTTP and HTTPS addresses`() {
        assertEquals("http://stash.test", normalizeServerUrl(" http://stash.test/ "))
        assertEquals("https://stash.test:9999", normalizeServerUrl("https://stash.test:9999/"))
    }

    @Test
    fun `rejects addresses without an HTTP scheme`() {
        val error = runCatching { normalizeServerUrl("stash.test") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `distinguishes successful and authentication responses`() {
        assertEquals(
            ConnectionTestResult.Success,
            classifyHttpResponse(HttpURLConnection.HTTP_OK),
        )
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            (classifyHttpResponse(HttpURLConnection.HTTP_UNAUTHORIZED)
                as ConnectionTestResult.Failure).kind,
        )
    }

    @Test
    fun `accepts a compatible Stash version response`() {
        assertEquals(
            ConnectionTestResult.Success,
            classifyGraphQlResponse(
                """{"data":{"version":{"version":"v0.31.1"}}}""",
            ),
        )
    }

    @Test
    fun `classifies GraphQL authentication errors`() {
        val result = classifyGraphQlResponse(
            """{"errors":[{"message":"unauthorized"}]}""",
        ) as ConnectionTestResult.Failure

        assertEquals(ConnectionFailureKind.AUTHENTICATION, result.kind)
    }

    @Test
    fun `rejects non Stash success responses`() {
        val result = classifyGraphQlResponse("""{"data":{"__typename":"Query"}}""")
            as ConnectionTestResult.Failure

        assertEquals(ConnectionFailureKind.SERVER, result.kind)
    }

    @Test
    fun `distinguishes DNS network and TLS failures`() {
        assertEquals(
            ConnectionFailureKind.DNS,
            (classifyConnectionFailure(UnknownHostException())
                as ConnectionTestResult.Failure).kind,
        )
        assertEquals(
            ConnectionFailureKind.NETWORK,
            (classifyConnectionFailure(SocketTimeoutException())
                as ConnectionTestResult.Failure).kind,
        )
        assertEquals(
            ConnectionFailureKind.TLS,
            (classifyConnectionFailure(SSLHandshakeException("certificate"))
                as ConnectionTestResult.Failure).kind,
        )
    }

    @Test
    fun `server errors remain distinct from transport failures`() {
        val result = classifyHttpResponse(HttpURLConnection.HTTP_INTERNAL_ERROR)
            as ConnectionTestResult.Failure

        assertEquals(ConnectionFailureKind.SERVER, result.kind)
        assertTrue(result.message.contains("500"))
    }
}
