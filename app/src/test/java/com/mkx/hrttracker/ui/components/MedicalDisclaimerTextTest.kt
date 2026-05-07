package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MedicalDisclaimerTextTest {
    @Test
    fun disclaimer_kinds_use_expected_strings() {
        assertEquals(
            R.string.medical_disclaimer_home_estimates_and_ranges,
            MedicalDisclaimerKind.HOME_ESTIMATES_AND_RANGES.textRes
        )
        assertEquals(
            R.string.medical_disclaimer_preset_doses,
            MedicalDisclaimerKind.PRESET_DOSES.textRes
        )
        assertEquals(
            R.string.medical_disclaimer_reference_ranges,
            MedicalDisclaimerKind.REFERENCE_RANGES.textRes
        )
        assertEquals(
            R.string.medical_disclaimer_wpath_reference_ranges,
            MedicalDisclaimerKind.WPATH_REFERENCE_RANGES.textRes
        )
        assertEquals(
            R.string.medical_disclaimer_plasma_concentration_estimates,
            MedicalDisclaimerKind.PLASMA_CONCENTRATION_ESTIMATES.textRes
        )
    }

    @Test
    fun common_disclaimer_sets_match_screen_risks() {
        assertEquals(
            listOf(
                MedicalDisclaimerKind.HOME_ESTIMATES_AND_RANGES,
            ),
            MedicalDisclaimerSets.home
        )
        assertEquals(
            listOf(MedicalDisclaimerKind.PRESET_DOSES),
            MedicalDisclaimerSets.medicationEditor
        )
        assertEquals(
            listOf(MedicalDisclaimerKind.REFERENCE_RANGES),
            MedicalDisclaimerSets.calibration
        )
        assertEquals(
            listOf(MedicalDisclaimerKind.WPATH_REFERENCE_RANGES),
            MedicalDisclaimerSets.calibrationEditor
        )
    }

    @Test
    fun english_reference_range_disclaimer_uses_concise_copy() {
        assertEquals(
            "Reference ranges are general guidelines, not medical advice—your target ranges may differ based on your treatment plan.",
            readStringResource(
                relativePath = "src/main/res/values/strings.xml",
                name = "medical_disclaimer_reference_ranges",
            )
        )
    }

    @Test
    fun english_wpath_reference_range_disclaimer_uses_soc8_copy() {
        assertEquals(
            "Reference ranges are based on WPATH Standards of Care 8. They are general guidelines, not medical advice—your target ranges may differ based on your treatment plan.",
            readStringResource(
                relativePath = "src/main/res/values/strings.xml",
                name = "medical_disclaimer_wpath_reference_ranges",
            )
        )
    }

    private fun readStringResource(relativePath: String, name: String): String {
        val resourceFile = sequenceOf(
            File(relativePath),
            File("app/$relativePath"),
        ).first(File::exists)
        val pattern = Regex("""<string name="$name">(.+)</string>""")
        return checkNotNull(
            resourceFile.readLines().firstNotNullOfOrNull { line ->
                pattern.find(line)?.groupValues?.get(1)
            }
        )
    }
}
