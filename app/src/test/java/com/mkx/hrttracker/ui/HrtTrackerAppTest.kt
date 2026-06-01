package com.mkx.hrttracker.ui

import android.content.Context
import android.content.res.Resources
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.navigation.Screen
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class HrtTrackerAppTest {
    private val context: Context = mockk(relaxed = true)
    private val resources: Resources = mockk(relaxed = true)

    @Test
    fun postLogStockWarningSnackbarMessage_singleUsesMedicineDisplayName() {
        val medicine = testMedicine(
            key = MedicationKey.ESTRADIOL,
            displayName = null,
        )
        every { context.resources } returns resources
        every { context.getString(R.string.medication_name_estradiol) } returns "Estradiol"
        every {
            context.getString(R.string.stock_toast_out_single, "Estradiol")
        } returns "Out of stock: Estradiol"
        every {
            context.getString(R.string.stock_toast_imminent_single, "Estradiol")
        } returns "Almost out: Estradiol"
        every {
            context.getString(R.string.stock_toast_user_low_single, "Estradiol")
        } returns "Low stock: Estradiol"

        assertEquals(
            "Out of stock: Estradiol",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Single(medicine, MedicineStockState.OUT),
                context = context,
            ),
        )
        assertEquals(
            "Almost out: Estradiol",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Single(medicine, MedicineStockState.IMMINENT),
                context = context,
            ),
        )
        assertEquals(
            "Low stock: Estradiol",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Single(medicine, MedicineStockState.USER_LOW),
                context = context,
            ),
        )
    }

    @Test
    fun postLogStockWarningDestination_singleDeepLinksToThatMedicineDetailUnderCurrentTab() {
        val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val medicine = testMedicine(uuid = uuid)

        val destination = postLogStockWarningDestination(
            PostLogStockWarning.Single(medicine, MedicineStockState.OUT),
            topLevelParentRoute = Screen.Main.route,
        )

        // Rooted under the caller's current tab so the highlighted tab stays put
        // and the back stack doesn't accumulate cross-tab entries.
        assertEquals(
            Screen.MedicineDetail.createRoute(
                medicineId = uuid.toString(),
                topLevelParentRoute = Screen.Main.route,
            ),
            destination,
        )
    }
}
