package com.yahigod.homestashtv.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ScenePlaybackSource(
    val sceneId: String,
    val title: String,
    val streamUrl: String,
)

class SceneResolutionException(message: String) : Exception(message)

class StashSceneResolver {
    suspend fun resolve(
        serverUrl: String,
        apiKey: String,
        sceneId: String,
    ): ScenePlaybackSource = withContext(Dispatchers.IO) {
        validateConfiguration(serverUrl, apiKey, sceneId)

        val connection = openGraphQlConnection(serverUrl)
        try {
            val requestBody = JSONObject()
                .put("query", FIND_SCENE_QUERY)
                .put("variables", JSONObject().put("id", sceneId))
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            stashAuthorizationHeaders(apiKey).forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                -> throw SceneResolutionException(
                    "Stash rejected the API key. Check the configured server profile.",
                )

                HttpURLConnection.HTTP_NOT_FOUND -> throw SceneResolutionException(
                    "The Stash GraphQL endpoint was not found. Check the server address.",
                )

                !in 200..299 -> throw SceneResolutionException(
                    "Stash returned HTTP ${connection.responseCode} while resolving the scene.",
                )
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseSceneResponse(response, serverUrl, sceneId)
        } catch (error: SceneResolutionException) {
            throw error
        } catch (_: Exception) {
            throw SceneResolutionException(
                "Could not reach Stash. Check the server address and local network.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun openGraphQlConnection(serverUrl: String): HttpURLConnection {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        val uri = URI(normalizedServerUrl)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw SceneResolutionException(
                "The server address must be a complete HTTP or HTTPS URL.",
            )
        }

        return URL("$normalizedServerUrl/graphql").openConnection() as HttpURLConnection
    }

    private fun validateConfiguration(
        serverUrl: String,
        apiKey: String,
        sceneId: String,
    ) {
        if (serverUrl.isBlank() || apiKey.isBlank() || sceneId.isBlank()) {
            throw SceneResolutionException(
                "The server address, API key, and scene ID are required.",
            )
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        private val FIND_SCENE_QUERY = """
            query FindScene(${'$'}id: ID!) {
              findScene(id: ${'$'}id) {
                id
                title
                paths {
                  stream
                }
              }
            }
        """.trimIndent()
    }
}

internal fun stashAuthorizationHeaders(apiKey: String): Map<String, String> =
    mapOf("Authorization" to "Bearer $apiKey")

internal fun parseSceneResponse(
    response: String,
    serverUrl: String,
    requestedSceneId: String,
): ScenePlaybackSource {
    val root = try {
        JSONObject(response)
    } catch (_: Exception) {
        throw SceneResolutionException("Stash returned an unreadable scene response.")
    }

    if (root.optJSONArray("errors")?.length()?.let { it > 0 } == true) {
        throw SceneResolutionException(
            "Stash could not resolve that scene. Check the scene ID and API access.",
        )
    }

    val scene = root.optJSONObject("data")?.optJSONObject("findScene")
        ?: throw SceneResolutionException("That scene does not exist in this Stash server.")
    val rawStreamUrl = scene.optJSONObject("paths")
        ?.optString("stream")
        ?.takeIf { it.isNotBlank() }
        ?: throw SceneResolutionException("Stash did not provide a playable source for this scene.")
    val absoluteStreamUrl = URI(serverUrl.trim().trimEnd('/') + "/")
        .resolve(rawStreamUrl)
        .toString()

    return ScenePlaybackSource(
        sceneId = scene.optString("id").ifBlank { requestedSceneId },
        title = scene.optString("title").ifBlank { "Scene $requestedSceneId" },
        streamUrl = removeApiKeyFromUrl(absoluteStreamUrl),
    )
}

internal fun removeApiKeyFromUrl(streamUrl: String): String {
    val fragmentStart = streamUrl.indexOf('#')
    val withoutFragment = if (fragmentStart >= 0) streamUrl.substring(0, fragmentStart) else streamUrl
    val fragment = if (fragmentStart >= 0) streamUrl.substring(fragmentStart) else ""
    val queryStart = withoutFragment.indexOf('?')
    if (queryStart < 0) {
        return streamUrl
    }

    val retainedParameters = withoutFragment
        .substring(queryStart + 1)
        .split('&')
        .filterNot { parameter ->
            val encodedName = parameter.substringBefore('=')
            URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
                .equals("apikey", ignoreCase = true)
        }

    val base = withoutFragment.substring(0, queryStart)
    val query = retainedParameters
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "&", prefix = "?")
        .orEmpty()
    return base + query + fragment
}
