package com.mkx.hrttracker.ui.journal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.model.journal.AnchorIcon
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnchorIconResTest {
    @Test
    fun everyAnchorIcon_resolvesToADrawable() {
        AnchorIcon.entries.forEach { icon ->
            assertNotEquals("No drawable for $icon", 0, anchorIconRes(icon))
        }
    }
}
