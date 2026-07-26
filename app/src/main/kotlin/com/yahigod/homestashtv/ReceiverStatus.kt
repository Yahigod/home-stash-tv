package com.yahigod.homestashtv

internal enum class ReceiverStatus(
    val message: String,
) {
    IDLE("Waiting for a Home Stash connection"),
    CONNECTED("Connected and ready to receive"),
    PLAYING("Playing from Home Stash"),
    FAILED("Receiver needs attention"),
}
