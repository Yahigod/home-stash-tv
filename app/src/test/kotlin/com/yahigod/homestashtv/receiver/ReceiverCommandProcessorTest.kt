package com.yahigod.homestashtv.receiver

import com.yahigod.homestashtv.profiles.ServerProfile
import com.yahigod.homestashtv.profiles.ServerProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverCommandProcessorTest {
    private val profiles = FakeProfiles(
        listOf(ServerProfile("profile-1", "Normal Stash", "http://stash.test")),
    )

    @Test
    fun `valid command is claimed before exactly one execution`() {
        val executed = mutableListOf<PlayQueueCommand>()
        val processor = ReceiverCommandProcessor(
            receiverId = "receiver-1",
            profileRepository = profiles,
            commandLedger = FakeCommandLedger(),
            commandExecutor = { executed += it },
            clock = { 2000 },
        )

        assertEquals(
            AcknowledgementStatus.ACCEPTED,
            processor.process(commandJson())?.status,
        )
        assertEquals(
            AcknowledgementStatus.DUPLICATE,
            processor.process(commandJson())?.status,
        )
        assertEquals(1, executed.size)
    }

    @Test
    fun `expired mismatched and missing-profile commands never execute`() {
        val executed = mutableListOf<PlayQueueCommand>()
        val processor = ReceiverCommandProcessor(
            receiverId = "receiver-1",
            profileRepository = profiles,
            commandLedger = FakeCommandLedger(),
            commandExecutor = { executed += it },
            clock = { 6000 },
        )

        assertEquals(
            AcknowledgementStatus.EXPIRED,
            processor.process(commandJson())?.status,
        )
        assertEquals(
            "receiver_mismatch",
            processor.process(
                commandJson(receiverId = "other", expiresAtMs = 9000),
            )?.errorCode,
        )
        assertEquals(
            "profile_missing",
            processor.process(
                commandJson(profileId = "missing", expiresAtMs = 9000),
            )?.errorCode,
        )
        assertTrue(executed.isEmpty())
    }
}

private class FakeProfiles(
    private val profiles: List<ServerProfile>,
) : ServerProfileRepository {
    override fun listProfiles(): List<ServerProfile> = profiles

    override fun getCredential(profileId: String): String? = null

    override fun saveProfile(
        profile: ServerProfile,
        newCredential: String?,
    ) = error("Not used")

    override fun deleteProfile(profileId: String) = error("Not used")
}
