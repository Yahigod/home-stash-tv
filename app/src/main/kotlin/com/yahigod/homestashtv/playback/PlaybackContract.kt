package com.yahigod.homestashtv.playback

object PlaybackContract {
    const val EXTRA_COMMAND_ID = "command_id"
    const val EXTRA_PROFILE_ID = "profile_id"
    const val EXTRA_SCENE_IDS = "scene_ids"
    const val EXTRA_START_INDEX = "start_index"
    const val EXTRA_START_POSITION_MS = "start_position_ms"
    const val EXTRA_CONTINUE = "continue"
    const val EXTRA_LOOP = "loop"
    const val EXTRA_RESHUFFLE = "reshuffle"

    // Temporary debug-launcher compatibility. These values are supplied only
    // at runtime and are never persisted by the activity.
    const val EXTRA_SERVER_URL = "server_url"
    const val EXTRA_API_KEY = "api_key"
    const val EXTRA_SCENE_ID = "scene_id"
}
