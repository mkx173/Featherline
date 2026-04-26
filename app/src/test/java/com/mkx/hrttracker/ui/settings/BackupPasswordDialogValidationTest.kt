package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPasswordDialogValidationTest {
    @Test
    fun validateBackupPasswordInput_requires_non_empty_password() {
        assertEquals(
            BackupPasswordValidationError.REQUIRED,
            validateBackupPasswordInput(
                password = "",
                confirmPassword = "",
                minimumPasswordLength = 6,
            )
        )
    }

    @Test
    fun validateBackupPasswordInput_requires_minimum_length() {
        assertEquals(
            BackupPasswordValidationError.TOO_SHORT,
            validateBackupPasswordInput(
                password = "12345",
                confirmPassword = "12345",
                minimumPasswordLength = 6,
            )
        )
    }

    @Test
    fun validateBackupPasswordInput_requires_matching_confirmation() {
        assertEquals(
            BackupPasswordValidationError.MISMATCH,
            validateBackupPasswordInput(
                password = "123456",
                confirmPassword = "654321",
                minimumPasswordLength = 6,
            )
        )
    }

    @Test
    fun validateBackupPasswordInput_accepts_valid_password() {
        assertNull(
            validateBackupPasswordInput(
                password = "123456",
                confirmPassword = "123456",
                minimumPasswordLength = 6,
            )
        )
    }
}
