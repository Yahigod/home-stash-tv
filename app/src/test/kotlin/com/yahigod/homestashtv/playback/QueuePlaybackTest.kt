package com.yahigod.homestashtv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueuePlaybackTest {
    private val sources = listOf(
        source("A"),
        source("B"),
        source("C"),
    )

    @Test
    fun `continuing queue preserves first-cycle order and start index`() {
        val (queue, startIndex) = effectiveQueue(
            sources = sources,
            startIndex = 1,
            continuePlayback = true,
        )

        assertEquals(listOf("A", "B", "C"), queue.map { it.sceneId })
        assertEquals(1, startIndex)
    }

    @Test
    fun `non-continuing playback isolates the selected scene`() {
        val (queue, startIndex) = effectiveQueue(
            sources = sources,
            startIndex = 1,
            continuePlayback = false,
        )

        assertEquals(listOf("B"), queue.map { it.sceneId })
        assertEquals(0, startIndex)
    }

    @Test
    fun `fixed loop repeats the original order`() {
        assertEquals(
            listOf("A", "B", "C"),
            buildNextQueueCycle(
                currentCycle = sources,
                previousFinalSceneId = "C",
                reshuffle = false,
            ).map { it.sceneId },
        )
    }

    @Test
    fun `two-scene reshuffle avoids a boundary repeat`() {
        val twoSources = sources.take(2)

        assertEquals(
            listOf("A", "B"),
            buildNextQueueCycle(
                currentCycle = twoSources,
                previousFinalSceneId = "B",
                reshuffle = true,
                random = { 0.0 },
            ).map { it.sceneId },
        )
    }

    @Test
    fun `reshuffle rejects repeated order and boundary then accepts fresh cycle`() {
        val random = sequenceRandom(
            0.99,
            0.99, // A-B-C: same order
            0.0,
            0.99, // C-B-A: repeats C at boundary
            0.0,
            0.0, // B-C-A: valid
        )

        assertEquals(
            listOf("B", "C", "A"),
            buildNextQueueCycle(
                currentCycle = sources,
                previousFinalSceneId = "C",
                reshuffle = true,
                random = random,
            ).map { it.sceneId },
        )
    }

    @Test
    fun `reshuffle has deterministic valid fallback`() {
        assertEquals(
            listOf("B", "C", "A"),
            buildNextQueueCycle(
                currentCycle = sources,
                previousFinalSceneId = "C",
                reshuffle = true,
                random = { 0.99 },
            ).map { it.sceneId },
        )
    }

    private fun source(id: String) =
        ScenePlaybackSource(id, "Scene $id", "http://stash.test/scene/$id")

    private fun sequenceRandom(vararg values: Double): () -> Double {
        var index = 0
        return {
            check(index < values.size) { "Random sequence was exhausted." }
            values[index++]
        }
    }
}
