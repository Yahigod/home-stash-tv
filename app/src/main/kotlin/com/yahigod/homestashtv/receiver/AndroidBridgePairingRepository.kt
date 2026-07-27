package com.yahigod.homestashtv.receiver

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidBridgePairingRepository(context: Context) : BridgePairingRepository {
    private val preferences = context.getSharedPreferences(
        BRIDGE_PAIRING_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun getPairing(): BridgePairing? {
        val encoded = preferences.getString(PAIRING_METADATA_KEY, null) ?: return null
        return runCatching {
            val value = JSONObject(encoded)
            BridgePairing(
                bridgeUrl = value.getString("bridge_url"),
                receiverId = value.getString("receiver_id"),
                deviceName = value.getString("device_name"),
            )
        }.getOrNull()
    }

    override fun getReceiverToken(): String? {
        val encoded = preferences.getString(RECEIVER_TOKEN_KEY, null) ?: return null
        val parts = encoded.split('.', limit = 2)
        if (parts.size != 2) {
            return null
        }
        return runCatching {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    Base64.decode(parts[0], Base64.NO_WRAP),
                ),
            )
            cipher.updateAAD(RECEIVER_TOKEN_KEY.toByteArray(Charsets.UTF_8))
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
                .toString(Charsets.UTF_8)
        }.getOrNull()
    }

    override fun savePairing(
        pairing: BridgePairing,
        receiverToken: String,
    ) {
        require(receiverToken.isNotBlank()) { "Receiver token is required." }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(RECEIVER_TOKEN_KEY.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(receiverToken.toByteArray(Charsets.UTF_8))
        val encodedToken = listOf(cipher.iv, encrypted).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
        val metadata = JSONObject()
            .put("bridge_url", pairing.bridgeUrl)
            .put("receiver_id", pairing.receiverId)
            .put("device_name", pairing.deviceName)
            .toString()

        if (!preferences.edit()
                .putString(PAIRING_METADATA_KEY, metadata)
                .putString(RECEIVER_TOKEN_KEY, encodedToken)
                .commit()
        ) {
            throw IllegalStateException("Pairing could not be persisted.")
        }
    }

    override fun clearPairing() {
        preferences.edit()
            .remove(PAIRING_METADATA_KEY)
            .remove(RECEIVER_TOKEN_KEY)
            .commit()
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

private const val BRIDGE_PAIRING_PREFERENCES = "bridge_pairing"
private const val PAIRING_METADATA_KEY = "metadata"
private const val RECEIVER_TOKEN_KEY = "receiver_token"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "home_stash_tv_receiver_token_v1"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
