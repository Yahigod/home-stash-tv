package com.yahigod.homestashtv.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
        validateConfiguration(serverUrl, sceneId)

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

    suspend fun resolveQueue(
        serverUrl: String,
        apiKey: String,
        sceneIds: List<String>,
        requestedStartIndex: Int,
    ): ResolvedPlaybackQueue = withContext(Dispatchers.IO) {
        if (sceneIds.isEmpty() || requestedStartIndex !in sceneIds.indices) {
            throw QueueResolutionException("Start index is outside the queue.")
        }
        val connection = openGraphQlConnection(serverUrl)
        try {
            val requestBody = JSONObject()
                .put("query", FIND_SCENES_QUERY)
                .put("variables", JSONObject().put("ids", JSONArray(sceneIds)))
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = QUEUE_READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            stashAuthorizationHeaders(apiKey).forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                -> throw QueueResolutionException(
                    "Stash rejected the API key. Check the configured server profile.",
                )

                HttpURLConnection.HTTP_NOT_FOUND -> throw QueueResolutionException(
                    "The Stash GraphQL endpoint was not found. Check the server address.",
                )

                !in 200..299 -> throw QueueResolutionException(
                    "Stash returned HTTP ${connection.responseCode} while resolving the queue.",
                )
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (queueResponseNeedsIndividualFallback(response)) {
                resolveQueueIndividually(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    sceneIds = sceneIds,
                    requestedStartIndex = requestedStartIndex,
                )
            } else {
                parseQueueResponse(response, serverUrl, sceneIds, requestedStartIndex)
            }
        } catch (error: QueueResolutionException) {
            throw error
        } catch (_: Exception) {
            throw QueueResolutionException(
                "Could not reach Stash. Check the server address and local network.",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveQueueIndividually(
        serverUrl: String,
        apiKey: String,
        sceneIds: List<String>,
        requestedStartIndex: Int,
    ): ResolvedPlaybackQueue {
        val request = buildIndividualQueueRequest(sceneIds)
        val connection = openGraphQlConnection(serverUrl)
        try {
            val requestBody = JSONObject()
                .put("query", request.query)
                .put("variables", request.variables)
                .toString()

            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = QUEUE_READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            stashAuthorizationHeaders(apiKey).forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN,
                -> throw QueueResolutionException(
                    "Stash rejected the API key. Check the configured server profile.",
                )

                HttpURLConnection.HTTP_NOT_FOUND -> throw QueueResolutionException(
                    "The Stash GraphQL endpoint was not found. Check the server address.",
                )

                !in 200..299 -> throw QueueResolutionException(
                    "Stash returned HTTP ${connection.responseCode} while resolving the queue.",
                )
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return parseIndividualQueueResponse(
                response = response,
                serverUrl = serverUrl,
                requestedSceneIds = sceneIds,
                requestedStartIndex = requestedStartIndex,
                aliasesBySceneId = request.aliasesBySceneId,
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
        sceneId: String,
    ) {
        if (serverUrl.isBlank() || sceneId.isBlank()) {
            throw SceneResolutionException(
                "The server address and scene ID are required.",
            )
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val QUEUE_READ_TIMEOUT_MS = 30_000

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

        private val FIND_SCENES_QUERY = """
            query FindScenes(${'$'}ids: [ID!]) {
              findScenes(ids: ${'$'}ids) {
                scenes {
                  id
                  title
                  paths {
                    stream
                  }
                }
              }
            }
        """.trimIndent()
    }
}

internal data class IndividualQueueRequest(
    val query: String,
    val variables: JSONObject,
    val aliasesBySceneId: Map<String, String>,
)

internal fun buildIndividualQueueRequest(sceneIds: List<String>): IndividualQueueRequest {
    val aliasesBySceneId = sceneIds.distinct().mapIndexed { index, sceneId ->
        sceneId to "scene$index"
    }.toMap()
    val variables = JSONObject()
    aliasesBySceneId.keys.forEachIndexed { index, sceneId ->
        variables.put("id$index", sceneId)
    }
    val variableDefinitions = (0 until aliasesBySceneId.size).joinToString(", ") { index ->
        "\$id$index: ID!"
    }
    val fields = aliasesBySceneId.values.mapIndexed { index, alias ->
        """
        $alias: findScene(id: ${'$'}id$index) {
          id
          title
          paths {
            stream
          }
        }
        """.trimIndent()
    }.joinToString("\n")
    return IndividualQueueRequest(
        query = "query FindQueueScenes($variableDefinitions) {\n$fields\n}",
        variables = variables,
        aliasesBySceneId = aliasesBySceneId,
    )
}

internal fun queueResponseNeedsIndividualFallback(response: String): Boolean {
    val root = runCatching { JSONObject(response) }.getOrNull() ?: return false
    if (root.optJSONArray("errors")?.length()?.let { it > 0 } == true) {
        return true
    }
    return root.optJSONObject("data")
        ?.optJSONObject("findScenes")
        ?.optJSONArray("scenes") == null
}

internal fun parseIndividualQueueResponse(
    response: String,
    serverUrl: String,
    requestedSceneIds: List<String>,
    requestedStartIndex: Int,
    aliasesBySceneId: Map<String, String>,
): ResolvedPlaybackQueue {
    val data = runCatching { JSONObject(response) }.getOrNull()
        ?.optJSONObject("data")
        ?: throw QueueResolutionException("Stash did not return a compatible queue response.")
    val scenes = JSONArray()
    aliasesBySceneId.forEach { (_, alias) ->
        data.optJSONObject(alias)?.let { scenes.put(it) }
    }
    val syntheticResponse = JSONObject()
        .put(
            "data",
            JSONObject().put(
                "findScenes",
                JSONObject().put("scenes", scenes),
            ),
        )
        .toString()
    return parseQueueResponse(
        response = syntheticResponse,
        serverUrl = serverUrl,
        requestedSceneIds = requestedSceneIds,
        requestedStartIndex = requestedStartIndex,
    )
}

internal fun stashAuthorizationHeaders(apiKey: String?): Map<String, String> =
    apiKey
        ?.takeIf { it.isNotBlank() }
        ?.let { mapOf("ApiKey" to it) }
        .orEmpty()

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
    return parseScene(
        scene = scene,
        serverUrl = serverUrl,
        requestedSceneId = requestedSceneId,
    )
}

internal fun parseQueueResponse(
    response: String,
    serverUrl: String,
    requestedSceneIds: List<String>,
    requestedStartIndex: Int,
): ResolvedPlaybackQueue {
    val root = runCatching { JSONObject(response) }.getOrNull()
        ?: throw QueueResolutionException("Stash returned an unreadable queue response.")
    val values = root.optJSONObject("data")
        ?.optJSONObject("findScenes")
        ?.optJSONArray("scenes")
        ?: throw QueueResolutionException("Stash did not return a compatible queue response.")
    val scenesById = buildMap {
        for (index in 0 until values.length()) {
            val scene = values.optJSONObject(index) ?: continue
            val id = scene.optString("id")
            if (id.isNotBlank()) {
                put(id, scene)
            }
        }
    }
    val resolved = mutableListOf<Pair<Int, ScenePlaybackSource>>()
    val skipped = mutableListOf<SkippedScene>()
    requestedSceneIds.forEachIndexed { index, sceneId ->
        val scene = scenesById[sceneId]
        if (scene == null) {
            skipped += SkippedScene(sceneId, "Scene is missing from this Stash server.")
            return@forEachIndexed
        }
        runCatching { parseScene(scene, serverUrl, sceneId) }
            .onSuccess { resolved += index to it }
            .onFailure {
                skipped += SkippedScene(
                    sceneId,
                    it.message ?: "Scene does not have a playable source.",
                )
            }
    }
    if (resolved.isEmpty()) {
        throw QueueResolutionException(
            "None of the ${requestedSceneIds.size} queued scenes can be played from this Stash server.",
        )
    }
    val resolvedStartIndex = resolved.indexOfFirst { it.first >= requestedStartIndex }
        .takeIf { it >= 0 }
        ?: 0
    return ResolvedPlaybackQueue(
        sources = resolved.map { it.second },
        startIndex = resolvedStartIndex,
        startPositionApplies = resolved.any { it.first == requestedStartIndex },
        skippedScenes = skipped,
    )
}

private fun parseScene(
    scene: JSONObject,
    serverUrl: String,
    requestedSceneId: String,
): ScenePlaybackSource {
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
