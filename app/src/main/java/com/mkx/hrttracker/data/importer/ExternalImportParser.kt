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
import com.squareup.moshi.Moshi
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlin.math.round

class ExternalImportParser @Inject constructor() {
    private val moshi = Moshi.Builder().build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    fun parse(json: String): ExternalImportParseResult {
        val rootValue = try {
            anyAdapter.fromJson(json)
        } catch (exception: Exception) {
            throw ExternalImportFatalException("Invalid or unreadable external import JSON.")
        }
        val root = rootValue.asMap()
            ?: throw ExternalImportFatalException("External import JSON must be a top-level object.")
        val sourceApp = detectSource(root)
        val content = contentMapForSource(root, sourceApp)
        val warnings = mutableListOf<ExternalImportWarning>()
        if (sourceApp == ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.SOURCE_FALLBACK,
                externalId = null,
                rowIndex = null,
                message = "Source was not positively identified; using Transmtf-compatible fallback semantics.",
            )
        }
        val seenMedicationExternalIds = mutableSetOf<String>()
        val seenLabExternalIds = mutableSetOf<String>()
        val seenLabPanelAnalytes = mutableSetOf<Pair<Long, BloodAnalyteKey>>()

        val medicationDoses = medicationRows(sourceApp, content).mapNotNull { row ->
            parseMedicationDose(
                sourceApp = sourceApp,
                row = row,
                warnings = warnings,
                seenExternalIds = seenMedicationExternalIds,
            )
        }
        val labResults = labRows(sourceApp, content).mapNotNull { row ->
            parseLabResult(
                sourceApp = sourceApp,
                row = row,
                warnings = warnings,
                seenExternalIds = seenLabExternalIds,
                seenPanelAnalytes = seenLabPanelAnalytes,
            )
        }

