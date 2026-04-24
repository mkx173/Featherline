package com.mkx.hrttracker.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "blood_test_panels",
    indices = [
        Index("collectedAtInstantEpochMillis")
    ]
)
data class BloodTestPanelEntity(
    @PrimaryKey val uuid: String,
    val collectedAtInstantEpochMillis: Long,
    val collectedAtTimeZoneId: String,
    val notes: String?,
    val timeSinceLastEstradiolDoseMillis: Long?,
    val timeSinceLastTestosteroneDoseMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "custom_blood_analytes",
    indices = [
        Index(value = ["normalizedName", "normalizedUnitLabel"], unique = true)
    ]
)
data class CustomBloodAnalyteEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val normalizedName: String,
    val unitLabel: String,
    val normalizedUnitLabel: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
)

@Entity(
    tableName = "blood_test_results",
    foreignKeys = [
        ForeignKey(
            entity = BloodTestPanelEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["panelUuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomBloodAnalyteEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["customAnalyteUuid"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("panelUuid"),
        Index("builtinAnalyteKey"),
        Index("customAnalyteUuid"),
        Index(value = ["panelUuid", "displayOrder"], unique = true),
        Index(value = ["panelUuid", "builtinAnalyteKey"], unique = true),
        Index(value = ["panelUuid", "customAnalyteUuid"], unique = true)
    ]
)
data class BloodTestResultEntity(
    @PrimaryKey val uuid: String,
    val panelUuid: String,
    val createdAtEpochMillis: Long,
    val displayOrder: Int,
    val builtinAnalyteKey: String?,
    val customAnalyteUuid: String?,
    val value: Double,
    val unitSnapshot: String,
    val canonicalValue: Double,
)

data class BloodTestPanelWithResultsEntity(
    @Embedded val panel: BloodTestPanelEntity,
    @Relation(
        parentColumn = "uuid",
        entityColumn = "panelUuid"
    )
    val results: List<BloodTestResultEntity>,
)

data class BloodTestTrendPointEntity(
    val panelUuid: String,
    val resultUuid: String,
    val collectedAtInstantEpochMillis: Long,
    val collectedAtTimeZoneId: String,
    val value: Double,
    val unitSnapshot: String,
    val canonicalValue: Double,
)
