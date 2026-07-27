package com.yahigod.homestashtv.receiver

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface CommandLedger {
    /**
     * Atomically claims a command before execution.
     *
     * Returns false when this command ID was claimed previously.
     */
    fun claim(
        commandId: String,
        expiresAtMs: Long,
        nowMs: Long,
    ): Boolean
}

class AndroidCommandLedger(context: Context) : CommandLedger {
    private val preferences = context.getSharedPreferences(
        COMMAND_LEDGER_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun claim(
        commandId: String,
        expiresAtMs: Long,
        nowMs: Long,
    ): Boolean = synchronized(preferences) {
        val entries = decodeEntries(preferences.getString(COMMAND_LEDGER_KEY, null))
            .filter { it.expiresAtMs > nowMs }
            .toMutableList()
        if (entries.any { it.commandId == commandId }) {
            return@synchronized false
        }
        entries += LedgerEntry(commandId, expiresAtMs)
        val bounded = entries
            .sortedByDescending { it.expiresAtMs }
            .take(MAX_LEDGER_ENTRIES)
        preferences.edit()
            .putString(COMMAND_LEDGER_KEY, encodeEntries(bounded))
            .commit()
        true
    }
}

internal data class LedgerEntry(
    val commandId: String,
    val expiresAtMs: Long,
)

internal fun encodeEntries(entries: List<LedgerEntry>): String {
    val values = JSONArray()
    entries.forEach {
        values.put(
            JSONObject()
                .put("command_id", it.commandId)
                .put("expires_at_ms", it.expiresAtMs),
        )
    }
    return values.toString()
}

internal fun decodeEntries(value: String?): List<LedgerEntry> {
    if (value.isNullOrBlank()) {
        return emptyList()
    }
    return runCatching {
        val values = JSONArray(value)
        buildList {
            for (index in 0 until values.length()) {
                val item = values.getJSONObject(index)
                val id = item.optString("command_id")
                val expiry = item.optLong("expires_at_ms", -1)
                if (id.isNotBlank() && expiry >= 0) {
                    add(LedgerEntry(id, expiry))
                }
            }
        }
    }.getOrDefault(emptyList())
}

private const val COMMAND_LEDGER_PREFERENCES = "receiver_command_ledger"
private const val COMMAND_LEDGER_KEY = "commands"
private const val MAX_LEDGER_ENTRIES = 256
