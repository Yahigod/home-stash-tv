package com.yahigod.homestashtv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueStateTest {
    @Test
    fun `queue state codec preserves crash recovery data without an api key`() {
        val state = PlaybackQueueState(
            commandId = "command-1",
            profileId = "profile-1",
            sources = listOf(
                ScenePlaybackSource(
                    sceneId = "42",
                    title = "First",
                    streamUrl = "http://stash.test/scene/42/stream",
                ),
                ScenePlaybackSource(
                    sceneId = "43",
                    title = "Second",
                    streamUrl = "http://stash.test/scene/43/stream",
                ),
            ),
            currentIndex = 1,
            positionMs = 12_345,
            loop = true,
            reshuffle = true,
            wasPlaying = true,
        )

        val encoded = encodePlaybackQueueState(state)

        assertEquals(state, decodePlaybackQueueState(encoded))
        assert(!encoded.contains("apikey", ignoreCase = true))
    }

    @Test
    fun `invalid persisted queue is discarded`() {
        assertNull(decodePlaybackQueueState("""{"version":1,"sources":[]}"""))
        assertNull(decodePlaybackQueueState("not-json"))
    }
}
