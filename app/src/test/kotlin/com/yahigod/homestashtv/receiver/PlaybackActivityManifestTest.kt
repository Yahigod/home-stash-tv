package com.yahigod.homestashtv.receiver

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

class PlaybackActivityManifestTest {
    @Test
    fun `playback alone wakes above ambient presentation`() {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val playback = (0 until activities.length)
            .map { activities.item(it) as Element }
            .singleOrNull {
                it.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                    ".playback.PlaybackActivity"
            }
        val main = (0 until activities.length)
            .map { activities.item(it) as Element }
            .singleOrNull {
                it.getAttributeNS(ANDROID_NAMESPACE, "name") == ".MainActivity"
            }

        assertNotNull(playback)
        assertNotNull(main)
        assertEquals("true", playback!!.getAttributeNS(ANDROID_NAMESPACE, "turnScreenOn"))
        assertEquals("true", playback.getAttributeNS(ANDROID_NAMESPACE, "showWhenLocked"))
        assertEquals("", main!!.getAttributeNS(ANDROID_NAMESPACE, "turnScreenOn"))
        assertEquals("", main.getAttributeNS(ANDROID_NAMESPACE, "showWhenLocked"))
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
