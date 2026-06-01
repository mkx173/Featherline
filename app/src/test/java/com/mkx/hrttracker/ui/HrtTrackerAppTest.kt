package com.mkx.hrttracker.ui

import android.content.Context
import android.content.res.Resources
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.PostLogStockWarning
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun postLogStockWarningSnackbarMessage_manyUsesQuantityResources() {
        every { context.resources } returns resources
        every { resources.getQuantityString(R.plurals.stock_toast_out_multiple, 2, 2) } returns
            "2 medicines out of stock"
        every { resources.getQuantityString(R.plurals.stock_toast_imminent_multiple, 2, 2) } returns
            "2 medicines almost out"
        every { resources.getQuantityString(R.plurals.stock_toast_user_low_multiple, 2, 2) } returns
            "2 medicines low on stock"

        assertEquals(
            "2 medicines out of stock",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Many(2, MedicineStockState.OUT),
                context = context,
            ),
        )
        assertEquals(
            "2 medicines almost out",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Many(2, MedicineStockState.IMMINENT),
                context = context,
            ),
        )
        assertEquals(
            "2 medicines low on stock",
            postLogStockWarningSnackbarMessage(
                warning = PostLogStockWarning.Many(2, MedicineStockState.USER_LOW),
                context = context,
            ),
        )
    }
}
