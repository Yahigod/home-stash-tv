package com.yahigod.homestashtv.playback

data class SkippedScene(
    val sceneId: String,
    val reason: String,
)

data class ResolvedPlaybackQueue(
    val sources: List<ScenePlaybackSource>,
    val startIndex: Int,
    val startPositionApplies: Boolean,
    val skippedScenes: List<SkippedScene>,
)

class QueueResolutionException(message: String) : Exception(message)

internal fun effectiveQueue(
    sources: List<ScenePlaybackSource>,
    startIndex: Int,
    continuePlayback: Boolean,
): Pair<List<ScenePlaybackSource>, Int> =
    if (continuePlayback) {
        sources to startIndex
    } else {
        listOf(sources[startIndex]) to 0
    }

internal fun buildNextQueueCycle(
    currentCycle: List<ScenePlaybackSource>,
    previousFinalSceneId: String,
    reshuffle: Boolean,
    random: () -> Double = Math::random,
): List<ScenePlaybackSource> {
    if (!reshuffle || currentCycle.size <= 1) {
        return currentCycle.toList()
    }
    if (currentCycle.size == 2) {
        // Reversing a two-item cycle repeats the final item at the boundary.
        return currentCycle.toList()
    }

    repeat(maxOf(12, currentCycle.size * 2)) {
        val candidate = fisherYates(currentCycle, random)
        val repeatsBoundary = candidate.first().sceneId == previousFinalSceneId
        val repeatsOrder = candidate.map { it.sceneId } == currentCycle.map { it.sceneId }
        if (!repeatsBoundary && !repeatsOrder) {
            return candidate
        }
    }

    // A deterministic fallback keeps a completed cycle moving even if the
    // random source repeatedly produces invalid candidates.
    return currentCycle.drop(1) + currentCycle.first()
}

private fun fisherYates(
    values: List<ScenePlaybackSource>,
    random: () -> Double,
): List<ScenePlaybackSource> {
    val shuffled = values.toMutableList()
    for (index in shuffled.lastIndex downTo 1) {
        val boundedRandom = random().coerceIn(0.0, Math.nextDown(1.0))
        val randomIndex = (boundedRandom * (index + 1)).toInt()
        val value = shuffled[index]
        shuffled[index] = shuffled[randomIndex]
        shuffled[randomIndex] = value
    }
    return shuffled
}
