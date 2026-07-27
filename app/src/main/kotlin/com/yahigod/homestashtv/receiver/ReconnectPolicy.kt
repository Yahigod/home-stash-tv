package com.yahigod.homestashtv.receiver

internal class ReconnectPolicy(
    private val initialDelayMs: Long = 1_000,
    private val maximumDelayMs: Long = 60_000,
) {
    private var attempt = 0

    fun nextDelayMs(): Long {
        val shift = attempt.coerceAtMost(30)
        val delay = (initialDelayMs * (1L shl shift)).coerceAtMost(maximumDelayMs)
        attempt += 1
        return delay
    }

    fun reset() {
        attempt = 0
    }
}
