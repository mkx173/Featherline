package com.mkx.hrttracker.data.importer

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import kotlin.math.round

class ExternalImportParserTest {
    private val parser = ExternalImportParser()

    @Test
    fun detectsTransmtfCurrentObjectWithGelProducts() {
        val result = parser.parse(
            """
            {
              "version": "2026.1",
              "exportedAt": "2026-06-01T00:00:00Z",
              "gelProducts": [{ "id": "estrogel" }],
              "events": [
                {
                  "id": "gel-a",
                  "timeH": 12.5,
                  "route": "gel",
                  "ester": "E2",
                  "doseMG": 0.75,
                  "extras": {
                    "product": "Oestrogel",
                    "site": "left arm",
                    "area": "upper",
                    "wash": "6h"
                  }
                }
              ],
              "labResults": [
                { "id": "lab-a", "timeH": 18, "unit": "pg/ml", "value": 150 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.TRANSMTF, result.sourceApp)
        assertEquals("2026.1", result.exportVersion)
        assertEquals("2026-06-01T00:00:00Z", result.exportedAt)
        assertEquals(1, result.medicationDoses.size)
        assertEquals(1, result.labResults.size)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())

        val dose = result.medicationDoses.single()
        assertEquals("gel-a", dose.provenance.externalId)
        assertEquals(MedicationApplicationType.GEL, dose.applicationType)
        assertEquals(MedicationCategory.ESTRADIOL, dose.category)
        assertIs<MedicinePreparation.ImportedGel>(dose.medicineIdentity!!.preparation)
        assertEquals("E|transmtf|GEL|ESTRADIOL|mg:0.75", dose.medicineIdentity.identityKey)

        val lab = result.labResults.single()
        assertEquals(BloodAnalyteKey.E2, lab.analyteKey)
        assertEquals(BloodUnitKey.PG_ML, lab.unitKey)
    }

    @Test
    fun numericExternalIdsStringifyWithoutTrailingDecimalPoint() {
        // Moshi decodes every JSON number as Double, so a numeric id must render
        // as "1042"/"2048", not "1042.0"/"2048.0" — otherwise the dedup key on
        // (importSourceApp, importExternalId) is corrupted and re-imports of the
        // same row no longer match.
        val result = parser.parse(
            """
            {
              "gelProducts": [{ "id": "estrogel" }],
              "events": [
                { "id": 1042, "timeH": 12.5, "route": "gel", "ester": "E2", "doseMG": 0.75 }
              ],
              "labResults": [
                { "id": 2048, "timeH": 18, "unit": "pg/ml", "value": 150 }
              ]
            }
            """.trimIndent()
        )

        assertEquals("1042", result.medicationDoses.single().provenance.externalId)
        assertEquals("2048", result.labResults.single().provenance.externalId)
    }

    @Test
    fun detectsOyamaNestedModesAndImportsTransfemDataWhenActiveModeIsTransmasc() {
        val result = parser.parse(
            """
            {
              "mode": "transmasc",
              "modes": {
                "transfem": {
                  "events": [
                    { "id": "tf-dose", "timeH": 2, "route": "oral", "ester": "EV", "doseMG": 2 }
                  ],
                  "labResults": [
                    { "id": "tf-e2", "timeH": 4, "unit": "pmol/l", "value": 367.1 }
                  ]
                },
                "transmasc": {
                  "events": [
                    { "id": "tm-dose", "timeH": 3, "route": "oral", "ester": "T", "doseMG": 20 }
                  ],
                  "labResults": [
                    { "id": "tm-t", "timeH": 4, "unit": "ng/dl", "value": 42 }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.OYAMA, result.sourceApp)
        assertEquals(1, result.medicationDoses.size)
        assertEquals(2, result.labResults.size)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())

        val dose = result.medicationDoses.single()
        assertEquals("tf-dose", dose.provenance.externalId)
        assertEquals(MedicationApplicationType.ORAL, dose.applicationType)
        assertEquals(MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE), dose.medicineIdentity!!.selection)
        assertEquals(BloodAnalyteKey.E2, result.labResults[0].analyteKey)
        assertEquals(BloodAnalyteKey.T, result.labResults[1].analyteKey)
    }

    @Test
    fun positivelyIdentifiesLegacyFlatOyamaViaDoseTemplates() {
        val result = parser.parse(
            """
            {
              "doseTemplates": [{ "name": "2mg EV" }],
              "events": [
                { "id": "legacy-dose", "timeH": 5, "route": "sublingual", "ester": "EV", "doseMG": 1 }
              ],
              "labResults": [
                { "id": "legacy-e2", "timeH": 6, "unit": "pg/ml", "value": 101 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.OYAMA, result.sourceApp)
        assertEquals(1, result.medicationDoses.size)
        assertEquals(1, result.labResults.size)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun detectsNoMtfAtRootAndUnderData() {
        val rootResult = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                { "id": "n-root", "timeH": 1, "route": "oral", "compound": "E2", "doseMG": 2 }
              ]
            }
            """.trimIndent()
        )
        val dataResult = parser.parse(
            """
            {
              "data": {
                "hrtRecorder": { "version": 3 },
                "events": [
                  { "id": "n-data", "timeH": 2, "route": "oral", "compound": "E2", "doseMG": 1 }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.NOMTF, rootResult.sourceApp)
        assertEquals(ExternalTrackerSourceApp.NOMTF, dataResult.sourceApp)
        assertEquals(1, rootResult.medicationDoses.size)
        assertEquals(1, dataResult.medicationDoses.size)
        assertEquals("n-root", rootResult.medicationDoses.single().provenance.externalId)
        assertEquals("n-data", dataResult.medicationDoses.single().provenance.externalId)
    }

    @Test
    fun noMtfDiscriminatorsWinBeforeTransmtfSignals() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "gelProducts": [{ "id": "looks-transmtf" }],
              "events": [
                {
                  "id": "aa",
                  "timeH": 8,
                  "route": "oral",
                  "category": "antiAndrogen",
                  "compound": "cpa",
                  "doseMG": 12.5
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.NOMTF, result.sourceApp)
        assertEquals(MedicationCategory.ANTIANDROGEN, result.medicationDoses.single().category)
        assertNull(result.medicationDoses.single().equivalentE2Mg)
    }

    @Test
    fun assignsTransmtfCompatibleSentinelForAmbiguousFlatEsterEvents() {
        val result = parser.parse(
            """
            {
              "events": [
                { "id": "flat-ev", "timeH": 3, "route": "injection", "ester": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE, result.sourceApp)
        assertEquals(1, result.medicationDoses.size)
        assertEquals(
            "E|transmtf-compatible|INJECTION|ESTRADIOL_VALERATE|mg:5",
            result.medicationDoses.single().medicineIdentity!!.identityKey,
        )
    }

    @Test
    fun rejectsEncryptedOyamaUnknownShapeAndBareArray() {
        assertFatal("""{ "encrypted": true, "mode": "transfem" }""", "encrypted")
        assertFatal("""{ "events": [{ "id": "x", "timeH": 1 }] }""", "unknown")
        assertFatal("""[{ "id": "x" }]""", "top-level object")
    }

    @Test
    fun encryptedFlagRejectsTruthyOrNonBooleanButAcceptsExplicitFalse() {
        // A truthy or non-boolean flag must be treated as encrypted and rejected,
        // so an encrypted payload can never slip past as plaintext just because
        // the flag arrived as a string or number rather than a boolean true.
        assertFatal("""{ "encrypted": "yes", "mode": "transfem" }""", "encrypted")
        assertFatal("""{ "encrypted": "true", "mode": "transfem" }""", "encrypted")
        assertFatal("""{ "encrypted": 1, "mode": "transfem" }""", "encrypted")
        assertFatal("""{ "encrypted": 0, "mode": "transfem" }""", "encrypted")

        // Only an explicit boolean false or the string "false" is recognized as
        // "not encrypted"; the export then parses normally.
        fun oyamaBody(encrypted: String) =
            """
            {
              "encrypted": $encrypted,
              "mode": "transfem",
              "modes": {
                "transfem": {
                  "events": [
                    { "id": "tf-dose", "timeH": 2, "route": "oral", "ester": "EV", "doseMG": 2 }
                  ],
                  "labResults": []
                }
              }
            }
            """.trimIndent()

        assertEquals(ExternalTrackerSourceApp.OYAMA, parser.parse(oyamaBody("false")).sourceApp)
        assertEquals(ExternalTrackerSourceApp.OYAMA, parser.parse(oyamaBody("\"false\"")).sourceApp)
    }

    @Test
    fun noMtfAntiandrogenClassificationAndRecordOnlySkipsHappenBeforeCompoundMapping() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                {
                  "id": "cpa-dose",
                  "timeH": 1,
                  "route": "oral",
                  "category": "cpa",
                  "compound": "E2",
                  "doseMG": 12.5
                },
                {
                  "id": "finasteride",
                  "timeH": 2,
                  "route": "oral",
                  "recordOnlyMedication": "finasteride",
                  "compound": "E2",
                  "doseMG": 1
                },
                {
                  "id": "dutasteride",
                  "timeH": 3,
                  "route": "oral",
                  "category": "antiAndrogen",
                  "compound": "dutasteride",
                  "doseMG": 0.5
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.medicationDoses.size)
        val dose = result.medicationDoses.single()
        assertEquals("cpa-dose", dose.provenance.externalId)
        assertEquals(MedicationCategory.ANTIANDROGEN, dose.category)
        assertEquals(MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE), dose.medicineIdentity!!.selection)
        assertEquals(
            listOf(
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun patchReleaseRateUsesRateIdentityAndPatchTotalUsesTotalMgIdentity() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                {
                  "id": "patch-rate",
                  "timeH": 10,
                  "route": "patchApply",
                  "ester": "E2",
                  "doseMG": 0,
                  "extras": { "releaseRateUGPerDay": 100 }
                },
                {
                  "id": "patch-total",
                  "timeH": 11,
                  "route": "patchApply",
                  "ester": "E2",
                  "doseMG": 3.2
                },
                {
                  "id": "patch-remove",
                  "timeH": 12,
                  "route": "patchRemove",
                  "ester": "E2",
                  "doseMG": 0
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(3, result.medicationDoses.size)
        val rate = result.medicationDoses[0]
        val ratePatch = assertIs<MedicinePreparation.Patch>(rate.medicineIdentity!!.preparation)
        assertIs<MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay>(ratePatch.specification)
        assertEquals("E|transmtf|PATCH_ON|ESTRADIOL_PATCH|rate:100", rate.medicineIdentity.identityKey)

        val total = result.medicationDoses[1]
        val totalPatch = assertIs<MedicinePreparation.Patch>(total.medicineIdentity!!.preparation)
        assertIs<MedicinePreparation.PatchSpecification.TotalMg>(totalPatch.specification)
        assertEquals("E|transmtf|PATCH_ON|ESTRADIOL_PATCH|totalmg:3.2", total.medicineIdentity.identityKey)

        val remove = result.medicationDoses[2]
        assertEquals(MedicationApplicationType.PATCH_OFF, remove.applicationType)
        assertNull(remove.medicineIdentity)
        assertEquals(DoseInstruction.Noop, remove.doseInstruction)
    }

    @Test
    fun gelExtrasDoNotSplitIdentity() {
        val result = parser.parse(
            """
            {
              "gelProducts": [{ "id": "gel" }],
              "events": [
                {
                  "id": "gel-left",
                  "timeH": 1,
                  "route": "gel",
                  "ester": "E2",
                  "doseMG": 1.5,
                  "extras": { "product": "A", "site": "left arm", "area": "wide", "wash": "4h" }
                },
                {
                  "id": "gel-right",
                  "timeH": 2,
                  "route": "gel",
                  "ester": "E2",
                  "doseMG": 1.500,
                  "extras": { "product": "B", "site": "right thigh", "area": "small", "wash": "8h" }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, result.medicationDoses.size)
        assertEquals(
            result.medicationDoses[0].medicineIdentity!!.identityKey,
            result.medicationDoses[1].medicineIdentity!!.identityKey,
        )
        assertIs<MedicinePreparation.ImportedGel>(result.medicationDoses[0].medicineIdentity!!.preparation)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun noMtfInjectionReconstructsAdministeredMgFromActiveEquivalentDose() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                { "id": "n-inj", "timeH": 1, "route": "injection", "compound": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        )

        val dose = result.medicationDoses.single()
        val injection = assertIs<MedicinePreparation.ImportedInjection>(dose.medicineIdentity!!.preparation)
        val ratio = requireNotNull(
            DoseInstructionCalculator.e2EquivalenceRatio(MedicationKey.ESTRADIOL_VALERATE)
        )
        assertEquals(5.0 / ratio, injection.administeredMg, 1e-9)
        assertEquals(5.0, dose.equivalentE2Mg!!, 1e-9)
    }

    @Test
    fun subHourTimeHDuplicateExternalIdsAndMalformedRowsAreRowLevelWarnings() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "ok", "timeH": 1.25, "route": "oral", "ester": "E2", "doseMG": 2 },
                { "id": "ok", "timeH": 2, "route": "oral", "ester": "E2", "doseMG": 2 },
                { "id": "", "timeH": 3, "route": "oral", "ester": "E2", "doseMG": 2 },
                { "timeH": 4, "route": "oral", "ester": "E2", "doseMG": 2 },
                { "id": "bad-time", "timeH": "soon", "route": "oral", "ester": "E2", "doseMG": 2 },
                { "id": "bad-dose", "timeH": 5, "route": "oral", "ester": "E2", "doseMG": -1 }
              ],
              "labResults": [
                { "id": "lab-ok", "timeH": 2.5, "unit": "pg/ml", "value": 120 },
                { "id": "lab-bad", "timeH": 3, "unit": "pg/ml", "value": "high" }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.medicationDoses.size)
        assertEquals(round(1.25 * 3_600_000.0).toLong(), result.medicationDoses.single().appliedAtEpochMillis)
        assertEquals(ZoneId.systemDefault().id, result.medicationDoses.single().appliedAtTimeZoneId)
        assertEquals(1, result.labResults.size)
        assertEquals(round(2.5 * 3_600_000.0).toLong(), result.labResults.single().collectedAtEpochMillis)
        assertEquals(
            listOf(
                ExternalImportWarningReason.DUPLICATE_EXTERNAL_ID,
                ExternalImportWarningReason.MISSING_EXTERNAL_ID,
                ExternalImportWarningReason.MISSING_EXTERNAL_ID,
                ExternalImportWarningReason.MALFORMED_ROW,
                ExternalImportWarningReason.MALFORMED_ROW,
                ExternalImportWarningReason.MALFORMED_ROW,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun skipsUnsupportedEstrogenCompoundsAndTestosteroneDoseRows() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "eu", "timeH": 1, "route": "oral", "ester": "EU", "doseMG": 2 },
                { "id": "epp", "timeH": 2, "route": "oral", "ester": "EPP", "doseMG": 2 },
                { "id": "t", "timeH": 3, "route": "oral", "ester": "T", "doseMG": 20 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), result.medicationDoses)
        assertEquals(
            listOf(
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_CATEGORY,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun mapsLabUnitsAndSkipsAmbiguousNoMtfUnitsAndDuplicatePanelAnalytes() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "labResults": [
                { "id": "e2-a", "timeH": 10, "unit": "pg/ml", "value": 125 },
                { "id": "e2-b", "timeH": 10, "unit": "pmol/l", "value": 450 },
                { "id": "ambiguous", "timeH": 11, "unit": "ng/dl", "value": 42 }
              ],
              "events": [
                { "id": "dose", "timeH": 1, "route": "oral", "compound": "E2", "doseMG": 2 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.labResults.size)
        val lab = result.labResults.single()
        assertEquals("e2-a", lab.provenance.externalId)
        assertEquals(BloodAnalyteKey.E2, lab.analyteKey)
        assertEquals(BloodUnitKey.PG_ML, lab.unitKey)
        assertEquals(
            listOf(
                ExternalImportWarningReason.DUPLICATE_LAB_RESULT,
                ExternalImportWarningReason.AMBIGUOUS_LAB_UNIT,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun transmtfE2LabsSkipUnitsOutsideExternalImportMapping() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "dose", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
              ],
              "labResults": [
                { "id": "e2-good", "timeH": 2, "unit": "pg/ml", "value": 125 },
                { "id": "e2-bad-ngml", "timeH": 3, "unit": "ng/ml", "value": 0.12 },
                { "id": "e2-bad-miul", "timeH": 4, "unit": "miu/l", "value": 100 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("dose"), result.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(listOf("e2-good"), result.labResults.map { lab -> lab.provenance.externalId })
        assertEquals(BloodAnalyteKey.E2, result.labResults.single().analyteKey)
        assertEquals(BloodUnitKey.PG_ML, result.labResults.single().unitKey)
        assertEquals(
            listOf(
                ExternalImportWarningReason.AMBIGUOUS_LAB_UNIT,
                ExternalImportWarningReason.AMBIGUOUS_LAB_UNIT,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun transmtfAndOyamaUseCompoundDoseButNoMtfUsesActiveEquivalentDose() {
        val transmtf = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "t-inj", "timeH": 1, "route": "injection", "ester": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        ).medicationDoses.single()
        val oyama = parser.parse(
            """
            {
              "doseTemplates": [],
              "events": [
                { "id": "o-inj", "timeH": 1, "route": "injection", "ester": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        ).medicationDoses.single()
        val noMtf = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                { "id": "n-inj", "timeH": 1, "route": "injection", "compound": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        ).medicationDoses.single()
        val ratio = requireNotNull(
            DoseInstructionCalculator.e2EquivalenceRatio(MedicationKey.ESTRADIOL_VALERATE)
        )

        assertEquals(5.0, assertIs<MedicinePreparation.ImportedInjection>(transmtf.medicineIdentity!!.preparation).administeredMg, 1e-9)
        assertEquals(5.0 * ratio, transmtf.equivalentE2Mg!!, 1e-9)
        assertEquals(5.0, assertIs<MedicinePreparation.ImportedInjection>(oyama.medicineIdentity!!.preparation).administeredMg, 1e-9)
        assertEquals(5.0 * ratio, oyama.equivalentE2Mg!!, 1e-9)
        assertEquals(5.0 / ratio, assertIs<MedicinePreparation.ImportedInjection>(noMtf.medicineIdentity!!.preparation).administeredMg, 1e-9)
        assertEquals(5.0, noMtf.equivalentE2Mg!!, 1e-9)
    }

    @Test
    fun mapsInjectionAndGelToImportedPreparationsAndCanonicalizesNumericIdentity() {
        val result = parser.parse(
            """
            {
              "gelProducts": [{ "id": "gel" }],
              "events": [
                { "id": "inj-a", "timeH": 1, "route": "injection", "ester": "EV", "doseMG": 5 },
                { "id": "inj-b", "timeH": 2, "route": "injection", "ester": "EV", "doseMG": 5.0 },
                { "id": "inj-c", "timeH": 3, "route": "injection", "ester": "EV", "doseMG": 5.000 },
                { "id": "gel-a", "timeH": 4, "route": "gel", "ester": "E2", "doseMG": 0.75 }
              ]
            }
            """.trimIndent()
        )

        val injectionIdentities = result.medicationDoses.take(3).map { dose ->
            assertIs<MedicinePreparation.ImportedInjection>(dose.medicineIdentity!!.preparation)
            dose.medicineIdentity.identityKey
        }
        assertEquals(1, injectionIdentities.toSet().size)
        assertEquals("E|transmtf|INJECTION|ESTRADIOL_VALERATE|mg:5", injectionIdentities.first())
        assertIs<MedicinePreparation.ImportedGel>(result.medicationDoses.last().medicineIdentity!!.preparation)
    }

    @Test
    fun oyamaNestedModesImportsOnlyTransfemDoses() {
        val result = parser.parse(
            """
            {
              "mode": "transmasc",
              "modes": {
                "transfem": {
                  "events": [
                    { "id": "tf-e2", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 2 }
                  ]
                },
                "transmasc": {
                  "events": [
                    { "id": "tm-e2", "timeH": 2, "route": "oral", "ester": "E2", "doseMG": 3 }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.OYAMA, result.sourceApp)
        assertEquals(listOf("tf-e2"), result.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun oyamaNestedLabsUseOnlyTransfemE2AndTransmascT() {
        val result = parser.parse(
            """
            {
              "modes": {
                "transfem": {
                  "labResults": [
                    { "id": "tf-e2", "timeH": 1, "unit": "pg/ml", "value": 120 }
                  ]
                },
                "transmasc": {
                  "labResults": [
                    { "id": "tm-t", "timeH": 2, "unit": "ng/dl", "value": 450 }
                  ]
                },
                "maintenance": {
                  "labResults": [
                    { "id": "unknown-e2", "timeH": 3, "unit": "pg/ml", "value": 99 }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("tf-e2", "tm-t"),
            result.labResults.map { lab -> lab.provenance.externalId },
        )
        assertEquals(listOf(BloodAnalyteKey.E2, BloodAnalyteKey.T), result.labResults.map { lab -> lab.analyteKey })
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun noMtfRecordOnlySupportedAntiandrogensImportAndFinasterideDutasterideSkip() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                { "id": "spiro", "timeH": 1, "route": "oral", "recordOnlyMedication": "spironolactone", "doseMG": 100 },
                { "id": "bica", "timeH": 2, "route": "oral", "recordOnlyMedication": "bicalutamide", "doseMG": 50 },
                { "id": "cpa", "timeH": 3, "route": "oral", "recordOnlyMedication": "cpa", "doseMG": 12.5 },
                { "id": "fin", "timeH": 4, "route": "oral", "recordOnlyMedication": "finasteride", "doseMG": 1 },
                { "id": "dut", "timeH": 5, "route": "oral", "recordOnlyMedication": "dutasteride", "doseMG": 0.5 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("spiro", "bica", "cpa"), result.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(
            listOf(
                MedicineSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                MedicineSelection.Catalog(MedicationKey.BICALUTAMIDE),
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
            ),
            result.medicationDoses.map { dose -> dose.medicineIdentity!!.selection },
        )
        assertTrue(result.medicationDoses.all { dose -> dose.category == MedicationCategory.ANTIANDROGEN })
        assertEquals(
            listOf(
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
            ),
            result.warningReasons(),
        )
    }

    @Test
    fun unsupportedNoMtfAntiandrogenCompoundsSkip() {
        val result = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                {
                  "id": "unknown-aa",
                  "timeH": 1,
                  "route": "oral",
                  "category": "antiAndrogen",
                  "compound": "mystery blocker",
                  "doseMG": 10
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), result.medicationDoses)
        assertEquals(listOf(ExternalImportWarningReason.UNSUPPORTED_COMPOUND), result.warningReasons())
    }

    @Test
    fun labRowsCanUseConcValue() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "labResults": [
                { "id": "conc-lab", "timeH": 1, "unit": "pg/ml", "concValue": 123.4 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.labResults.size)
        assertEquals("conc-lab", result.labResults.single().provenance.externalId)
        assertEquals(123.4, result.labResults.single().value, 1e-9)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun exportMetadataCanComeFromMetaObject() {
        val rootMetaResult = parser.parse(
            """
            {
              "meta": {
                "version": "root-meta-v1",
                "exportedAt": "2026-06-10T00:00:00Z"
              },
              "gelProducts": [],
              "events": [
                { "id": "dose", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 1 }
              ]
            }
            """.trimIndent()
        )
        val contentMetaResult = parser.parse(
            """
            {
              "data": {
                "meta": {
                  "version": "content-meta-v1",
                  "exportedAt": "2026-06-11T00:00:00Z"
                },
                "hrtRecorder": true,
                "events": [
                  { "id": "dose", "timeH": 1, "route": "oral", "compound": "E2", "doseMG": 1 }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals("root-meta-v1", rootMetaResult.exportVersion)
        assertEquals("2026-06-10T00:00:00Z", rootMetaResult.exportedAt)
        assertEquals("content-meta-v1", contentMetaResult.exportVersion)
        assertEquals("2026-06-11T00:00:00Z", contentMetaResult.exportedAt)
    }

    @Test
    fun nonObjectMedicationAndLabRowsAddMalformedWarnings() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                "not a dose row",
                { "id": "dose", "timeH": 1, "route": "oral", "ester": "E2", "doseMG": 1 }
              ],
              "labResults": [
                42,
                { "id": "lab", "timeH": 2, "unit": "pg/ml", "value": 100 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("dose"), result.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(listOf("lab"), result.labResults.map { lab -> lab.provenance.externalId })
        assertEquals(
            listOf(
                ExternalImportWarningReason.MALFORMED_ROW,
                ExternalImportWarningReason.MALFORMED_ROW,
            ),
            result.warningReasons(),
        )
        assertEquals(listOf(0, 0), result.warnings.map { warning -> warning.rowIndex })
    }

    @Test
    fun noMtfTestosteroneAndUnknownCategoriesSkipBeforeCompoundMapping() {
        val testosterone = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                {
                  "id": "t-row",
                  "timeH": 1,
                  "route": "oral",
                  "category": "testosterone",
                  "compound": "E2",
                  "doseMG": 2
                }
              ]
            }
            """.trimIndent()
        )
        val unknown = parser.parse(
            """
            {
              "hrtRecorder": true,
              "events": [
                {
                  "id": "unknown-category",
                  "timeH": 1,
                  "route": "oral",
                  "category": "vitamins",
                  "compound": "E2",
                  "doseMG": 2
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), testosterone.medicationDoses)
        assertEquals(listOf(ExternalImportWarningReason.UNSUPPORTED_CATEGORY), testosterone.warningReasons())
        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), unknown.medicationDoses)
        assertEquals(listOf(ExternalImportWarningReason.UNSUPPORTED_CATEGORY), unknown.warningReasons())
    }

    @Test
    fun transmtfAndOyamaSupportedAntiandrogensImportAndUnsupportedSkip() {
        val transmtf = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "cpa", "timeH": 1, "route": "oral", "ester": "CPA", "doseMG": 12.5 },
                { "id": "bica", "timeH": 2, "route": "oral", "ester": "bicalutamide", "doseMG": 50 },
                { "id": "fin", "timeH": 3, "route": "oral", "ester": "finasteride", "doseMG": 1 }
              ]
            }
            """.trimIndent()
        )
        val oyama = parser.parse(
            """
            {
              "doseTemplates": [],
              "events": [
                { "id": "oyama-cpa", "timeH": 1, "route": "oral", "ester": "CPA", "doseMG": 10 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("cpa", "bica"), transmtf.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(
            listOf(
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
                MedicineSelection.Catalog(MedicationKey.BICALUTAMIDE),
            ),
            transmtf.medicationDoses.map { dose -> dose.medicineIdentity!!.selection },
        )
        assertTrue(transmtf.medicationDoses.all { dose -> dose.category == MedicationCategory.ANTIANDROGEN })
        assertEquals(listOf(ExternalImportWarningReason.UNSUPPORTED_COMPOUND), transmtf.warningReasons())

        assertEquals(listOf("oyama-cpa"), oyama.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(
            MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
            oyama.medicationDoses.single().medicineIdentity!!.selection,
        )
        assertEquals(MedicationCategory.ANTIANDROGEN, oyama.medicationDoses.single().category)
    }

    @Test
    fun estrogenRouteCompoundCompatibilityIsEnforced() {
        val result = parser.parse(
            """
            {
              "gelProducts": [],
              "events": [
                { "id": "oral-ec", "timeH": 1, "route": "oral", "ester": "EC", "doseMG": 2 },
                { "id": "oral-en", "timeH": 2, "route": "sublingual", "ester": "EN", "doseMG": 2 },
                { "id": "inj-ec", "timeH": 3, "route": "injection", "ester": "EC", "doseMG": 5 },
                { "id": "gel-ev", "timeH": 4, "route": "gel", "ester": "EV", "doseMG": 1 },
                {
                  "id": "patch-ev",
                  "timeH": 5,
                  "route": "patchApply",
                  "ester": "EV",
                  "doseMG": 0,
                  "extras": { "releaseRateUGPerDay": 100 }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("inj-ec"), result.medicationDoses.map { dose -> dose.provenance.externalId })
        assertEquals(MedicationApplicationType.INJECTION, result.medicationDoses.single().applicationType)
        assertEquals(
            MedicineSelection.Custom("External tracker"),
            result.medicationDoses.single().medicineIdentity!!.selection,
        )
        assertEquals(
            listOf(
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
            ),
            result.warningReasons(),
        )
        assertEquals(
            listOf(
                ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
                ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
                ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
                ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
            ),
            result.warnings.map(ExternalImportWarning::messageKey),
        )
    }

    @Test
    fun oyamaTransmascTestosteroneLabSupportsNgMl() {
        val result = parser.parse(
            """
            {
              "modes": {
                "transmasc": {
                  "labResults": [
                    { "id": "t-ng-ml", "timeH": 1, "unit": "ng/ml", "value": 5.2 }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.labResults.size)
        assertEquals("t-ng-ml", result.labResults.single().provenance.externalId)
        assertEquals(BloodAnalyteKey.T, result.labResults.single().analyteKey)
        assertEquals(BloodUnitKey.NG_ML, result.labResults.single().unitKey)
        assertEquals(emptyList<ExternalImportWarningReason>(), result.warningReasons())
    }

    @Test
    fun transmtfCompatibleFallbackEmitsSourceWarning() {
        val result = parser.parse(
            """
            {
              "events": [
                { "id": "flat-ev", "timeH": 3, "route": "injection", "ester": "EV", "doseMG": 5 }
              ]
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE, result.sourceApp)
        assertTrue(
            result.warnings.any { warning ->
                warning.message.contains("fallback", ignoreCase = true) ||
                        warning.message.contains("not positively identified", ignoreCase = true)
            }
        )
    }

    @Test
    fun modeAndModesKeysIdentifyOyamaRegardlessOfValue() {
        val modeResult = parser.parse(
            """
            {
              "mode": "maintenance",
              "events": []
            }
            """.trimIndent()
        )
        val modesResult = parser.parse(
            """
            {
              "modes": {
                "maintenance": {
                  "events": []
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(ExternalTrackerSourceApp.OYAMA, modeResult.sourceApp)
        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), modeResult.medicationDoses)
        assertEquals(emptyList<ExternalImportCandidate.LabResult>(), modeResult.labResults)
        assertEquals(ExternalTrackerSourceApp.OYAMA, modesResult.sourceApp)
        assertEquals(emptyList<ExternalImportCandidate.MedicationDose>(), modesResult.medicationDoses)
        assertEquals(emptyList<ExternalImportCandidate.LabResult>(), modesResult.labResults)
    }

    private fun ExternalImportParseResult.warningReasons(): List<ExternalImportWarningReason> {
        return warnings.map { warning -> warning.reason }
    }

    private fun assertFatal(json: String, messagePart: String) {
        val error = assertThrows(ExternalImportFatalException::class.java) {
            parser.parse(json)
        }
        assertNotNull(error.message)
        assertTrue(error.message!!.contains(messagePart, ignoreCase = true))
    }

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue("Expected ${T::class.java.name}, got ${value?.javaClass?.name}", value is T)
        return value as T
    }
}
