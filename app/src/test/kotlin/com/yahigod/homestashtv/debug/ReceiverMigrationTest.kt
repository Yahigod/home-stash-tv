package com.yahigod.homestashtv.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiverMigrationTest {
    @Test
    fun `migration keeps stable profile IDs and credentials`() {
        val migration = decodeReceiverMigration(
            """
            {
              "v": 1,
              "pairing": {
                "bridge_url": "http://bridge.test/",
                "receiver_id": "receiver-1",
                "receiver_token": "receiver-token",
                "device_name": "Living room"
              },
              "profiles": [
                {
                  "id": "profile-a",
                  "name": "Primary",
                  "server_url": "http://stash-a.test/",
                  "api_key": "key-a"
                },
                {
                  "id": "profile-b",
                  "name": "Secondary",
                  "server_url": "https://stash-b.test",
                  "api_key": ""
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("http://bridge.test", migration.bridgeUrl)
        assertEquals(listOf("profile-a", "profile-b"), migration.profiles.map { it.id })
        assertEquals(listOf("key-a", ""), migration.profiles.map { it.apiKey })
    }

    @Test
    fun `migration rejects duplicate profile IDs`() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeReceiverMigration(
                """
                {
                  "v": 1,
                  "pairing": {
                    "bridge_url": "http://bridge.test",
                    "receiver_id": "receiver-1",
                    "receiver_token": "receiver-token",
                    "device_name": "Living room"
                  },
                  "profiles": [
                    {
                      "id": "same",
                      "name": "One",
                      "server_url": "http://one.test",
                      "api_key": "one"
                    },
                    {
                      "id": "same",
                      "name": "Two",
                      "server_url": "http://two.test",
                      "api_key": "two"
                    }
                  ]
                }
                """.trimIndent(),
            )
        }
    }
}
