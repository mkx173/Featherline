package com.mkx.hrttracker.util

import android.content.Context
import android.content.res.Configuration
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationDisplayTextTest {

    private val context: Context = mockk(relaxed = true)
    private val originalDefaultLocale: Locale = Locale.getDefault()
    private val realContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @After
    fun resetLocale() {
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        Locale.setDefault(originalDefaultLocale)
    }

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

    private fun capsule5mg() = testCustomMedicine(
        medicationName = "Capsule",
        preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 5.0),
    )

    private fun patchTotalMg() = testCustomMedicine(
        medicationName = "Patch",
        preparation = MedicinePreparation.Patch(
            MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.44),
        ),
    )

    private fun gelSachet() = testCustomMedicine(
        medicationName = "Gel sachet",
        preparation = MedicinePreparation.GelSachet(
            concentrationPercent = 0.1,
            sachetWeightGrams = 1.0,
        ),
    )

    @Test
    fun medicineDisplayName_importedInjectionUsesLocalizedEsterNameInsteadOfSentinel() {
        val medicine = testCustomMedicine(
            medicationName = "External tracker",
            category = MedicationCategory.ESTRADIOL,
            displayName = "External tracker",
        ).copy(
            preparation = MedicinePreparation.ImportedInjection(
                administeredMg = 5.0,
                ester = MedicationKey.ESTRADIOL_VALERATE,
            ),
            identityKey = "E|transmtf|INJECTION|ESTRADIOL_VALERATE|mg:5",
            importedFromExternalTracker = true,
        )

        assertEquals("Estradiol valerate", medicineDisplayName(medicine, realContext))
        assertEquals(
            "戊酸雌二醇",
            medicineDisplayName(medicine, localizedContext(Locale.SIMPLIFIED_CHINESE)),
        )
    }

    @Test
    fun medicineDisplayName_importedGelUsesLocalizedEstradiolNameInsteadOfSentinel() {
        val medicine = testCustomMedicine(
            medicationName = "External tracker",
            category = MedicationCategory.ESTRADIOL,
            displayName = "External tracker",
        ).copy(
            preparation = MedicinePreparation.ImportedGel(appliedEstradiolMg = 0.75),
            identityKey = "E|transmtf|GEL|ESTRADIOL|mg:0.75",
            importedFromExternalTracker = true,
        )

        assertEquals("Estradiol", medicineDisplayName(medicine, realContext))
        assertEquals(
            "雌二醇",
            medicineDisplayName(medicine, localizedContext(Locale.SIMPLIFIED_CHINESE)),
        )
    }

    @Test
    fun medicineDisplayName_importedCatalogMedicineUsesLocalizedNameInsteadOfStoredEnglishName() {
        val medicine = testMedicine(
            key = MedicationKey.CYPROTERONE_ACETATE,
            displayName = "Cyproterone acetate",
        ).copy(
            identityKey = "E|transmtf|ORAL|CYPROTERONE_ACETATE|mg:12.5",
            importedFromExternalTracker = true,
        )

        assertEquals(
            "醋酸环丙孕酮",
            medicineDisplayName(medicine, localizedContext(Locale.SIMPLIFIED_CHINESE)),
        )
    }

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

    @Test
    fun doseInstructionText_pillFoldsLoggedCountIntoRealIntake() {
        val text = doseInstructionText(
            context = realContext,
            medicine = pill(),
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            count = 3,
        )

        assertEquals("1.5 tablets · 15 mg", text)
        assertFalse(text.orEmpty().contains("3x"))
    }

    @Test
    fun doseInstructionText_keepsSubUnitTabletFractionsExactAfterFolding() {
        assertEquals(
            "1/8 tablets · 1.25 mg",
            doseInstructionText(
                context = realContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(1, 8),
                count = 1,
            ),
        )
        assertEquals(
            "1/4 tablets · 2.5 mg",
            doseInstructionText(
                context = realContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(1, 8),
                count = 2,
            ),
        )
    }

    @Test
    fun doseInstructionText_countedWholeUnitAndConcentrationFormsShowAggregateDose() {
        assertEquals(
            "2 capsules · 10 mg",
            doseInstructionText(
                context = realContext,
                medicine = capsule5mg(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 2,
            ),
        )
        assertEquals(
            "2 patches · 2.88 mg",
            doseInstructionText(
                context = realContext,
                medicine = patchTotalMg(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 2,
            ),
        )
        assertEquals(
            "2 sachets · 0.1% · 2 g",
            doseInstructionText(
                context = realContext,
                medicine = gelSachet(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 2,
            ),
        )
        assertEquals(
            "20 mg/mL · 1 mL",
            doseInstructionText(
                context = realContext,
                medicine = multiUseVial(),
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                count = 2,
            ),
        )
    }

    @Test
    fun doseInstructionText_singleCountKeepsPreAggregateShapeAcrossForms() {
        assertEquals(
            "1/2 tablets · 5 mg",
            doseInstructionText(
                context = realContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(1, 2),
                count = 1,
            ),
        )
        assertEquals(
            "5 mg",
            doseInstructionText(
                context = realContext,
                medicine = capsule5mg(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 1,
            ),
        )
        assertEquals(
            "1.44 mg",
            doseInstructionText(
                context = realContext,
                medicine = patchTotalMg(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 1,
            ),
        )
        assertEquals(
            "0.1% · 1 g",
            doseInstructionText(
                context = realContext,
                medicine = gelSachet(),
                doseInstruction = DoseInstruction.WholeUnit,
                count = 1,
            ),
        )
        assertEquals(
            "20 mg/mL · 0.5 mL",
            doseInstructionText(
                context = realContext,
                medicine = multiUseVial(),
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                count = 1,
            ),
        )
    }

    @Test
    fun doseInstructionText_singleCountDoesNotCanonicalizeRestoredTabletFractions() {
        assertEquals(
            "3/2 tablets · 15 mg",
            doseInstructionText(
                context = realContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(3, 2),
                count = 1,
            ),
        )
        assertEquals(
            "2/2 tablets · 10 mg",
            doseInstructionText(
                context = realContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(2, 2),
                count = 1,
            ),
        )
    }

    @Test
    fun patchRouteLabelsKeepPatchOnShortAndPatchOffTitleFallback() {
        assertEquals("Patch", medicationRouteLabel(MedicationApplicationType.PATCH_ON, realContext))
        assertEquals(
            "Remove patch",
            medicationEntryTitle(
                medicine = null,
                applicationType = MedicationApplicationType.PATCH_OFF,
                context = realContext,
            ),
        )
    }

    @Test
    fun doseInstructionTextUsesContextAppLocaleDecimalSeparatorIndependentOfJvmDefault() {
        // Numbers must follow the locale of the context passed by the caller (the widget's
        // settings-derived localized context), not the JVM default. A German context yields
        // a comma separator even though the JVM default is US.
        Locale.setDefault(Locale.US)
        val germanContext = realContext.createConfigurationContext(
            android.content.res.Configuration(realContext.resources.configuration).apply {
                setLocale(Locale.GERMANY)
            }
        )

        assertEquals(
            "1/8 tablets · 1,25 mg",
            doseInstructionText(
                context = germanContext,
                medicine = pill(),
                doseInstruction = DoseInstruction.TabletFraction(1, 8),
                count = 1,
            ),
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(realContext.resources.configuration).apply {
            setLocale(locale)
        }
        return realContext.createConfigurationContext(configuration)
    }
}
