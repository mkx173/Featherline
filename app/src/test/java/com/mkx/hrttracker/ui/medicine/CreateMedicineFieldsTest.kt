package com.mkx.hrttracker.ui.medicine

import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.ui.text.input.ImeAction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateMedicineFieldsTest {

    // The ordered field list drives IME Next/Done routing in the sheet.
    // Catalog pill: display-name override sits in the identity section, so it
    // precedes the preparation strength field — mirrors where CUSTOM_NAME
    // appears for custom medicines.
    @Test
    fun editableFields_forCatalogPill_ordersDisplayNameThenStrength() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )

        assertEquals(
            listOf(
                CreateMedicineField.DISPLAY_NAME,
                CreateMedicineField.PILL_STRENGTH,
            ),
            editableFields(draft),
        )
    }

    // Custom-named medicines: customMedicationName is the primary name, so
    // the display-name override is hidden — the last visible field becomes
    // the preparation field. Forcing selectionKind=CUSTOM is the
    // requiresCustomName() trigger; the category stays ESTRADIOL because
    // CUSTOM doesn't expose gel routes.
    @Test
    fun editableFields_forCustomGel_omitsDisplayNameAndPicksContainerByDefault() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
        ).copy(selectionKind = MedicationSelectionKind.CUSTOM)

        assertEquals(
            listOf(
                CreateMedicineField.CUSTOM_NAME,
                CreateMedicineField.GEL_PERCENT,
                CreateMedicineField.CONTAINER_WEIGHT,
            ),
            editableFields(draft),
        )
    }

    @Test
    fun editableFields_forPatchReleaseRate_picksReleaseRateFieldOnly() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
        ).copy(patchSpecKind = PatchSpecKind.RELEASE_RATE)

        val fields = editableFields(draft)

        assertTrue(
            fields.contains(CreateMedicineField.PATCH_RELEASE_RATE) &&
                !fields.contains(CreateMedicineField.PATCH_TOTAL_MG)
        )
    }

    @Test
    fun editableFields_forPatchOff_isEmpty() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )

        assertEquals(emptyList<CreateMedicineField>(), editableFields(draft))
    }

    // Encodes the IME promise: all but the last get Next, the last gets Done.
    @Test
    fun imeActionFor_picksDoneOnlyForTheLastField() {
        val fields = listOf(
            CreateMedicineField.CONCENTRATION_MG_PER_ML,
            CreateMedicineField.VIAL_VOLUME_ML,
        )

        assertEquals(
            ImeAction.Next,
            imeActionFor(fields, CreateMedicineField.CONCENTRATION_MG_PER_ML),
        )
        assertEquals(
            ImeAction.Done,
            imeActionFor(fields, CreateMedicineField.VIAL_VOLUME_ML),
        )
    }

    @Test
    fun nextField_walksTheListThenReturnsNull() {
        val fields = listOf(
            CreateMedicineField.CUSTOM_NAME,
            CreateMedicineField.PILL_STRENGTH,
        )

        assertEquals(
            CreateMedicineField.PILL_STRENGTH,
            nextField(fields, CreateMedicineField.CUSTOM_NAME),
        )
        assertNull(nextField(fields, CreateMedicineField.PILL_STRENGTH))
    }

    @Test
    fun displayNameFieldLabelPosition_keepsPlaceholderVisibleWhenEmpty() {
        val position = displayNameFieldLabelPosition()

        assertTrue(position is TextFieldLabelPosition.Attached)
        assertTrue((position as TextFieldLabelPosition.Attached).alwaysMinimize)
    }
}
