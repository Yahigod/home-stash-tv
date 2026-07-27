package com.yahigod.homestashtv.profiles

import java.util.UUID

data class ServerProfile(
    val id: String,
    val name: String,
    val serverUrl: String,
)

internal fun newProfileId(): String = UUID.randomUUID().toString()

interface ServerProfileRepository {
    fun listProfiles(): List<ServerProfile>

    fun getCredential(profileId: String): String?

    fun saveProfile(
        profile: ServerProfile,
        newCredential: String?,
    )

    fun deleteProfile(profileId: String)
}

internal interface ProfileMetadataStore {
    fun load(): List<ServerProfile>

    fun save(profiles: List<ServerProfile>)
}

internal interface CredentialStore {
    fun get(profileId: String): String?

    fun put(
        profileId: String,
        credential: String,
    )

    fun delete(profileId: String)
}

internal class StoredServerProfileRepository(
    private val metadataStore: ProfileMetadataStore,
    private val credentialStore: CredentialStore,
) : ServerProfileRepository {
    override fun listProfiles(): List<ServerProfile> =
        metadataStore.load().sortedBy { it.name.lowercase() }

    override fun getCredential(profileId: String): String? =
        credentialStore.get(profileId)

    override fun saveProfile(
        profile: ServerProfile,
        newCredential: String?,
    ) {
        require(profile.id.isNotBlank()) { "Profile ID is required." }
        require(profile.name.isNotBlank()) { "Profile name is required." }
        require(profile.serverUrl.isNotBlank()) { "Server address is required." }

        val existing = metadataStore.load()

        if (!newCredential.isNullOrBlank()) {
            credentialStore.put(profile.id, newCredential)
        }

        val updated = existing
            .filterNot { it.id == profile.id }
            .plus(profile)
        metadataStore.save(updated)
    }

    override fun deleteProfile(profileId: String) {
        metadataStore.save(metadataStore.load().filterNot { it.id == profileId })
        credentialStore.delete(profileId)
    }
}
