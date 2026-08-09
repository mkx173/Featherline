package com.mkx.hrttracker.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProviderInfoResourceTest {

    // Resource qualifiers fully override rather than merge, so a size attribute added to
    // one variant alone silently does nothing on the other's API level — which is where
    // most users are for -v31. Both providers ship a pair, so both pairs are checked.
    @Test
    fun providerVariants_keepSharedSizingInSync() {
        listOf("hrt_widget_large_info.xml", "hrt_widget_medium_info.xml").forEach { fileName ->
            val base = providerInfo("xml/$fileName")
            val api31 = providerInfo("xml-v31/$fileName")

            SHARED_SIZE_ATTRIBUTES.forEach { attribute ->
                assertEquals(
                    "$attribute must stay in sync across the base and API 31+ $fileName",
                    attributeValue(base, attribute),
                    attributeValue(api31, attribute),
                )
            }
        }
    }

    // The large widget is the only one that shrinks: a 4x2 default (minWidth/minHeight
    // below 12, targetCell* on 12+) down to a 3x2 floor.
    @Test
    fun largeWidgetProvider_keepsFourByTwoDefaultAndThreeByTwoMinimum() {
        assertProviderSizing(
            fileName = "hrt_widget_large_info.xml",
            expected = mapOf(
                "minWidth" to "250dp",
                "minHeight" to "110dp",
                "minResizeWidth" to "210dp",
                "minResizeHeight" to "110dp",
                "targetCellWidth" to "4",
                "targetCellHeight" to "2",
                "resizeMode" to "horizontal|vertical",
            ),
        )
    }

    // The medium widget has no compact layout, so its floor is its 2x2 default.
    @Test
    fun mediumWidgetProvider_pinsItsFloorToTheTwoByTwoDefault() {
        assertProviderSizing(
            fileName = "hrt_widget_medium_info.xml",
            expected = mapOf(
                "minWidth" to "110dp",
                "minHeight" to "110dp",
                "minResizeWidth" to "110dp",
                "minResizeHeight" to "110dp",
                "targetCellWidth" to "2",
                "targetCellHeight" to "2",
                "resizeMode" to "horizontal|vertical",
            ),
        )
    }

    private fun assertProviderSizing(fileName: String, expected: Map<String, String>) {
        listOf("xml/$fileName", "xml-v31/$fileName").forEach { relativePath ->
            val providerInfo = providerInfo(relativePath)
            expected.forEach { (attribute, value) ->
                assertEquals(relativePath, value, attributeValue(providerInfo, attribute))
            }
        }
    }

    private fun providerInfo(relativePath: String): String {
        // Gradle runs unit tests from the module directory; the repo-root prefix is the
        // fallback for runners that don't.
        val candidates = listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        )
        val resourceFile = candidates.firstOrNull(File::exists)
            ?: error("Provider info not found at any of ${candidates.map(File::getAbsolutePath)}")
        return resourceFile.readText()
    }

    private fun attributeValue(providerInfo: String, attribute: String): String {
        val match = Regex("""android:$attribute="([^"]+)"""").find(providerInfo)
        return checkNotNull(match?.groupValues?.get(1)) {
            "Missing android:$attribute in provider info"
        }
    }

    private companion object {
        val SHARED_SIZE_ATTRIBUTES = listOf(
            "minWidth",
            "minHeight",
            "minResizeWidth",
            "minResizeHeight",
            "targetCellWidth",
            "targetCellHeight",
            "resizeMode",
        )
    }
}
