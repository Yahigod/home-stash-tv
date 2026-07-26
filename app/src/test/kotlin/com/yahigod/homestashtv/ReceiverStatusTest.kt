package com.yahigod.homestashtv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverStatusTest {
    @Test
    fun idleStatusIsSuitableForTheFirstLaunchScreen() {
        assertEquals(
            "Waiting for a Home Stash connection",
            ReceiverStatus.IDLE.message,
        )
    }

    @Test
    fun everyStatusHasAUniqueNonBlankMessage() {
        val messages = ReceiverStatus.entries.map(ReceiverStatus::message)

        assertTrue(messages.all(String::isNotBlank))
        assertEquals(messages.size, messages.distinct().size)
    }
}
