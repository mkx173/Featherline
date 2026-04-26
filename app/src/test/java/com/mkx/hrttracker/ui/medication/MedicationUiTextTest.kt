package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationUiTextTest {
    @Test
    fun applicationTypeBadgeLabelRes_returns_localized_badge_resource() {
        assertEquals(R.string.medication_application_badge_oral, applicationTypeBadgeLabelRes(MedicationApplicationType.ORAL))
        assertEquals(R.string.medication_application_badge_sublingual, applicationTypeBadgeLabelRes(MedicationApplicationType.SUBLINGUAL))
        assertEquals(R.string.medication_application_badge_injection, applicationTypeBadgeLabelRes(MedicationApplicationType.INJECTION))
        assertEquals(R.string.medication_application_badge_gel, applicationTypeBadgeLabelRes(MedicationApplicationType.GEL))
        assertEquals(R.string.medication_application_badge_patch_on, applicationTypeBadgeLabelRes(MedicationApplicationType.PATCH_ON))
        assertEquals(R.string.medication_application_badge_patch_off, applicationTypeBadgeLabelRes(MedicationApplicationType.PATCH_OFF))
    }

    @Test
    fun medicationCountIndicatorText_formats_count_with_times_suffix() {
        assertEquals("1x", medicationCountIndicatorText(1))
        assertEquals("3x", medicationCountIndicatorText(3))
    }
}
