package com.yahigod.homestashtv.receiver

data class BridgePairing(
    val bridgeUrl: String,
    val receiverId: String,
    val deviceName: String,
)

interface BridgePairingRepository {
    fun getPairing(): BridgePairing?

    fun getReceiverToken(): String?

    fun savePairing(
        pairing: BridgePairing,
        receiverToken: String,
    )

    fun clearPairing()
}

data class PendingPairing(
    val pairingId: String,
    val code: String,
    val expiresAtMs: Long,
)

sealed interface PairingPollResult {
    data object Pending : PairingPollResult

    data class Approved(
        val receiverId: String,
        val receiverToken: String,
    ) : PairingPollResult

    data class Failed(val message: String) : PairingPollResult
}
