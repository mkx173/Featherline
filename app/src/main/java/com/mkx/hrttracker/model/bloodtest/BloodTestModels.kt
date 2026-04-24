package com.mkx.hrttracker.model.bloodtest

import java.time.Instant
import java.util.UUID

data class BloodTestPanel(
    val uuid: UUID,
    val collectedAt: Instant,
    val collectedAtTimeZoneId: String,
    val notes: String?,
    val results: List<BloodTestResult>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BloodTestResult(
    val uuid: UUID,
    val createdAt: Instant,
    val displayOrder: Int,
    val analyte: BloodTestResultAnalyte,
    val value: Double,
    val unitSnapshot: String,
    val canonicalValue: Double,
)

sealed interface BloodTestResultAnalyte {
    data class Builtin(val key: BloodAnalyteKey) : BloodTestResultAnalyte

    data class Custom(
        val uuid: UUID,
        val name: String?,
    ) : BloodTestResultAnalyte
}

sealed interface BloodTestResultInput {
    val uuid: UUID?
    val value: Double

    data class Builtin(
        override val uuid: UUID? = null,
        val analyteKey: BloodAnalyteKey,
        val unit: BloodUnitKey,
        override val value: Double,
    ) : BloodTestResultInput

    data class Custom(
        override val uuid: UUID? = null,
        val customAnalyteUuid: UUID,
        override val value: Double,
    ) : BloodTestResultInput
}

data class CustomBloodAnalyte(
    val uuid: UUID,
    val name: String,
    val unitLabel: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
)

data class BloodTestTrendPoint(
    val panelUuid: UUID,
    val resultUuid: UUID,
    val collectedAt: Instant,
    val collectedAtTimeZoneId: String,
    val value: Double,
    val unitSnapshot: String,
    val canonicalValue: Double,
)
