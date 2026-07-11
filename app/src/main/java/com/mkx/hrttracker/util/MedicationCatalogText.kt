package com.mkx.hrttracker.util

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationKey

@get:StringRes
val MedicationCategory.labelRes: Int
    get() = when (this) {
        MedicationCategory.ESTRADIOL -> R.string.medication_category_estradiol
        MedicationCategory.TESTOSTERONE -> R.string.medication_category_testosterone
        MedicationCategory.ANTIANDROGEN -> R.string.medication_category_antiandrogen
        MedicationCategory.SERM -> R.string.medication_category_serm
        MedicationCategory.GNRH_AGONIST -> R.string.medication_category_gnrh_agonist
        MedicationCategory.CUSTOM -> R.string.medication_category_custom
    }

@get:StringRes
val MedicationApplicationType.labelRes: Int
    get() = when (this) {
        MedicationApplicationType.ORAL -> R.string.medication_application_oral
        MedicationApplicationType.SUBLINGUAL -> R.string.medication_application_sublingual
        MedicationApplicationType.INJECTION -> R.string.medication_application_injection
        MedicationApplicationType.GEL -> R.string.medication_application_gel
        MedicationApplicationType.PATCH_ON -> R.string.medication_application_patch_on
        MedicationApplicationType.PATCH_OFF -> R.string.medication_application_patch_off
    }

@get:StringRes
val MedicationGelApplicationArea.labelRes: Int
    get() = when (this) {
        MedicationGelApplicationArea.DEFAULT -> R.string.medication_gel_application_area_default
    }

@get:StringRes
val MedicationKey.labelRes: Int
    get() = when (this) {
        MedicationKey.SPIRONOLACTONE -> R.string.medication_name_spironolactone
        MedicationKey.CYPROTERONE_ACETATE -> R.string.medication_name_cyproterone_acetate
        MedicationKey.BICALUTAMIDE -> R.string.medication_name_bicalutamide
        MedicationKey.FINASTERIDE -> R.string.medication_name_finasteride
        MedicationKey.DUTASTERIDE -> R.string.medication_name_dutasteride
        MedicationKey.RALOXIFENE -> R.string.medication_name_raloxifene
        MedicationKey.TAMOXIFEN -> R.string.medication_name_tamoxifen
        MedicationKey.TRIPTORELIN -> R.string.medication_name_triptorelin
        MedicationKey.LEUPRORELIN -> R.string.medication_name_leuprorelin
        MedicationKey.GOSERELIN -> R.string.medication_name_goserelin
        MedicationKey.ESTRADIOL -> R.string.medication_name_estradiol
        MedicationKey.ESTRADIOL_VALERATE -> R.string.medication_name_estradiol_valerate
        MedicationKey.ESTRADIOL_BENZOATE -> R.string.medication_name_estradiol_benzoate
        MedicationKey.ESTRADIOL_CYPIONATE -> R.string.medication_name_estradiol_cypionate
        MedicationKey.ESTRADIOL_ENANTHATE -> R.string.medication_name_estradiol_enanthate
        MedicationKey.ESTRADIOL_UNDECYLATE -> R.string.medication_name_estradiol_undecylate
        MedicationKey.ESTRADIOL_GEL -> R.string.medication_name_estradiol_gel
        MedicationKey.ESTRADIOL_PATCH -> R.string.medication_name_estradiol_patch
    }
