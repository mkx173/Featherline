package com.mkx.hrttracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

// API 33-35 route Back through OnBackInvokedCallback, and so play the predictive-back
// animations, only for an app whose manifest opts in. API 36+ makes it the default for
// targetSdk 36+. Robolectric (sdk 33) doesn't carry the attribute into ApplicationInfo, so
// this reads the manifest itself; a real XML parser skips comments.
class PredictiveBackOptInTest {
    @Test
    fun applicationOptsIntoOnBackInvokedCallback() {
        // Gradle runs unit tests from the module directory; the repo-root prefix is the
        // fallback for runners that don't.
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull(File::exists)
            ?: error("Manifest not found at any of ${candidates.map(File::getAbsolutePath)}")
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals(
            "<application> must set android:enableOnBackInvokedCallback",
            "true",
            application.getAttributeNS(ANDROID_NS, "enableOnBackInvokedCallback"),
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
