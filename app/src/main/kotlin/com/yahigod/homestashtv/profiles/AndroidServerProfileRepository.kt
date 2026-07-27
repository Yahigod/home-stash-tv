package com.yahigod.homestashtv.profiles

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidServerProfileRepository(context: Context) : ServerProfileRepository by
    StoredServerProfileRepository(
        metadataStore = SharedPreferencesProfileMetadataStore(
            context.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE),
        ),
        credentialStore = KeystoreCredentialStore(
            context.getSharedPreferences(CREDENTIAL_PREFERENCES, Context.MODE_PRIVATE),
        ),
    )

private class SharedPreferencesProfileMetadataStore(
    private val preferences: SharedPreferences,
) : ProfileMetadataStore {
    override fun load(): List<ServerProfile> =
        decodeProfileMetadata(preferences.getString(PROFILES_KEY, null))

    override fun save(profiles: List<ServerProfile>) {
        preferences.edit()
            .putString(PROFILES_KEY, encodeProfileMetadata(profiles))
            .apply()
    }
}

private class KeystoreCredentialStore(
    private val preferences: SharedPreferences,
) : CredentialStore {
    override fun get(profileId: String): String? {
        val encoded = preferences.getString(profileId, null) ?: return null
        val parts = encoded.split('.', limit = 2)
        if (parts.size != 2) {
            return null
        }

        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override fun put(
        profileId: String,
        credential: String,
    ) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(credential.toByteArray(Charsets.UTF_8))
        val encoded = listOf(cipher.iv, ciphertext).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
        preferences.edit().putString(profileId, encoded).apply()
    }

    override fun delete(profileId: String) {
        preferences.edit().remove(profileId).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

internal fun encodeProfileMetadata(profiles: List<ServerProfile>): String {
    val items = JSONArray()
    profiles.forEach { profile ->
        items.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("serverUrl", profile.serverUrl),
        )
    }
    return items.toString()
}

internal fun decodeProfileMetadata(value: String?): List<ServerProfile> {
    if (value.isNullOrBlank()) {
        return emptyList()
    }

    return runCatching {
        val items = JSONArray(value)
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val id = item.optString("id")
                val name = item.optString("name")
                val serverUrl = item.optString("serverUrl")
                if (id.isNotBlank() && name.isNotBlank() && serverUrl.isNotBlank()) {
                    add(ServerProfile(id, name, serverUrl))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private const val PROFILE_PREFERENCES = "stash_server_profiles"
private const val CREDENTIAL_PREFERENCES = "stash_server_credentials"
private const val PROFILES_KEY = "profiles"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "home_stash_tv_server_credentials_v1"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
