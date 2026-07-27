package com.yahigod.homestashtv.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackQueueState(
    val commandId: String,
    val profileId: String,
    val sources: List<ScenePlaybackSource>,
    val currentIndex: Int,
    val positionMs: Long,
    val loop: Boolean,
    val reshuffle: Boolean,
    val wasPlaying: Boolean,
)

interface PlaybackQueueStateStore {
    fun load(): PlaybackQueueState?

    fun save(state: PlaybackQueueState)

    fun clear()
}

class AndroidPlaybackQueueStateStore(context: Context) : PlaybackQueueStateStore {
    private val preferences = context.getSharedPreferences(
        QUEUE_STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun load(): PlaybackQueueState? =
        decodePlaybackQueueState(preferences.getString(QUEUE_STATE_KEY, null))

    override fun save(state: PlaybackQueueState) {
        preferences.edit()
            .putString(QUEUE_STATE_KEY, encodePlaybackQueueState(state))
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(QUEUE_STATE_KEY).apply()
    }
}

internal fun encodePlaybackQueueState(state: PlaybackQueueState): String {
    val sources = JSONArray()
    state.sources.forEach {
        sources.put(
            JSONObject()
                .put("scene_id", it.sceneId)
                .put("title", it.title)
                .put("stream_url", it.streamUrl),
        )
    }
    return JSONObject()
        .put("version", QUEUE_STATE_VERSION)
        .put("command_id", state.commandId)
        .put("profile_id", state.profileId)
        .put("sources", sources)
        .put("current_index", state.currentIndex)
        .put("position_ms", state.positionMs)
        .put("loop", state.loop)
        .put("reshuffle", state.reshuffle)
        .put("was_playing", state.wasPlaying)
        .toString()
}

internal fun decodePlaybackQueueState(value: String?): PlaybackQueueState? {
    if (value.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val root = JSONObject(value)
        require(root.optInt("version") == QUEUE_STATE_VERSION)
        val values = root.getJSONArray("sources")
        val sources = buildList {
            for (index in 0 until values.length()) {
                val source = values.getJSONObject(index)
                add(
                    ScenePlaybackSource(
                        sceneId = source.getString("scene_id"),
                        title = source.getString("title"),
                        streamUrl = source.getString("stream_url"),
                    ),
                )
            }
        }
        val currentIndex = root.getInt("current_index")
        require(sources.isNotEmpty() && currentIndex in sources.indices)
        PlaybackQueueState(
            commandId = root.getString("command_id"),
            profileId = root.getString("profile_id"),
            sources = sources,
            currentIndex = currentIndex,
            positionMs = root.getLong("position_ms").coerceAtLeast(0L),
            loop = root.optBoolean("loop"),
            reshuffle = root.optBoolean("reshuffle"),
            wasPlaying = root.optBoolean("was_playing"),
        )
    }.getOrNull()
}

private const val QUEUE_STATE_VERSION = 1
private const val QUEUE_STATE_PREFERENCES = "playback_queue_state"
private const val QUEUE_STATE_KEY = "active_queue"
