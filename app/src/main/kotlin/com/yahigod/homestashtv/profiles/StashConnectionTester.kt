package com.yahigod.homestashtv.profiles

import com.yahigod.homestashtv.playback.stashAuthorizationHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ConnectionFailureKind {
    INVALID_ADDRESS,
    DNS,
    NETWORK,
    TLS,
    AUTHENTICATION,
    SERVER,
}

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult

    data class Failure(
        val kind: ConnectionFailureKind,
        val message: String,
    ) : ConnectionTestResult
}

class StashConnectionTester {
    suspend fun test(
        serverUrl: String,
        apiKey: String?,
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val normalizedUrl = try {
            normalizeServerUrl(serverUrl)
        } catch (_: IllegalArgumentException) {
            return@withContext failureFor(ConnectionFailureKind.INVALID_ADDRESS)
        }

        val connection = try {
            URL("$normalizedUrl/graphql").openConnection() as HttpURLConnection
        } catch (error: Exception) {
            return@withContext classifyConnectionFailure(error)
        }

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            stashAuthorizationHeaders(apiKey).forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.bufferedWriter().use {
                it.write("""{"query":"query { version { version } }"}""")
            }
            when (val httpResult = classifyHttpResponse(connection.responseCode)) {
                is ConnectionTestResult.Failure -> httpResult
                ConnectionTestResult.Success -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    classifyGraphQlResponse(response)
                }
            }
        } catch (error: Exception) {
            classifyConnectionFailure(error)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun normalizeServerUrl(serverUrl: String): String {
    val normalized = serverUrl.trim().trimEnd('/')
    val uri = runCatching { URI(normalized) }.getOrNull()
        ?: throw IllegalArgumentException("Invalid server address.")
    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("Invalid server address.")
    }
    return normalized
}

internal fun classifyHttpResponse(responseCode: Int): ConnectionTestResult =
    when (responseCode) {
        in 200..299 -> ConnectionTestResult.Success
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_FORBIDDEN,
        -> failureFor(ConnectionFailureKind.AUTHENTICATION)
        else -> failureFor(ConnectionFailureKind.SERVER, responseCode)
    }

internal fun classifyGraphQlResponse(response: String): ConnectionTestResult {
    val root = runCatching { JSONObject(response) }.getOrNull()
        ?: return failureFor(ConnectionFailureKind.SERVER)

    val errors = root.optJSONArray("errors")
    if (errors != null && errors.length() > 0) {
        val authenticationFailure = (0 until errors.length()).any { index ->
            val message = errors.optJSONObject(index)
                ?.optString("message")
                ?.lowercase()
                .orEmpty()
            AUTHENTICATION_ERROR_TERMS.any { term -> message.contains(term) }
        }
        return failureFor(
            if (authenticationFailure) {
                ConnectionFailureKind.AUTHENTICATION
            } else {
                ConnectionFailureKind.SERVER
            },
        )
    }

    val version = root.optJSONObject("data")
        ?.optJSONObject("version")
        ?.optString("version")
        ?.takeIf { it.isNotBlank() }
    return if (version != null) {
        ConnectionTestResult.Success
    } else {
        failureFor(ConnectionFailureKind.SERVER)
    }
}

internal fun classifyConnectionFailure(error: Throwable): ConnectionTestResult {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is UnknownHostException } ->
            failureFor(ConnectionFailureKind.DNS)
        causes.any { it is SSLException } ->
            failureFor(ConnectionFailureKind.TLS)
        causes.any {
            it is ConnectException ||
                it is NoRouteToHostException ||
                it is SocketTimeoutException
        } -> failureFor(ConnectionFailureKind.NETWORK)
        else -> failureFor(ConnectionFailureKind.NETWORK)
    }
}

private fun failureFor(
    kind: ConnectionFailureKind,
    responseCode: Int? = null,
): ConnectionTestResult.Failure {
    val message = when (kind) {
        ConnectionFailureKind.INVALID_ADDRESS ->
            "Enter a complete HTTP or HTTPS server address."
        ConnectionFailureKind.DNS ->
            "DNS could not resolve this server name. Check the address or use its LAN IP."
        ConnectionFailureKind.NETWORK ->
            "The server could not be reached. Check the address, port, and local network."
        ConnectionFailureKind.TLS ->
            "The secure connection failed. Check the HTTPS certificate and server name."
        ConnectionFailureKind.AUTHENTICATION ->
            "Stash rejected the API key. Check or replace the key."
        ConnectionFailureKind.SERVER ->
            if (responseCode != null) {
                "Stash returned HTTP $responseCode. Check the server and GraphQL endpoint."
            } else {
                "The server did not return a compatible Stash GraphQL response."
            }
    }
    return ConnectionTestResult.Failure(kind, message)
}

private val AUTHENTICATION_ERROR_TERMS = listOf(
    "unauthorized",
    "forbidden",
    "access denied",
    "authentication",
)

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
