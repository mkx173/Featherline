package com.mkx.hrttracker.util

import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationDisplayTextTest {

    private val context: Context = mockk(relaxed = true)

    private fun multiUseVial() = testMedicine(
        key = MedicationKey.ESTRADIOL_VALERATE,
        preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 10.0,
        ),
    )

    private fun singleUseVial() = testMedicine(
        key = MedicationKey.ESTRADIOL_VALERATE,
        preparation = MedicinePreparation.InjectionSingleUseVial(
            strengthMgPerVial = 5.0,
        ),
    )

    private fun pill() = testMedicine(
        key = MedicationKey.ESTRADIOL,
        preparation = MedicinePreparation.Pill(
            strengthMgPerTablet = 10.0,
        ),
    )

    @Test
    fun doseInstructionText_withDelta_showsActualDrawnVolume() {
        every {
            context.getString(R.string.dose_instruction_summary_concentration_mg_per_ml, "20")
        } returns "20 mg/mL"
        every {
            context.getString(R.string.dose_instruction_summary_volume_ml, "0.6")
        } returns "0.6 mL"

        val text = doseInstructionText(
            context = context,
            medicine = multiUseVial(),
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            doseAmountDelta = 0.1,
        )

        // Actual drawn volume (0.5 + 0.1). The active mass is not surfaced for
        // concentration-bearing forms.
        assertEquals("20 mg/mL · 0.6 mL", text)
    }

    @Test
    fun doseInstructionText_withoutDelta_keepsCompactConcentrationAndPortion() {
        every {
            context.getString(R.string.dose_instruction_summary_concentration_mg_per_ml, "20")
        } returns "20 mg/mL"
        every {
            context.getString(R.string.dose_instruction_summary_volume_ml, "0.5")
        } returns "0.5 mL"

        val text = doseInstructionText(
            context = context,
            medicine = multiUseVial(),
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            doseAmountDelta = null,
        )

        // No adjustment: the active-mass line stays suppressed for the
        // concentration-bearing form, matching pre-existing behavior.
        assertEquals("20 mg/mL · 0.5 mL", text)
    }

    @Test
    fun doseInstructionText_ampuleWithDelta_showsActualActiveMass() {
        // Single-arg getString resolves the unit short label (mg).
        every { context.getString(any<Int>()) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, "5.2", "mg")
        } returns "5.2 mg"

        val text = doseInstructionText(
            context = context,
            medicine = singleUseVial(),
            doseInstruction = DoseInstruction.WholeUnit,
            doseAmountDelta = 0.2,
        )

        // Ampules carry no portion; the mg line is the only display and reflects
        // the actual administered mass (5.0 + 0.2).
        assertEquals("5.2 mg", text)
    }

    @Test
    fun doseInstructionText_zeroCount_usesLegacySingleCountSummary() {
        // Single-arg getString resolves the unit short label (mg).
        every { context.getString(any<Int>()) } returns "mg"
        every {
            context.getString(R.string.dose_instruction_summary_tablet_fraction, "1/2")
        } returns "1/2 tablets"
        every {
            context.getString(R.string.dose_instruction_summary_active_amount, "5", "mg")
        } returns "5 mg"

        val text = doseInstructionText(
            context = context,
            medicine = pill(),
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            count = 0,
        )

        assertEquals("1/2 tablets · 5 mg", text)
    }
}