        return ExternalImportParseResult(
            sourceApp = sourceApp,
            exportVersion = text(root["exportVersion"]) ?: text(root["version"])
                ?: metadataValue(root, "version") ?: text(content["exportVersion"])
                ?: text(content["version"]) ?: metadataValue(content, "version"),
            exportedAt = text(root["exportedAt"]) ?: metadataValue(root, "exportedAt")
                ?: text(content["exportedAt"]) ?: metadataValue(content, "exportedAt"),
            medicationDoses = medicationDoses,
            labResults = labResults,
            warnings = warnings,
        )
    }

    private fun detectSource(root: Map<String, Any?>): ExternalTrackerSourceApp {
        if (boolean(root["encrypted"]) == true) {
            throw ExternalImportFatalException("Encrypted Oyama exports are not supported.")
        }

        val data = root["data"].asMap()
        if (root.containsKey("hrtRecorder") || data?.containsKey("hrtRecorder") == true) {
            return ExternalTrackerSourceApp.NOMTF
        }
        if (eventRowsForDetection(root).any { row -> row.hasAny("category", "compound", "recordOnlyMedication") }) {
            return ExternalTrackerSourceApp.NOMTF
        }
        if (isOyamaShape(root)) {
            return ExternalTrackerSourceApp.OYAMA
        }
        if (root.containsKey("gelProducts")) {
            return ExternalTrackerSourceApp.TRANSMTF
        }
        if (root["events"].asList().orEmpty().any { row -> row.asMap()?.containsKey("ester") == true }) {
            return ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE
        }

        throw ExternalImportFatalException("Unknown external import JSON shape.")
    }

    private fun isOyamaShape(root: Map<String, Any?>): Boolean {
        if (root.containsKey("doseTemplates") && !root.containsKey("gelProducts")) {
            return true
        }
        if (root.containsKey("mode")) {
            return true
        }
        if (root.containsKey("modes")) {
            return true
        }
        return false
    }

    private fun contentMapForSource(
        root: Map<String, Any?>,
        sourceApp: ExternalTrackerSourceApp,
    ): Map<String, Any?> {
        val data = root["data"].asMap()
        if (
            sourceApp == ExternalTrackerSourceApp.NOMTF &&
            data != null &&
            (
                    data.containsKey("hrtRecorder") ||
                            eventRowsForDetection(data).any { row ->
                                row.hasAny("category", "compound", "recordOnlyMedication")
                            }
                    )
        ) {
            return data
        }
        return root
    }

    private fun eventRowsForDetection(root: Map<String, Any?>): List<Map<String, Any?>> {
        val rootEvents = root["events"].asList().orEmpty().mapNotNull { row -> row.asMap() }
        val dataEvents = root["data"].asMap()
            ?.get("events")
            .asList()
            .orEmpty()
            .mapNotNull { row -> row.asMap() }
        return rootEvents + dataEvents
    }

    private fun medicationRows(
        sourceApp: ExternalTrackerSourceApp,
        content: Map<String, Any?>,
    ): List<MedicationRow> {
        if (sourceApp == ExternalTrackerSourceApp.OYAMA) {
            val modes = content["modes"].asMap()
            if (modes != null) {
                val modeName = "transfem"
                return modes.valueByKey(modeName)
                    .asMap()
                    ?.get("events")
                    .asList()
                    .orEmpty()
                    .mapIndexed { index, rawRow ->
                        MedicationRow(row = rawRow.asMap(), rowIndex = index, modeName = modeName)
                    }
            }
        }
        return content["events"].asList()
            .orEmpty()
            .mapIndexed { index, rawRow ->
                MedicationRow(row = rawRow.asMap(), rowIndex = index, modeName = null)
            }
    }

    private fun labRows(
        sourceApp: ExternalTrackerSourceApp,
        content: Map<String, Any?>,
    ): List<LabRow> {
        if (sourceApp == ExternalTrackerSourceApp.OYAMA) {
            val modes = content["modes"].asMap()
            if (modes != null) {
                return listOf(
                    "transfem" to BloodAnalyteKey.E2,
                    "transmasc" to BloodAnalyteKey.T,
                ).flatMap { (modeName, analyte) ->
                    modes.valueByKey(modeName)
                        .asMap()
                        ?.get("labResults")
                        .asList()
                        .orEmpty()
                        .mapIndexed { index, rawRow ->
                            LabRow(
                                row = rawRow.asMap(),
                                rowIndex = index,
                                modeName = modeName,
                                sourceAnalyte = analyte,
                            )
                        }
                }
            }
            return content["labResults"].asList()
                .orEmpty()
                .mapIndexed { index, rawRow ->
                    LabRow(row = rawRow.asMap(), rowIndex = index, modeName = null, sourceAnalyte = BloodAnalyteKey.E2)
                }
        }

        return content["labResults"].asList()
            .orEmpty()
            .mapIndexed { index, rawRow ->
                LabRow(
                    row = rawRow.asMap(),
                    rowIndex = index,
                    modeName = null,
                    sourceAnalyte = if (sourceApp == ExternalTrackerSourceApp.NOMTF) null else BloodAnalyteKey.E2,
                )
            }
    }

    private fun parseMedicationDose(
        sourceApp: ExternalTrackerSourceApp,
        row: MedicationRow,
        warnings: MutableList<ExternalImportWarning>,
        seenExternalIds: MutableSet<String>,
    ): ExternalImportCandidate.MedicationDose? {
        val fields = row.row
        if (fields == null) {
            warnings.addMalformed(null, row.rowIndex, "Skipped non-object medication row.")
            return null
        }
        val externalId = text(fields["id"]) ?: text(fields["uuid"]) ?: text(fields["key"])
        if (externalId.isNullOrBlank()) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.MISSING_EXTERNAL_ID,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped medication row without an external id.",
            )
            return null
        }
        if (!seenExternalIds.add(externalId)) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.DUPLICATE_EXTERNAL_ID,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped duplicate medication row external id.",
            )
            return null
        }

        val timeH = number(fields["timeH"])
        if (timeH == null || !timeH.isFinite()) {
            warnings.addMalformed(externalId, row.rowIndex, "Skipped medication row with invalid timeH.")
            return null
        }
        val route = routeFrom(text(fields["route"]) ?: text(fields["type"]) ?: text(fields["applicationType"]))
        if (route == null) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.UNSUPPORTED_ROUTE,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped medication row with unsupported route.",
            )
            return null
        }

        if (sourceApp == ExternalTrackerSourceApp.NOMTF) {
            when (val noMtfResult = parseNoMtfAntiandrogenOrRecordOnly(
                sourceApp = sourceApp,
                row = row,
                fields = fields,
                externalId = externalId,
                timeH = timeH,
                route = route,
                warnings = warnings,
            )) {
                NoMtfSpecialResult.Continue -> Unit
                NoMtfSpecialResult.Skip -> return null
                is NoMtfSpecialResult.Dose -> return noMtfResult.dose
            }
        }

        if (route == ExternalRoute.PATCH_REMOVE) {
            return patchRemoveCandidate(sourceApp, externalId, timeH)
        }

        val compoundText = compoundTextFor(sourceApp, fields)
        if (sourceApp != ExternalTrackerSourceApp.NOMTF) {
            val antiandrogenKey = supportedNonNoMtfAntiandrogenKey(sourceApp, compoundText)
            if (antiandrogenKey != null) {
                return parseAntiandrogenDose(
                    sourceApp = sourceApp,
                    externalId = externalId,
                    timeH = timeH,
                    route = route,
                    medicationKey = antiandrogenKey,
                    row = row,
                    fields = fields,
                    warnings = warnings,
                )
            }
            if (antiandrogenKey(compoundText) != null || compoundText.isFinasterideOrDutasteride()) {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped unsupported antiandrogen.",
                )
                return null
            }
        }
        val compound = estrogenCompound(compoundText)
        when (compound) {
            CompoundMapping.UnsupportedEstrogen,
            CompoundMapping.UnsupportedOther,
            null,
            -> {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped medication row with unsupported estrogen compound.",
                )
                return null
            }

            CompoundMapping.Testosterone -> {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_CATEGORY,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped testosterone medication row.",
                )
                return null
            }

            is CompoundMapping.Estrogen -> {
                return parseEstrogenDose(
                    sourceApp = sourceApp,
                    externalId = externalId,
                    timeH = timeH,
                    route = route,
                    compoundKey = compound.key,
                    row = row,
                    fields = fields,
                    warnings = warnings,
                )
            }
        }
    }

    private fun parseNoMtfAntiandrogenOrRecordOnly(
        sourceApp: ExternalTrackerSourceApp,
        row: MedicationRow,
        fields: Map<String, Any?>,
        externalId: String,
        timeH: Double,
        route: ExternalRoute,
        warnings: MutableList<ExternalImportWarning>,
    ): NoMtfSpecialResult {
        val recordOnlyMedication = text(fields["recordOnlyMedication"])
        if (!recordOnlyMedication.isNullOrBlank()) {
            if (recordOnlyMedication.isFinasterideOrDutasteride()) {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped unsupported record-only antiandrogen.",
                )
                return NoMtfSpecialResult.Skip
            }
            val medicationKey = antiandrogenKey(recordOnlyMedication)
            if (medicationKey == null) {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped unsupported record-only medication from NoMTF.",
                )
                return NoMtfSpecialResult.Skip
            }
            return parseAntiandrogenDose(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                route = route,
                medicationKey = medicationKey,
                row = row,
                fields = fields,
                warnings = warnings,
            ).toNoMtfResult()
        }

        val category = text(fields["category"]).normalizedToken()
        when (category) {
            null,
            "",
            "estradiol",
            -> return NoMtfSpecialResult.Continue

            "cpa" -> {
                return parseAntiandrogenDose(
                    sourceApp = sourceApp,
                    externalId = externalId,
                    timeH = timeH,
                    route = route,
                    medicationKey = MedicationKey.CYPROTERONE_ACETATE,
                    row = row,
                    fields = fields,
                    warnings = warnings,
                ).toNoMtfResult()
            }

            "antiandrogen" -> {
                val compound = compoundTextFor(sourceApp, fields)
                if (compound.isFinasterideOrDutasteride()) {
                    warnings.addWarning(
                        reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                        externalId = externalId,
                        rowIndex = row.rowIndex,
                        message = "Skipped unsupported antiandrogen.",
                    )
                    return NoMtfSpecialResult.Skip
                }
                val medicationKey = antiandrogenKey(compound)
                if (medicationKey == null) {
                    warnings.addWarning(
                        reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                        externalId = externalId,
                        rowIndex = row.rowIndex,
                        message = "Skipped unsupported antiandrogen.",
                    )
                    return NoMtfSpecialResult.Skip
                }
                return parseAntiandrogenDose(
                    sourceApp = sourceApp,
                    externalId = externalId,
                    timeH = timeH,
                    route = route,
                    medicationKey = medicationKey,
                    row = row,
                    fields = fields,
                    warnings = warnings,
                ).toNoMtfResult()
            }

            "testosterone" -> {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_CATEGORY,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped testosterone medication row.",
                )
                return NoMtfSpecialResult.Skip
            }

            else -> {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_CATEGORY,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped medication row with unsupported NoMTF category.",
                )
                return NoMtfSpecialResult.Skip
            }
        }
    }

    private fun parseAntiandrogenDose(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        route: ExternalRoute,
        medicationKey: MedicationKey,
        row: MedicationRow,
        fields: Map<String, Any?>,
        warnings: MutableList<ExternalImportWarning>,
    ): ExternalImportCandidate.MedicationDose? {
        val applicationType = when (route) {
            ExternalRoute.ORAL -> MedicationApplicationType.ORAL
            ExternalRoute.SUBLINGUAL -> MedicationApplicationType.SUBLINGUAL
            else -> {
                warnings.addWarning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_ROUTE,
                    externalId = externalId,
                    rowIndex = row.rowIndex,
                    message = "Skipped antiandrogen row with unsupported route.",
                )
                return null
            }
        }
        val doseMg = positiveDoseMg(fields["doseMG"])
            ?: positiveDoseMg(fields["doseMg"])
            ?: positiveDoseMg(fields["dose"])
        if (doseMg == null) {
            warnings.addMalformed(externalId, row.rowIndex, "Skipped antiandrogen row with invalid dose.")
            return null
        }

        val selection = MedicineSelection.Catalog(medicationKey)
        val preparation = MedicinePreparation.Pill(doseMg)
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val compoundIdentity = medicationKey.name
        val identity = ImportedMedicineIdentity(
            identityKey = MedicineIdentityKey.external(
                sourceApp = sourceApp.storageValue,
                applicationType = applicationType,
                compound = compoundIdentity,
                doseKey = doseKey("mg", doseMg),
            ),
            displayName = medicationKey.displayName(),
            category = MedicationCategory.ANTIANDROGEN,
            selection = selection,
            preparation = preparation,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
        )
        return ExternalImportCandidate.MedicationDose(
            provenance = ExternalImportProvenance(sourceApp, externalId),
            appliedAtEpochMillis = epochMillisFromTimeH(timeH),
            appliedAtTimeZoneId = ZoneId.systemDefault().id,
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            medicineIdentity = identity,
            equivalentE2Mg = null,
            previewNotes = emptyList(),
        )
    }

    private fun parseEstrogenDose(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        route: ExternalRoute,
        compoundKey: MedicationKey,
        row: MedicationRow,
        fields: Map<String, Any?>,
        warnings: MutableList<ExternalImportWarning>,
    ): ExternalImportCandidate.MedicationDose? {
        val extras = fields["extras"].asMap().orEmpty()
        val releaseRateMcgPerDay = extras.numberByKey("releaseRateUGPerDay")
            ?: extras.numberByKey("releaseRateMcgPerDay")
            ?: extras.numberByKey("releaseRate")
        val doseMg = number(fields["doseMG"])
            ?: number(fields["doseMg"])
            ?: number(fields["dose"])
            ?: number(fields["amountMg"])

        if (!isEstrogenCompatibleWithRoute(route, compoundKey)) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.UNSUPPORTED_COMPOUND,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped estrogen row with a compound unsupported for its route.",
            )
            return null
        }

        if (route == ExternalRoute.PATCH_APPLY && releaseRateMcgPerDay != null && releaseRateMcgPerDay > 0.0) {
            return patchApplyCandidate(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    releaseRateMcgPerDay,
                ),
                doseKey = doseKey("rate", releaseRateMcgPerDay),
            )
        }

        if (doseMg == null || !doseMg.isFinite() || doseMg <= 0.0) {
            warnings.addMalformed(externalId, row.rowIndex, "Skipped estrogen row with invalid dose.")
            return null
        }

        val equivalentE2Mg = equivalentE2Mg(
            sourceApp = sourceApp,
            route = route,
            compoundKey = compoundKey,
            doseMg = doseMg,
        )
        val administeredDoseMg = administeredDoseMg(
            sourceApp = sourceApp,
            route = route,
            compoundKey = compoundKey,
            doseMg = doseMg,
        )

        return when (route) {
            ExternalRoute.ORAL,
            ExternalRoute.SUBLINGUAL,
            -> pillCandidate(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                route = route,
                compoundKey = compoundKey,
                administeredDoseMg = administeredDoseMg,
                equivalentE2Mg = equivalentE2Mg,
            )

            ExternalRoute.INJECTION -> injectionCandidate(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                compoundKey = compoundKey,
                administeredDoseMg = administeredDoseMg,
                equivalentE2Mg = equivalentE2Mg,
            )

            ExternalRoute.GEL -> gelCandidate(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                appliedEstradiolMg = doseMg,
                warnings = warnings,
                row = row,
                extras = extras,
            )

            ExternalRoute.PATCH_APPLY -> patchApplyCandidate(
                sourceApp = sourceApp,
                externalId = externalId,
                timeH = timeH,
                specification = MedicinePreparation.PatchSpecification.TotalMg(doseMg),
                doseKey = doseKey("totalmg", doseMg),
            )

            ExternalRoute.PATCH_REMOVE -> patchRemoveCandidate(sourceApp, externalId, timeH)
        }
    }

    private fun pillCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        route: ExternalRoute,
        compoundKey: MedicationKey,
        administeredDoseMg: Double,
        equivalentE2Mg: Double?,
    ): ExternalImportCandidate.MedicationDose {
        val applicationType = when (route) {
            ExternalRoute.ORAL -> MedicationApplicationType.ORAL
            ExternalRoute.SUBLINGUAL -> MedicationApplicationType.SUBLINGUAL
            else -> error("Unsupported pill route $route")
        }
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val preparation = MedicinePreparation.Pill(administeredDoseMg)
        val identity = ImportedMedicineIdentity(
            identityKey = MedicineIdentityKey.external(
                sourceApp = sourceApp.storageValue,
                applicationType = applicationType,
                compound = compoundKey.name,
                doseKey = doseKey("mg", administeredDoseMg),
            ),
            displayName = compoundKey.displayName(),
            category = MedicationCategory.ESTRADIOL,
            selection = MedicineSelection.Catalog(compoundKey),
            preparation = preparation,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
        )
        return medicationCandidate(
            sourceApp = sourceApp,
            externalId = externalId,
            timeH = timeH,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            medicineIdentity = identity,
            equivalentE2Mg = equivalentE2Mg,
        )
    }

    private fun injectionCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        compoundKey: MedicationKey,
        administeredDoseMg: Double,
        equivalentE2Mg: Double?,
    ): ExternalImportCandidate.MedicationDose {
        val doseInstruction = DoseInstruction.WholeUnit
        val preparation = MedicinePreparation.ImportedInjection(
            administeredMg = administeredDoseMg,
            ester = compoundKey,
        )
        val identity = ImportedMedicineIdentity(
            identityKey = MedicineIdentityKey.external(
                sourceApp = sourceApp.storageValue,
                applicationType = MedicationApplicationType.INJECTION,
                compound = compoundKey.name,
                doseKey = doseKey("mg", administeredDoseMg),
            ),
            displayName = EXTERNAL_TRACKER_DISPLAY_NAME,
            category = MedicationCategory.ESTRADIOL,
            selection = MedicineSelection.Custom(EXTERNAL_TRACKER_DISPLAY_NAME),
            preparation = preparation,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = doseInstruction,
        )
        return medicationCandidate(
            sourceApp = sourceApp,
            externalId = externalId,
            timeH = timeH,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = doseInstruction,
            medicineIdentity = identity,
            equivalentE2Mg = equivalentE2Mg,
        )
    }

    private fun gelCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        appliedEstradiolMg: Double,
        warnings: MutableList<ExternalImportWarning>,
        row: MedicationRow,
        extras: Map<String, Any?>,
    ): ExternalImportCandidate.MedicationDose {
        val doseInstruction = DoseInstruction.WholeUnit
        val preparation = MedicinePreparation.ImportedGel(appliedEstradiolMg)
        val identity = ImportedMedicineIdentity(
            identityKey = MedicineIdentityKey.external(
                sourceApp = sourceApp.storageValue,
                applicationType = MedicationApplicationType.GEL,
                compound = MedicationKey.ESTRADIOL.name,
                doseKey = doseKey("mg", appliedEstradiolMg),
            ),
            displayName = EXTERNAL_TRACKER_DISPLAY_NAME,
            category = MedicationCategory.ESTRADIOL,
            selection = MedicineSelection.Custom(EXTERNAL_TRACKER_DISPLAY_NAME),
            preparation = preparation,
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = doseInstruction,
        )
        val previewNotes = gelPreviewNotes(extras)
        if (previewNotes.isNotEmpty()) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.GEL_METADATA_PREVIEW_ONLY,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Gel metadata is available for preview only and does not affect medicine identity.",
            )
        }
        return medicationCandidate(
            sourceApp = sourceApp,
            externalId = externalId,
            timeH = timeH,
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = doseInstruction,
            medicineIdentity = identity,
            equivalentE2Mg = appliedEstradiolMg,
            previewNotes = previewNotes,
        )
    }

    private fun patchApplyCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        specification: MedicinePreparation.PatchSpecification,
        doseKey: String,
    ): ExternalImportCandidate.MedicationDose {
        val doseInstruction = DoseInstruction.WholeUnit
        val preparation = MedicinePreparation.Patch(specification)
        val identity = ImportedMedicineIdentity(
            identityKey = MedicineIdentityKey.external(
                sourceApp = sourceApp.storageValue,
                applicationType = MedicationApplicationType.PATCH_ON,
                compound = MedicationKey.ESTRADIOL_PATCH.name,
                doseKey = doseKey,
            ),
            displayName = MedicationKey.ESTRADIOL_PATCH.displayName(),
            category = MedicationCategory.ESTRADIOL,
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            preparation = preparation,
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = doseInstruction,
        )
        return medicationCandidate(
            sourceApp = sourceApp,
            externalId = externalId,
            timeH = timeH,
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = doseInstruction,
            medicineIdentity = identity,
            equivalentE2Mg = null,
        )
    }

    private fun patchRemoveCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
    ): ExternalImportCandidate.MedicationDose {
        return ExternalImportCandidate.MedicationDose(
            provenance = ExternalImportProvenance(sourceApp, externalId),
            appliedAtEpochMillis = epochMillisFromTimeH(timeH),
            appliedAtTimeZoneId = ZoneId.systemDefault().id,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            medicineIdentity = null,
            equivalentE2Mg = null,
            previewNotes = emptyList(),
        )
    }

    private fun medicationCandidate(
        sourceApp: ExternalTrackerSourceApp,
        externalId: String,
        timeH: Double,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        medicineIdentity: ImportedMedicineIdentity?,
        equivalentE2Mg: Double?,
        previewNotes: List<String> = emptyList(),
    ): ExternalImportCandidate.MedicationDose {
        return ExternalImportCandidate.MedicationDose(
            provenance = ExternalImportProvenance(sourceApp, externalId),
            appliedAtEpochMillis = epochMillisFromTimeH(timeH),
            appliedAtTimeZoneId = ZoneId.systemDefault().id,
            category = MedicationCategory.ESTRADIOL,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            medicineIdentity = medicineIdentity,
            equivalentE2Mg = equivalentE2Mg,
            previewNotes = previewNotes,
        )
    }

    private fun parseLabResult(
        sourceApp: ExternalTrackerSourceApp,
        row: LabRow,
        warnings: MutableList<ExternalImportWarning>,
        seenExternalIds: MutableSet<String>,
        seenPanelAnalytes: MutableSet<Pair<Long, BloodAnalyteKey>>,
    ): ExternalImportCandidate.LabResult? {
        val fields = row.row
        if (fields == null) {
            warnings.addMalformed(null, row.rowIndex, "Skipped non-object lab row.")
            return null
        }
        val externalId = text(fields["id"]) ?: text(fields["uuid"]) ?: text(fields["key"])
        if (externalId.isNullOrBlank()) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.MISSING_EXTERNAL_ID,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped lab row without an external id.",
            )
            return null
        }
        if (!seenExternalIds.add(externalId)) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.DUPLICATE_EXTERNAL_ID,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped duplicate lab row external id.",
            )
            return null
        }

        val timeH = number(fields["timeH"])
        val value = number(fields["concValue"]) ?: number(fields["value"])
        val unit = unitFrom(text(fields["unit"]) ?: text(fields["units"]))
        if (timeH == null || !timeH.isFinite() || value == null || !value.isFinite() || unit == null) {
            warnings.addMalformed(externalId, row.rowIndex, "Skipped malformed lab row.")
            return null
        }

        val analyte = analyteForLab(sourceApp, row, unit)
        if (analyte == null) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.AMBIGUOUS_LAB_UNIT,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped lab row with ambiguous analyte unit.",
            )
            return null
        }

        val panelKey = epochMillisFromTimeH(timeH)
        if (!seenPanelAnalytes.add(panelKey to analyte)) {
            warnings.addWarning(
                reason = ExternalImportWarningReason.DUPLICATE_LAB_RESULT,
                externalId = externalId,
                rowIndex = row.rowIndex,
                message = "Skipped duplicate analyte result in the same panel.",
            )
            return null
        }

        return ExternalImportCandidate.LabResult(
            provenance = ExternalImportProvenance(sourceApp, externalId),
            panelProvenance = ExternalImportPanelProvenance(sourceApp, panelKey),
            collectedAtEpochMillis = panelKey,
            collectedAtTimeZoneId = ZoneId.systemDefault().id,
            analyteKey = analyte,
            unitKey = unit,
            value = value,
        )
    }

    private fun analyteForLab(
        sourceApp: ExternalTrackerSourceApp,
        row: LabRow,
        unit: BloodUnitKey,
    ): BloodAnalyteKey? {
        if (
            sourceApp == ExternalTrackerSourceApp.NOMTF ||
            sourceApp == ExternalTrackerSourceApp.TRANSMTF ||
            sourceApp == ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE
        ) {
            return when (unit) {
                BloodUnitKey.PG_ML,
                BloodUnitKey.PMOL_L,
                -> BloodAnalyteKey.E2

                else -> null
            }
        }
        if (sourceApp == ExternalTrackerSourceApp.OYAMA) {
            return when (row.sourceAnalyte) {
                BloodAnalyteKey.E2 -> when (unit) {
                    BloodUnitKey.PG_ML,
                    BloodUnitKey.PMOL_L,
                    -> BloodAnalyteKey.E2

                    else -> null
                }

                BloodAnalyteKey.T -> when (unit) {
                    BloodUnitKey.NG_DL,
                    BloodUnitKey.NG_ML,
                    BloodUnitKey.NMOL_L,
                    -> BloodAnalyteKey.T

                    else -> null
                }

                else -> null
            }
        }
        return BloodAnalyteKey.E2
    }

    private fun equivalentE2Mg(
        sourceApp: ExternalTrackerSourceApp,
        route: ExternalRoute,
        compoundKey: MedicationKey,
        doseMg: Double,
    ): Double? {
        return when (route) {
            ExternalRoute.ORAL,
            ExternalRoute.SUBLINGUAL,
            ExternalRoute.INJECTION,
            -> if (sourceApp == ExternalTrackerSourceApp.NOMTF) {
                doseMg
            } else {
                doseMg * requireNotNull(DoseInstructionCalculator.e2EquivalenceRatio(compoundKey))
            }

            ExternalRoute.GEL -> doseMg
            ExternalRoute.PATCH_APPLY,
            ExternalRoute.PATCH_REMOVE,
            -> null
        }
    }

    private fun administeredDoseMg(
        sourceApp: ExternalTrackerSourceApp,
        route: ExternalRoute,
        compoundKey: MedicationKey,
        doseMg: Double,
    ): Double {
        if (
            sourceApp == ExternalTrackerSourceApp.NOMTF &&
            route in setOf(ExternalRoute.ORAL, ExternalRoute.SUBLINGUAL, ExternalRoute.INJECTION)
        ) {
            val ratio = requireNotNull(DoseInstructionCalculator.e2EquivalenceRatio(compoundKey))
            return doseMg / ratio
        }
        return doseMg
    }

    private fun compoundTextFor(
        sourceApp: ExternalTrackerSourceApp,
        row: Map<String, Any?>,
    ): String? {
        return if (sourceApp == ExternalTrackerSourceApp.NOMTF) {
            text(row["compound"]) ?: text(row["ester"]) ?: text(row["medicationKey"])
        } else {
            text(row["ester"]) ?: text(row["compound"]) ?: text(row["medicationKey"])
        }
    }

    private fun routeFrom(value: String?): ExternalRoute? {
        return when (value.normalizedToken()) {
            "oral" -> ExternalRoute.ORAL
            "sublingual" -> ExternalRoute.SUBLINGUAL
            "injection",
            "inject",
            "injected",
            -> ExternalRoute.INJECTION

            "gel" -> ExternalRoute.GEL
            "patchapply",
            "patchon",
            -> ExternalRoute.PATCH_APPLY

            "patchremove",
            "patchoff",
            -> ExternalRoute.PATCH_REMOVE

            else -> null
        }
    }

    private fun estrogenCompound(value: String?): CompoundMapping? {
        return when (value.normalizedToken()) {
            "e2",
            "estradiol",
            -> CompoundMapping.Estrogen(MedicationKey.ESTRADIOL)

            "ev",
            "estradiolvalerate",
            -> CompoundMapping.Estrogen(MedicationKey.ESTRADIOL_VALERATE)

            "eb",
            "estradiolbenzoate",
            -> CompoundMapping.Estrogen(MedicationKey.ESTRADIOL_BENZOATE)

            "ec",
            "estradiolcypionate",
            -> CompoundMapping.Estrogen(MedicationKey.ESTRADIOL_CYPIONATE)

            "en",
            "estradiolenanthate",
            -> CompoundMapping.Estrogen(MedicationKey.ESTRADIOL_ENANTHATE)

            "eu",
            "epp",
            -> CompoundMapping.UnsupportedEstrogen

            "t",
            "testosterone",
            -> CompoundMapping.Testosterone

            null,
            "",
            -> null

            else -> CompoundMapping.UnsupportedOther
        }
    }

    private fun antiandrogenKey(value: String?): MedicationKey? {
        return when (value.normalizedToken()) {
            "cpa",
            "cyproterone",
            "cyproteroneacetate",
            -> MedicationKey.CYPROTERONE_ACETATE

            "spiro",
            "spironolactone",
            -> MedicationKey.SPIRONOLACTONE

            "bica",
            "bicalutamide",
            -> MedicationKey.BICALUTAMIDE

            else -> null
        }
    }

    private fun supportedNonNoMtfAntiandrogenKey(
        sourceApp: ExternalTrackerSourceApp,
        value: String?,
    ): MedicationKey? {
        val key = antiandrogenKey(value) ?: return null
        return when (sourceApp) {
            ExternalTrackerSourceApp.OYAMA -> key.takeIf {
                it == MedicationKey.CYPROTERONE_ACETATE
            }

            ExternalTrackerSourceApp.TRANSMTF,
            ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE,
            -> key.takeIf {
                it == MedicationKey.CYPROTERONE_ACETATE ||
                        it == MedicationKey.BICALUTAMIDE
            }

            ExternalTrackerSourceApp.NOMTF -> null
        }
    }

    private fun isEstrogenCompatibleWithRoute(
        route: ExternalRoute,
        compoundKey: MedicationKey,
    ): Boolean {
        return when (route) {
            ExternalRoute.ORAL,
            ExternalRoute.SUBLINGUAL,
            -> compoundKey == MedicationKey.ESTRADIOL ||
                    compoundKey == MedicationKey.ESTRADIOL_VALERATE

            ExternalRoute.INJECTION -> compoundKey in setOf(
                MedicationKey.ESTRADIOL,
                MedicationKey.ESTRADIOL_VALERATE,
                MedicationKey.ESTRADIOL_BENZOATE,
                MedicationKey.ESTRADIOL_CYPIONATE,
                MedicationKey.ESTRADIOL_ENANTHATE,
            )

            ExternalRoute.GEL,
            ExternalRoute.PATCH_APPLY,
            -> compoundKey == MedicationKey.ESTRADIOL

            ExternalRoute.PATCH_REMOVE -> true
        }
    }

    private fun unitFrom(value: String?): BloodUnitKey? {
        return when (value.normalizedToken()) {
            "pgml" -> BloodUnitKey.PG_ML
            "ngml" -> BloodUnitKey.NG_ML
            "ngdl" -> BloodUnitKey.NG_DL
            "pmoll" -> BloodUnitKey.PMOL_L
            "nmoll" -> BloodUnitKey.NMOL_L
            "miul" -> BloodUnitKey.MIU_L
            "miuml" -> BloodUnitKey.MIU_ML
            "iul" -> BloodUnitKey.IU_L
            else -> null
        }
    }

    private fun gelPreviewNotes(extras: Map<String, Any?>): List<String> {
        return listOfNotNull(
            text(extras.valueByKey("product"))?.let { value -> "Product: $value" },
            text(extras.valueByKey("site"))?.let { value -> "Site: $value" },
            text(extras.valueByKey("area"))?.let { value -> "Area: $value" },
            text(extras.valueByKey("wash"))?.let { value -> "Wash: $value" },
        )
    }

    private fun positiveDoseMg(value: Any?): Double? {
        val dose = number(value) ?: return null
        return dose.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun doseKey(prefix: String, value: Double): String =
        "$prefix:${MedicineIdentityKey.canonicalDouble(value)}"

    private fun epochMillisFromTimeH(value: Double): Long =
        round(value * 3_600_000.0).toLong()

    private fun MutableList<ExternalImportWarning>.addMalformed(
        externalId: String?,
        rowIndex: Int?,
        message: String,
    ) {
        addWarning(
            reason = ExternalImportWarningReason.MALFORMED_ROW,
            externalId = externalId,
            rowIndex = rowIndex,
            message = message,
        )
    }

    private fun MutableList<ExternalImportWarning>.addWarning(
        reason: ExternalImportWarningReason,
        externalId: String?,
        rowIndex: Int?,
        message: String,
    ) {
        add(
            ExternalImportWarning(
                reason = reason,
                externalId = externalId?.takeIf { id -> id.isNotBlank() },
                rowIndex = rowIndex,
                message = message,
            )
        )
    }

    private fun MedicationKey.displayName(): String {
        return name.lowercase(Locale.ROOT)
            .split("_")
            .joinToString(" ") { part -> part.replaceFirstChar { char -> char.titlecase(Locale.ROOT) } }
    }

    private fun String?.isFinasterideOrDutasteride(): Boolean {
        return when (normalizedToken()) {
            "finasteride",
            "dutasteride",
            -> true

            else -> false
        }
    }

    private fun Map<String, Any?>.hasAny(vararg keys: String): Boolean {
        return keys.any { key -> containsKey(key) }
    }

    private fun Map<String, Any?>.numberByKey(key: String): Double? {
        return number(valueByKey(key))
    }

    private fun Map<String, Any?>.valueByKey(key: String): Any? {
        return entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value
    }

    private fun metadataValue(
        root: Map<String, Any?>,
        key: String,
    ): String? {
        return text(root["meta"].asMap()?.valueByKey(key))
    }

    private fun Any?.asMap(): Map<String, Any?>? {
        val map = this as? Map<*, *> ?: return null
        return map.entries.mapNotNull { (key, value) ->
            (key as? String)?.let { stringKey -> stringKey to value }
        }.toMap()
    }

    private fun Any?.asList(): List<Any?>? = this as? List<Any?>

    private fun text(value: Any?): String? {
        return when (value) {
            is String -> value.trim()
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> null
        }?.takeIf { text -> text.isNotBlank() }
    }

    private fun number(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun boolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> null
        }
    }

    private fun String?.normalizedToken(): String? {
        return this?.lowercase(Locale.ROOT)?.replace(Regex("[^a-z0-9]"), "")
    }

    private data class MedicationRow(
        val row: Map<String, Any?>?,
        val rowIndex: Int,
        val modeName: String?,
    )

    private data class LabRow(
        val row: Map<String, Any?>?,
        val rowIndex: Int,
        val modeName: String?,
        val sourceAnalyte: BloodAnalyteKey?,
    )

    private enum class ExternalRoute {
        ORAL,
        SUBLINGUAL,
        INJECTION,
        GEL,
        PATCH_APPLY,
        PATCH_REMOVE,
    }

    private sealed interface CompoundMapping {
        data class Estrogen(val key: MedicationKey) : CompoundMapping
        data object Testosterone : CompoundMapping
        data object UnsupportedEstrogen : CompoundMapping
        data object UnsupportedOther : CompoundMapping
    }

    private sealed interface NoMtfSpecialResult {
        data object Continue : NoMtfSpecialResult
        data object Skip : NoMtfSpecialResult
        data class Dose(val dose: ExternalImportCandidate.MedicationDose) : NoMtfSpecialResult
    }

    private fun ExternalImportCandidate.MedicationDose?.toNoMtfResult(): NoMtfSpecialResult {
        return if (this == null) {
            NoMtfSpecialResult.Skip
        } else {
            NoMtfSpecialResult.Dose(this)
        }
    }

    private companion object {
        private const val EXTERNAL_TRACKER_DISPLAY_NAME = "External tracker"
    }
}
