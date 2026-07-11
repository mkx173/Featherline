package com.mkx.hrttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the semantics of the latest-antiandrogen home snapshot query
 * (`HomeDao.getLatestAntiandrogenEntriesOnOrBefore`): the single latest
 * antiandrogen-card category entry on or before the cutoff, per full medication signature,
 * with ties on `appliedAtEpochMillis` broken by the greater `uuid`. This is the
 * contract the `ROW_NUMBER()` window query must keep; it must not change when
 * the query implementation is tuned.
 */
@RunWith(AndroidJUnit4::class)
class HomeDaoLatestAntiandrogenTest {
    private lateinit var database: HrtTrackerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun latestAntiandrogen_picksLatestPerIdentity_honoringTiesNullGroupsAndCutoff() =
        runBlocking {
            val cutoff = 10_000L
            database.medicationLogDao().insertEntries(
                listOf(
                    // Identity A: (ORAL, m-aa, group-1) — newer wins within the identity.
                    aaEntry("a-old", appliedAt = 1_000, group = "group-1"),
                    aaEntry("a-new", appliedAt = 2_000, group = "group-1"),
                    // A future row for identity A, past the cutoff: must be excluded, so
                    // a-new (not a-future) is identity A's answer.
                    aaEntry("a-future", appliedAt = 99_000, group = "group-1"),
                    // Identity B: (ORAL, m-aa, null) — manual logs, distinct from A's group.
                    aaEntry("b-old", appliedAt = 1_500, group = null),
                    aaEntry("b-new", appliedAt = 3_000, group = null),
                    // Identity C: (INJECTION, m-aa2, group-2) with a same-timestamp tie;
                    // the greater uuid ("c-tie-2") must win.
                    aaEntry(
                        "c-tie-1",
                        appliedAt = 4_000,
                        group = "group-2",
                        applicationType = MedicationApplicationType.INJECTION.name,
                        medicineUuid = "m-aa2",
                    ),
                    aaEntry(
                        "c-tie-2",
                        appliedAt = 4_000,
                        group = "group-2",
                        applicationType = MedicationApplicationType.INJECTION.name,
                        medicineUuid = "m-aa2",
                    ),
                    // Noise: a non-antiandrogen row must be ignored entirely.
                    e2Entry("e2", appliedAt = 5_000),
                )
            )

            val latest = database.homeDao()
                .getLatestAntiandrogenEntriesOnOrBefore(cutoff)
                .map { it.uuid }
                .toSet()

            assertEquals(setOf("a-new", "b-new", "c-tie-2"), latest)
        }

    @Test
    fun latestAntiandrogen_returnsLatestPerFullDoseSignature() =
        runBlocking {
            val cutoff = 10_000L
            database.medicationLogDao().insertEntries(
                listOf(
                    aaEntry(
                        "quarter-old",
                        appliedAt = 1_000,
                        group = "group-1",
                        tabletFractionNumerator = 1,
                        tabletFractionDenominator = 4,
                    ),
                    aaEntry(
                        "quarter-new",
                        appliedAt = 4_000,
                        group = "group-1",
                        tabletFractionNumerator = 1,
                        tabletFractionDenominator = 4,
                    ),
                    aaEntry(
                        "third",
                        appliedAt = 2_000,
                        group = "group-1",
                        tabletFractionNumerator = 1,
                        tabletFractionDenominator = 3,
                    ),
                )
            )

            val latest = database.homeDao()
                .getLatestAntiandrogenEntriesOnOrBefore(cutoff)
                .map { it.uuid }
                .toSet()

            assertEquals(setOf("quarter-new", "third"), latest)
        }

    @Test
    fun latestAntiandrogen_includesSermAndGnrhAgonistHistory() = runBlocking {
        val cutoff = 10_000L
        database.medicationLogDao().insertEntries(
            listOf(
                aaEntry("aa", appliedAt = 1_000, group = "group-aa"),
                categoryEntry(
                    uuid = "serm",
                    category = MedicationCategory.SERM,
                    appliedAt = 2_000,
                    group = "group-serm",
                    medicineUuid = "m-serm",
                ),
                categoryEntry(
                    uuid = "gnrh",
                    category = MedicationCategory.GNRH_AGONIST,
                    appliedAt = 3_000,
                    group = "group-gnrh",
                    medicineUuid = "m-gnrh",
                ),
                e2Entry("e2", appliedAt = 4_000),
            )
        )

        val latest = database.homeDao()
            .getLatestAntiandrogenEntriesOnOrBefore(cutoff)
            .map { it.uuid }
            .toSet()

        assertEquals(setOf("aa", "serm", "gnrh"), latest)
    }

