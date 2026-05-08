package com.mkx.hrttracker.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingTopChromeTest {
    @Test
    fun resolveOnboardingProgressAlpha_hides_progress_without_removing_its_layout_space() {
        assertEquals(0f, resolveOnboardingProgressAlpha(showProgress = false), 0f)
    }

    @Test
    fun resolveOnboardingProgressAlpha_shows_progress_after_the_first_step() {
        assertEquals(1f, resolveOnboardingProgressAlpha(showProgress = true), 0f)
    }
}
