package com.yahigod.homestashtv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StashSceneResolverTest {
    @Test
    fun `stash authentication uses api key header`() {
        assertEquals(
            mapOf("ApiKey" to "test-api-key"),
            stashAuthorizationHeaders("test-api-key"),
        )
    }

    @Test
    fun `anonymous Stash omits authentication header`() {
        assertTrue(stashAuthorizationHeaders(null).isEmpty())
        assertTrue(stashAuthorizationHeaders("").isEmpty())
    }

    @Test
    fun `scene response resolves title and removes embedded api key`() {
        val response = """
            {
              "data": {
                "findScene": {
                  "id": "42",
                  "title": "Playback proof",
                  "paths": {
                    "stream": "http://stash.test/scene/42/stream?apikey=secret&download=false"
                  }
                }
              }
            }
        """.trimIndent()

        val source = parseSceneResponse(response, "http://stash.test", "42")

        assertEquals("42", source.sceneId)
        assertEquals("Playback proof", source.title)
        assertEquals(
            "http://stash.test/scene/42/stream?download=false",
            source.streamUrl,
        )
        assertFalse(source.streamUrl.contains("secret"))
    }

    @Test
    fun `relative stream URL resolves against the configured server`() {
        val response = """
            {
              "data": {
                "findScene": {
                  "id": "9",
                  "title": "",
                  "paths": {"stream": "/scene/9/stream"}
                }
              }
            }
        """.trimIndent()

        val source = parseSceneResponse(response, "http://stash.test", "9")

        assertEquals("Scene 9", source.title)
        assertEquals("http://stash.test/scene/9/stream", source.streamUrl)
    }

    @Test
    fun `missing scene produces an actionable error`() {
        val error = assertThrows(SceneResolutionException::class.java) {
            parseSceneResponse(
                """{"data":{"findScene":null}}""",
                "http://stash.test",
                "404",
            )
        }

        assertEquals(
            "That scene does not exist in this Stash server.",
            error.message,
        )
    }

    @Test
    fun `queue response restores requested order and maps the starting scene`() {
        val response = """
            {
              "data": {
                "findScenes": {
                  "scenes": [
                    {"id":"43","title":"Second","paths":{"stream":"/scene/43/stream"}},
                    {"id":"42","title":"First","paths":{"stream":"/scene/42/stream"}}
                  ]
                }
              }
            }
        """.trimIndent()

        val queue = parseQueueResponse(
            response = response,
            serverUrl = "http://stash.test",
            requestedSceneIds = listOf("42", "missing", "43"),
            requestedStartIndex = 1,
        )

        assertEquals(listOf("42", "43"), queue.sources.map { it.sceneId })
        assertEquals(1, queue.startIndex)
        assertEquals(listOf("missing"), queue.skippedScenes.map { it.sceneId })
    }
}
