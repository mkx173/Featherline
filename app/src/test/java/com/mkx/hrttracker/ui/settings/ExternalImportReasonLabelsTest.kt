package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalImportReasonLabelsTest {
    // SOURCE_FALLBACK is the only non-skip key; it must NOT get a concise label
    // because it is never shown in the skipped-rows dialog. Every other key must,
    // so adding a new skip reason without a label fails loud here.
    private val nonSkipKeys = setOf(ExternalImportWarningMessageKey.SOURCE_FALLBACK)

    @Test
    fun everySkipMessageKeyHasALabel() {
        ExternalImportWarningMessageKey.entries
            .filterNot { it in nonSkipKeys }
            .forEach { key ->
                assertNotNull("Missing concise label for $key", skippedReasonLabelRes(key))
            }
    }

    @Test
    fun nonSkipAndNullKeysHaveNoLabel() {
        assertNull(skippedReasonLabelRes(ExternalImportWarningMessageKey.SOURCE_FALLBACK))
        assertNull(skippedReasonLabelRes(null))
    }
}
