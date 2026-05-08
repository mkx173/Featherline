package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSecuritySectionLayoutTest {
    @Test
    fun resolveSettingsSecuritySectionLayout_appLockDisabled_keepsHideScreenContentAsSecondItem() {
        assertEquals(
            SettingsSecuritySectionLayout(
                itemCount = 2,
                hideScreenContentIndex = 1,
            ),
            resolveSettingsSecuritySectionLayout(
                screenLockProtectionEnabled = false,
            ),
        )
    }

    @Test
    fun resolveSettingsSecuritySectionLayout_appLockEnabled_placesHideScreenContentAfterGracePeriod() {
        assertEquals(
            SettingsSecuritySectionLayout(
                itemCount = 3,
                hideScreenContentIndex = 2,
            ),
            resolveSettingsSecuritySectionLayout(
                screenLockProtectionEnabled = true,
            ),
        )
    }
}
