package com.yahigod.homestashtv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
}