    @Test
    fun observeLatestAntiandrogen_returnsLatestPerFullDoseSignature() =
        runBlocking {
            val cutoff = 10_000L
            database.medicationLogDao().insertEntries(
                listOf(
                    aaEntry(
                        "quarter",
                        appliedAt = 4_000,
                        group = "group-1",
                        tabletFractionNumerator = 1,
                        tabletFractionDenominator = 4,
                    ),
                    aaEntry(
                        "third",
                        appliedAt = 2_000,
                        group = "group-1",
                        tabletFractionNumerator = 1,
                        tabletFractionDenominator = 3,
                    ),
                    categoryEntry(
                        uuid = "serm",
                        category = MedicationCategory.SERM,
                        appliedAt = 3_000,
                        group = "group-serm",
                        medicineUuid = "m-serm",
                    ),
                    categoryEntry(
                        uuid = "gnrh",
                        category = MedicationCategory.GNRH_AGONIST,
                        appliedAt = 1_000,
                        group = "group-gnrh",
                        medicineUuid = "m-gnrh",
                    ),
                )
            )

            val latest = database.homeDao()
                .observeLatestAntiandrogenEntriesOnOrBefore(cutoff)
                .first()
                .map { it.uuid }
                .toSet()

            assertEquals(setOf("quarter", "third", "serm", "gnrh"), latest)
        }

    private fun categoryEntry(
        uuid: String,
        category: MedicationCategory,
        appliedAt: Long,
        group: String?,
        medicineUuid: String,
    ): MedicationLogEntryEntity = MedicationLogEntryEntity(
        uuid = uuid,
        category = category.name,
        medicineUuid = medicineUuid,
        applicationType = MedicationApplicationType.INJECTION.name,
        doseInstructionKind = DoseInstructionKind.VOLUME_ML.name,
        tabletFractionNumerator = null,
        tabletFractionDenominator = null,
        doseVolumeMl = 1.0,
        doseWeightGrams = null,
        equivalentE2Mg = null,
        sourceGroupUuid = group,
        appliedAtEpochMillis = appliedAt,
        appliedAtTimeZoneId = "UTC",
    )

    private fun aaEntry(
        uuid: String,
        appliedAt: Long,
        group: String?,
        applicationType: String = MedicationApplicationType.ORAL.name,
        medicineUuid: String? = "m-aa",
        tabletFractionNumerator: Int = 1,
        tabletFractionDenominator: Int = 1,
    ): MedicationLogEntryEntity = MedicationLogEntryEntity(
        uuid = uuid,
        category = MedicationCategory.ANTIANDROGEN.name,
        medicineUuid = medicineUuid,
        applicationType = applicationType,
        doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
        tabletFractionNumerator = tabletFractionNumerator,
        tabletFractionDenominator = tabletFractionDenominator,
        doseVolumeMl = null,
        doseWeightGrams = null,
        equivalentE2Mg = null,
        sourceGroupUuid = group,
        appliedAtEpochMillis = appliedAt,
        appliedAtTimeZoneId = "UTC",
    )

    private fun e2Entry(
        uuid: String,
        appliedAt: Long,
    ): MedicationLogEntryEntity = MedicationLogEntryEntity(
        uuid = uuid,
        category = MedicationCategory.ESTRADIOL.name,
        medicineUuid = "m-e2",
        applicationType = MedicationApplicationType.ORAL.name,
        doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
        tabletFractionNumerator = 1,
        tabletFractionDenominator = 1,
        doseVolumeMl = null,
        doseWeightGrams = null,
        equivalentE2Mg = 2.0,
        sourceGroupUuid = null,
        appliedAtEpochMillis = appliedAt,
        appliedAtTimeZoneId = "UTC",
    )
}
