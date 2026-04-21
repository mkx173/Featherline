package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails

internal enum class MainMedicationAccent {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

internal fun mainMedicationAccent(details: MedicationDetails?): MainMedicationAccent {
    if (details == null) {
        return MainMedicationAccent.NEUTRAL
    }

    return when {
        details.category == MedicationCategory.ANTIANDROGEN -> MainMedicationAccent.SECONDARY
        details.applicationType in setOf(
            MedicationApplicationType.GEL,
            MedicationApplicationType.PATCH_ON,
            MedicationApplicationType.PATCH_OFF,
        ) -> MainMedicationAccent.TERTIARY
        details.category == MedicationCategory.ESTRADIOL -> MainMedicationAccent.PRIMARY
        else -> MainMedicationAccent.NEUTRAL
    }
}

internal enum class MainTodayRowTone {
    DEFAULT,
    DUE_SOON,
    OVERDUE,
    DONE,
}

internal fun mainTodayRowTone(status: MainTodayDoseStatus): MainTodayRowTone {
    return when (status) {
        MainTodayDoseStatus.DONE -> MainTodayRowTone.DONE
        MainTodayDoseStatus.DUE_SOON -> MainTodayRowTone.DUE_SOON
        MainTodayDoseStatus.UPCOMING -> MainTodayRowTone.DEFAULT
        MainTodayDoseStatus.OVERDUE -> MainTodayRowTone.OVERDUE
    }
}
