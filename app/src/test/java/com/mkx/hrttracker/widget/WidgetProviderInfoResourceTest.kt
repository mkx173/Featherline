package com.mkx.hrttracker.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetProviderInfoResourceTest {

    @Test
    fun largeWidgetProviderVariants_keepSharedSizingInSync() {
        val base = providerInfo("xml/hrt_widget_large_info.xml")
        val api31 = providerInfo("xml-v31/hrt_widget_large_info.xml")

        SHARED_SIZE_ATTRIBUTES.forEach { attribute ->
            assertEquals(
                "$attribute must stay in sync across the base and API 31+ provider info",
                attributeValue(base, attribute),
                attributeValue(api31, attribute),
            )
        }
    }

    @Test
    fun largeWidgetProvider_supportsThreeByTwoMinimum() {
        val expected = mapOf(
            "minWidth" to "140dp",
            "minHeight" to "80dp",
            "minResizeWidth" to "140dp",
            "minResizeHeight" to "80dp",
            "targetCellWidth" to "3",
            "targetCellHeight" to "2",
            "resizeMode" to "horizontal|vertical",
        )

        listOf(
            providerInfo("xml/hrt_widget_large_info.xml"),
            providerInfo("xml-v31/hrt_widget_large_info.xml"),
        ).forEach { providerInfo ->
            expected.forEach { (attribute, value) ->
                assertEquals(value, attributeValue(providerInfo, attribute))
            }
        }
    }

    private fun providerInfo(relativePath: String): String {
        val resourceFile = sequenceOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        ).first(File::exists)
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
