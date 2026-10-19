package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.ui.components.BackupPasswordValidationError
import com.mkx.hrttracker.ui.components.validateBackupPasswordInput
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
                minimumPasswordLength = 6,
            )
        )
    }

    @Test
    fun validateBackupPasswordInput_accepts_valid_password() {
        assertNull(
            validateBackupPasswordInput(
                password = "123456",
                minimumPasswordLength = 6,
            )
        )
    }
}
