package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationParityVectorsTest {
    @Test
    fun sharedOracleVectors_matchHostRuntime() {
        assertEquals(
            PkCalibrationRoute.entries.toSet(),
            PkCalibrationParityVectors.forwardOracles.map { it.route }.toSet(),
        )

        val report = PkCalibrationParityVectors.run()

        assertTrue(report.failures.joinToString(separator = "\n"), report.isSuccess)
        assertEquals(PkCalibrationParityVectors.SUCCESS_TEXT, report.uiText)
    }
}
