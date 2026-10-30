package com.darshan.splitsmart.data.upi

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Found while preparing the emulator run: the manifest had no `<queries>`, so on Android 11+ `queryIntentActivities`
 * for `upi://pay` returns nothing on a real phone and every "Pay via UPI" tap says "no UPI app". This guards the
 * declaration (Robolectric does not enforce package visibility, so the launcher tests cannot catch it).
 */
class UpiPackageVisibilityTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun `manifest declares visibility of upi pay handlers with scheme and host`() {
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val intents = doc.getElementsByTagName("queries").let { q -> (0 until q.length).flatMap { i -> (q.item(i) as Element).getElementsByTagName("intent").let { n -> (0 until n.length).map { n.item(it) as Element } } } }
        val upi = intents.filter { intent ->
            val actions = intent.getElementsByTagName("action").let { a -> (0 until a.length).map { (a.item(it) as Element).getAttributeNS(android, "name") } }
            val data = intent.getElementsByTagName("data").let { d -> (0 until d.length).map { d.item(it) as Element } }
            "android.intent.action.VIEW" in actions && data.any { it.getAttributeNS(android, "scheme") == "upi" && it.getAttributeNS(android, "host") == "pay" }
        }
        assertThat(upi).isNotEmpty()
    }
}
