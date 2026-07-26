package com.yahigod.homestashtv.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileRepositoryTest {
    @Test
    fun `new profile requires and stores a credential separately`() {
        val metadata = FakeMetadataStore()
        val credentials = FakeCredentialStore()
        val repository = StoredServerProfileRepository(metadata, credentials)
        val profile = ServerProfile("normal-id", "Normal Stash", "http://stash.test")

        repository.saveProfile(profile, "normal-secret")

        assertEquals(listOf(profile), repository.listProfiles())
        assertEquals("normal-secret", repository.getCredential(profile.id))
        assertFalse(encodeProfileMetadata(repository.listProfiles()).contains("normal-secret"))
    }

    @Test
    fun `editing metadata without a new key preserves the credential and stable id`() {
        val metadata = FakeMetadataStore()
        val credentials = FakeCredentialStore()
        val repository = StoredServerProfileRepository(metadata, credentials)
        val original = ServerProfile("stable-id", "JAV Stash", "http://jav.test")
        repository.saveProfile(original, "existing-secret")

        val edited = original.copy(serverUrl = "http://jav-updated.test")
        repository.saveProfile(edited, null)

        assertEquals(listOf(edited), repository.listProfiles())
        assertEquals("stable-id", repository.listProfiles().single().id)
        assertEquals("existing-secret", repository.getCredential("stable-id"))
    }

    @Test
    fun `deleting one profile removes only its credential`() {
        val metadata = FakeMetadataStore()
        val credentials = FakeCredentialStore()
        val repository = StoredServerProfileRepository(metadata, credentials)
        val normal = ServerProfile("normal-id", "Normal Stash", "http://normal.test")
        val jav = ServerProfile("jav-id", "JAV Stash", "http://jav.test")
        repository.saveProfile(normal, "normal-secret")
        repository.saveProfile(jav, "jav-secret")

        repository.deleteProfile(normal.id)

        assertEquals(listOf(jav), repository.listProfiles())
        assertNull(repository.getCredential(normal.id))
        assertEquals("jav-secret", repository.getCredential(jav.id))
    }

    @Test
    fun `new profile cannot be saved without a credential`() {
        val repository = StoredServerProfileRepository(
            FakeMetadataStore(),
            FakeCredentialStore(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            repository.saveProfile(
                ServerProfile("id", "Stash", "http://stash.test"),
                null,
            )
        }

        assertTrue(error.message.orEmpty().contains("API key"))
        assertTrue(repository.listProfiles().isEmpty())
    }

    @Test
    fun `metadata codec round trips non-secret profile state`() {
        val profiles = listOf(
            ServerProfile("one", "Normal Stash", "http://normal.test"),
            ServerProfile("two", "JAV Stash", "https://jav.test"),
        )

        assertEquals(profiles, decodeProfileMetadata(encodeProfileMetadata(profiles)))
    }
}

private class FakeMetadataStore : ProfileMetadataStore {
    private var profiles = emptyList<ServerProfile>()

    override fun load(): List<ServerProfile> = profiles

    override fun save(profiles: List<ServerProfile>) {
        this.profiles = profiles
    }
}

private class FakeCredentialStore : CredentialStore {
    private val credentials = mutableMapOf<String, String>()

    override fun get(profileId: String): String? = credentials[profileId]

    override fun put(
        profileId: String,
        credential: String,
    ) {
        credentials[profileId] = credential
    }

    override fun delete(profileId: String) {
        credentials.remove(profileId)
    }
}
