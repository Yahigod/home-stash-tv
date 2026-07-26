package com.yahigod.homestashtv.playback

/**
 * Intent contract used by the temporary debug launcher.
 *
 * The values are supplied at runtime and are never persisted by the app.
 */
object PlaybackContract {
    const val EXTRA_SERVER_URL = "server_url"
    const val EXTRA_API_KEY = "api_key"
    const val EXTRA_SCENE_ID = "scene_id"
}
