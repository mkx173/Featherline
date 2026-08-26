package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.pk.DenseSamplePolicy
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.homeSnapshotDataStore: DataStore<HomeSnapshotState> by dataStore(
    fileName = "home_snapshot.pb",
    serializer = HomeSnapshotSerializer(AndroidHomeSnapshotCrypto()),
)

private val Context.homeSnapshotGenerationDataStore by preferencesDataStore(
    name = "home_snapshot_metadata",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class HomeSnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun observeSnapshot(): Flow<HomeSnapshotRecord?> {
        return context.homeSnapshotDataStore.data
            .map { state -> state.record }
            .distinctUntilChanged()
    }

    suspend fun readSnapshot(): HomeSnapshotRecord? {
        return context.homeSnapshotDataStore.data.first().record
    }

    suspend fun writeSnapshot(record: HomeSnapshotRecord) {
        context.homeSnapshotDataStore.updateData {
            HomeSnapshotState(record = record)
        }
    }

    suspend fun clearSnapshot() {
        context.homeSnapshotDataStore.updateData {
            HomeSnapshotState.Empty
        }
    }
}

@Singleton
class HomeSnapshotGenerationStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun observeGeneration(): Flow<Long> {
        return context.homeSnapshotGenerationDataStore.data
            .map { preferences -> preferences[HOME_SNAPSHOT_GENERATION_KEY] ?: 0L }
            .distinctUntilChanged()
    }

    suspend fun readGeneration(): Long {
        return context.homeSnapshotGenerationDataStore.data
            .map { preferences -> preferences[HOME_SNAPSHOT_GENERATION_KEY] ?: 0L }
            .first()
    }

    suspend fun incrementGeneration(): Long {
        var nextGeneration = 0L
        context.homeSnapshotGenerationDataStore.edit { preferences ->
            nextGeneration = (preferences[HOME_SNAPSHOT_GENERATION_KEY] ?: 0L) + 1L
            preferences[HOME_SNAPSHOT_GENERATION_KEY] = nextGeneration
        }
        return nextGeneration
    }
}

data class HomeSnapshotRecord(
    val schemaVersion: Int,
    val generation: Long = 0L,
    val generatedAtEpochMillis: Long,
    val anchorDateEpochDay: Long,
    val zoneId: String,
    val pkProjection: HomePkProjectionRecord?,
    // Sibling of pkProjection simulated with plannedEntries=emptyList(). The
    // widget reads this to display the current body-state estimate from logged
    // doses only — without it, a planned dose 30 min from now would distort the
    // widget's "current" reading. Nullable so test fixtures don't have to fill
    // it; refresh always populates both projections in the same pass.
    val widgetPkProjection: HomePkProjectionRecord? = null,
    val activeGroups: List<MedicationGroup>,
    /** Archived groups, carried so Home/widget can mirror the Plan page. Defaulted for old fixtures/caches. */
    val archivedGroups: List<MedicationGroup> = emptyList(),
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
    // Cached so Settings (and any other UserProfile consumer) can render the real
    // weight on first frame instead of flashing the empty default while Room cold
    // init completes. Nullable for forward-compat with snapshots from before this
    // field was added.
    val userProfile: UserProfile? = null,
    /** Every active tracked medicine, used so cold-start can re-project ungrouped meds. */
    val stockMedicines: List<Medicine> = emptyList(),
    /** Log entries whose `scheduledFor` falls inside the stock simulation window. */
    val stockFulfillmentEntries: List<MedicationLogEntry> = emptyList(),
    // Real estradiol log entries over the PK input window. Embedded so the
    // snapshot path can re-run the simulation locally — without waiting for
    // Room — when the cached projection expires (e.g. the user missed a
    // future planned dose). Encoded with medicine dedup since a daily doser
    // repeats the same one or two medicines across the whole window.
    val pkEntries: List<MedicationLogEntry> = emptyList(),
    /**
     * Route log-scales from the lab calibration evaluated at refresh time,
     * keyed by [com.mkx.hrttracker.model.pk.PkCalibrationRoute.stableId].
     * Both projections above are simulated with them, so Home and the widget
     * show the calibrated curve on first frame and after every mutation.
     */
    val pkRouteLogScale: Map<String, Double> = emptyMap(),
    /** Predictive band over the Home projection window (logged + planned doses). */
    val pkBandKnots: List<HomePkBandKnotRecord> = emptyList(),
    // First pinned tracked date (the Home hero anchor), cached so cold start can
    // render the hero on the first frame instead of waiting on Room. Null when no
    // date is pinned. Defaulted for forward-compat with pre-field snapshots.
    val homeAnchor: TrackedDate? = null,
)

data class HomePkBandKnotRecord(
    val epochMillis: Long,
    val p025Pgml: Double,
    val p158655254Pgml: Double,
    val p50Pgml: Double,
    val p841344746Pgml: Double,
    val p975Pgml: Double,
)

fun HomeSnapshotRecord.pkBandKnots(): List<PkPredictiveBandKnot> = pkBandKnots.map { knot ->
    PkPredictiveBandKnot(
        epochMillis = knot.epochMillis,
        p025Pgml = knot.p025Pgml,
        p158655254Pgml = knot.p158655254Pgml,
        p50Pgml = knot.p50Pgml,
        p841344746Pgml = knot.p841344746Pgml,
        p975Pgml = knot.p975Pgml,
    )
}

/** Personal params the snapshot's projections were simulated with. */
fun HomeSnapshotRecord.pkPersonalParams(): PkPersonalParams {
    return PkPersonalParams.create(
        pkRouteLogScale.entries.mapNotNull { (stableId, beta) ->
            PkCalibrationRoute.fromStableId(stableId)?.let { route -> route to beta }
        }.toMap()
    ) ?: PkPersonalParams.population()
}

data class HomePkProjectionRecord(
    val generatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    // Wall-clock instant past which the projection is considered stale —
    // typically the soonest planned dose time strictly after generatedAt.
    // Once now crosses this, consumers should treat the projection as
    // missing and request a refresh, because the cached curve still shows
    // doses the user may have missed.
    val pkProjectionExpiresAtEpochMillis: Long,
    val concentrationUnit: String,
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val doseMarkers: List<HomePkProjectionDoseMarkerRecord>,
    val latestEstradiolEntry: MedicationLogEntry?,
    // Sampling fingerprint: validator compares these against the resolved
    // HomeE2ChartWindowOption rather than the enum name, so option renames
    // do not invalidate caches whose sampling recipe is unchanged.
    val chartWindowHours: Int,
    val densePolicy: HomePkDenseSamplePolicyRecord,
    val includesPostDoseOffsets: Boolean,
)

data class HomePkProjectionDoseMarkerRecord(
    val timeH: Double,
    val concentration: Double,
    val isPlanned: Boolean = false,
)

sealed interface HomePkDenseSamplePolicyRecord {
    data class Interval(val hours: Double) : HomePkDenseSamplePolicyRecord
    data class Budget(val segmentCount: Int) : HomePkDenseSamplePolicyRecord
}

fun DenseSamplePolicy.toRecord(): HomePkDenseSamplePolicyRecord = when (this) {
    is DenseSamplePolicy.Interval -> HomePkDenseSamplePolicyRecord.Interval(hours = hours)
    is DenseSamplePolicy.Budget -> HomePkDenseSamplePolicyRecord.Budget(segmentCount = segmentCount)
}

internal data class HomeSnapshotState(
    val record: HomeSnapshotRecord?,
) {
    companion object {
        val Empty = HomeSnapshotState(record = null)
    }
}

internal class HomeSnapshotSerializer(
    private val crypto: HomeSnapshotCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) : Serializer<HomeSnapshotState> {
    override val defaultValue: HomeSnapshotState = HomeSnapshotState.Empty

    override suspend fun readFrom(input: InputStream): HomeSnapshotState {
        val encryptedBytes = input.readBytes()
        if (encryptedBytes.isEmpty()) {
            return HomeSnapshotState.Empty
        }
        return runCatching {
            HomeSnapshotState(
                record = HomeSnapshotCodec.decode(
                    crypto.decrypt(encryptedBytes)
                )
            )
        }.getOrElse { throwable ->
            diagnosticsLogger.warning(
                TAG,
                "home_snapshot_read_failed bytes=${encryptedBytes.size}",
                throwable,
            )
            HomeSnapshotState.Empty
        }
    }

    override suspend fun writeTo(
        t: HomeSnapshotState,
        output: OutputStream,
    ) {
        val record = t.record ?: return
        output.write(
            crypto.encrypt(
                HomeSnapshotCodec.encode(record)
            )
        )
    }
}

internal object HomeSnapshotCodec {
    fun encode(record: HomeSnapshotRecord): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeInt(SNAPSHOT_CODEC_VERSION)
            stream.writeInt(record.schemaVersion)
            stream.writeLong(record.generation)
            stream.writeLong(record.generatedAtEpochMillis)
            stream.writeLong(record.anchorDateEpochDay)
            stream.writeString(record.zoneId)
            stream.writeHomePkProjectionRecord(record.pkProjection)
            stream.writeHomePkProjectionRecord(record.widgetPkProjection)
            stream.writeList(record.activeGroups) { group -> writeMedicationGroup(group) }
            stream.writeList(record.archivedGroups) { group -> writeMedicationGroup(group) }
            stream.writeList(record.scheduleEntries) { entry -> writeMedicationLogEntry(entry) }
            stream.writeList(record.antiandrogenHistoryEntries) { entry ->
                writeMedicationLogEntry(
                    entry
                )
            }
            stream.writeUserProfile(record.userProfile)
            stream.writeList(record.stockMedicines) { medicine -> writeMedicine(medicine) }
            stream.writeList(record.stockFulfillmentEntries) { entry ->
                writeMedicationLogEntry(
                    entry
                )
            }
            stream.writePooledMedicationLogEntries(record.pkEntries)
            stream.writeTrackedDate(record.homeAnchor)
            stream.writeList(record.pkRouteLogScale.entries.toList()) { (route, beta) ->
                writeString(route)
                writeDouble(beta)
            }
            stream.writeList(record.pkBandKnots) { knot ->
                writeLong(knot.epochMillis)
                writeDouble(knot.p025Pgml)
                writeDouble(knot.p158655254Pgml)
                writeDouble(knot.p50Pgml)
                writeDouble(knot.p841344746Pgml)
                writeDouble(knot.p975Pgml)
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): HomeSnapshotRecord {
        return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val version = stream.readInt()
            require(version == SNAPSHOT_CODEC_VERSION) {
                "Unsupported Home snapshot version: $version."
            }
            HomeSnapshotRecord(
                schemaVersion = stream.readInt(),
                generation = stream.readLong(),
                generatedAtEpochMillis = stream.readLong(),
                anchorDateEpochDay = stream.readLong(),
                zoneId = stream.readString(),
                pkProjection = stream.readHomePkProjectionRecord(),
                widgetPkProjection = stream.readHomePkProjectionRecord(),
                activeGroups = stream.readList { readMedicationGroup() },
                archivedGroups = stream.readList { readMedicationGroup() },
                scheduleEntries = stream.readList { checkNotNull(readMedicationLogEntry()) },
                antiandrogenHistoryEntries = stream.readList { checkNotNull(readMedicationLogEntry()) },
                userProfile = stream.readUserProfile(),
                stockMedicines = stream.readList { readMedicine() },
                stockFulfillmentEntries = stream.readList { checkNotNull(readMedicationLogEntry()) },
                pkEntries = stream.readPooledMedicationLogEntries(),
                homeAnchor = stream.readTrackedDate(),
                pkRouteLogScale = stream.readList { readString() to readDouble() }.toMap(),
                pkBandKnots = stream.readList {
                    HomePkBandKnotRecord(
                        epochMillis = readLong(),
                        p025Pgml = readDouble(),
                        p158655254Pgml = readDouble(),
                        p50Pgml = readDouble(),
                        p841344746Pgml = readDouble(),
                        p975Pgml = readDouble(),
                    )
                },
            )
        }
    }

    private fun DataOutputStream.writeHomePkProjectionRecord(record: HomePkProjectionRecord?) {
        writeBoolean(record != null)
        record ?: return
        writeLong(record.generatedAtEpochMillis)
        writeLong(record.windowStartEpochMillis)
        writeLong(record.windowEndEpochMillis)
        writeLong(record.pkProjectionExpiresAtEpochMillis)
        writeString(record.concentrationUnit)
        writeList(record.timeH) { value -> writeDouble(value) }
        writeList(record.concentrations) { value -> writeDouble(value) }
        writeList(record.doseMarkers) { marker ->
            writeDouble(marker.timeH)
            writeDouble(marker.concentration)
            writeBoolean(marker.isPlanned)
        }
        writeMedicationLogEntry(record.latestEstradiolEntry)
        writeInt(record.chartWindowHours)
        writeDenseSamplePolicy(record.densePolicy)
        writeBoolean(record.includesPostDoseOffsets)
    }

    private fun DataOutputStream.writeDenseSamplePolicy(policy: HomePkDenseSamplePolicyRecord) {
        when (policy) {
            is HomePkDenseSamplePolicyRecord.Interval -> {
                writeByte(POLICY_DISCRIMINATOR_INTERVAL)
                writeDouble(policy.hours)
            }

            is HomePkDenseSamplePolicyRecord.Budget -> {
                writeByte(POLICY_DISCRIMINATOR_BUDGET)
                writeInt(policy.segmentCount)
            }
        }
    }

    private fun DataInputStream.readDenseSamplePolicy(): HomePkDenseSamplePolicyRecord {
        return when (val discriminator = readByte().toInt()) {
            POLICY_DISCRIMINATOR_INTERVAL -> HomePkDenseSamplePolicyRecord.Interval(hours = readDouble())
            POLICY_DISCRIMINATOR_BUDGET -> HomePkDenseSamplePolicyRecord.Budget(segmentCount = readInt())
            else -> error("Unknown dense sample policy discriminator: $discriminator")
        }
    }

    private fun DataInputStream.readHomePkProjectionRecord(): HomePkProjectionRecord? {
        if (!readBoolean()) {
            return null
        }
        return HomePkProjectionRecord(
            generatedAtEpochMillis = readLong(),
            windowStartEpochMillis = readLong(),
            windowEndEpochMillis = readLong(),
            pkProjectionExpiresAtEpochMillis = readLong(),
            concentrationUnit = readString(),
            timeH = readList { readDouble() },
            concentrations = readList { readDouble() },
            doseMarkers = readList {
                HomePkProjectionDoseMarkerRecord(
                    timeH = readDouble(),
                    concentration = readDouble(),
                    isPlanned = readBoolean(),
                )
            },
            latestEstradiolEntry = readMedicationLogEntry(),
            chartWindowHours = readInt(),
            densePolicy = readDenseSamplePolicy(),
            includesPostDoseOffsets = readBoolean(),
        )
    }

    private fun DataOutputStream.writeMedicationGroup(group: MedicationGroup) {
        writeString(group.uuid.toString())
        writeString(group.name)
        writeString(group.colorKey.name)
        writeMedicationGroupSchedule(group.schedule)
        writeList(group.medications) { medication -> writeMedicationGroupMedication(medication) }
        writeBoolean(group.notificationsEnabled)
        writeLong(group.createdAt.toEpochMilli())
        writeLong(group.updatedAt.toEpochMilli())
        writeNullableLong(group.archivedAt?.toEpochMilli())
        writeNullableString(group.archivedAtLocal?.toString())
        writeBoolean(group.includePastScheduledSlots)
        writeNullableString(group.replacedByGroupUuid?.toString())
        writeNullableString(group.recreatedFromGroupUuid?.toString())
    }

    private fun DataInputStream.readMedicationGroup(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString(readString()),
            name = readString(),
            colorKey = MedicationGroupColorKey.fromStorageValue(readString()),
            schedule = readMedicationGroupSchedule(),
            medications = readList { readMedicationGroupMedication() },
            notificationsEnabled = readBoolean(),
            createdAt = Instant.ofEpochMilli(readLong()),
            updatedAt = Instant.ofEpochMilli(readLong()),
            archivedAt = readNullableLong()?.let(Instant::ofEpochMilli),
            archivedAtLocal = readNullableString()?.let(LocalDateTime::parse),
            includePastScheduledSlots = readBoolean(),
            replacedByGroupUuid = readNullableString()?.let(UUID::fromString),
            recreatedFromGroupUuid = readNullableString()?.let(UUID::fromString),
        )
    }

    private fun DataOutputStream.writeMedicationGroupSchedule(schedule: MedicationGroupSchedule) {
        writeString(schedule.type.name)
        writeInt(schedule.interval)
        writeLong(schedule.since.toEpochDay())
        writeList(schedule.weeklyDaysOfWeek.sortedBy(DayOfWeek::getValue)) { day ->
            writeInt(day.value)
        }
        writeList(schedule.times) { time -> writeLocalTime(time) }
        writeList(schedule.timeSlots) { timeSlot -> writeMedicationGroupScheduleTime(timeSlot) }
    }

    private fun DataInputStream.readMedicationGroupSchedule(): MedicationGroupSchedule {
        return MedicationGroupSchedule(
            type = MedicationGroupScheduleType.fromStorageValue(readString()),
            interval = readInt(),
            since = LocalDate.ofEpochDay(readLong()),
            weeklyDaysOfWeek = readList { DayOfWeek.of(readInt()) }.toSet(),
            times = readList { readLocalTime() },
            timeSlots = readList { readMedicationGroupScheduleTime() },
        )
    }

    private fun DataOutputStream.writeMedicationGroupScheduleTime(time: MedicationGroupScheduleTime) {
        writeString(time.uuid.toString())
        writeLocalTime(time.time)
        writeString(time.effectiveFrom.toString())
    }

    private fun DataInputStream.readMedicationGroupScheduleTime(): MedicationGroupScheduleTime {
        return MedicationGroupScheduleTime(
            uuid = UUID.fromString(readString()),
            time = readLocalTime(),
            effectiveFrom = LocalDateTime.parse(readString()),
        )
    }

    private fun DataOutputStream.writeMedicationGroupMedication(medication: MedicationGroupMedication) {
        writeString(medication.uuid.toString())
        writeNullableMedicine(medication.medicine)
        writeString(medication.applicationType.name)
        writeDoseInstruction(medication.doseInstruction)
        writeInt(medication.count)
    }

    private fun DataInputStream.readMedicationGroupMedication(): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = UUID.fromString(readString()),
            medicine = readNullableMedicine(),
            applicationType = MedicationApplicationType.fromStorageValue(readString()),
            doseInstruction = readDoseInstruction(),
            count = readInt(),
        )
    }

    private fun DataOutputStream.writeMedicationLogEntry(entry: MedicationLogEntry?) {
        writeBoolean(entry != null)
        entry ?: return
        writeString(entry.uuid.toString())
        writeNullableMedicine(entry.medicine)
        writeMedicationLogEntryBody(entry)
    }

    private fun DataInputStream.readMedicationLogEntry(): MedicationLogEntry? {
        if (!readBoolean()) {
            return null
        }
        val uuid = UUID.fromString(readString())
        val medicine = readNullableMedicine()
        return readMedicationLogEntryBody(uuid = uuid, medicine = medicine)
    }

    // Writes a non-null entry list using a deduplicated medicine pool: each
    // distinct medicine is serialized once, and entries reference it by pool
    // index (-1 = no medicine). A daily doser embeds ~200 entries that repeat
    // the same medicine, so inlining each one would roughly double the cost.
    private fun DataOutputStream.writePooledMedicationLogEntries(entries: List<MedicationLogEntry>) {
        val medicinePool = entries.mapNotNull { entry -> entry.medicine }.distinctBy { it.uuid }
        val indexByUuid =
            medicinePool.withIndex().associate { (index, medicine) -> medicine.uuid to index }
        writeList(medicinePool) { medicine -> writeMedicine(medicine) }
        writeList(entries) { entry ->
            writeString(entry.uuid.toString())
            writeInt(entry.medicine?.let { medicine -> indexByUuid.getValue(medicine.uuid) } ?: -1)
            writeMedicationLogEntryBody(entry)
        }
    }

    private fun DataInputStream.readPooledMedicationLogEntries(): List<MedicationLogEntry> {
        val medicinePool = readList { readMedicine() }
        return readList {
            val uuid = UUID.fromString(readString())
            val medicineIndex = readInt()
            val medicine = medicinePool.getOrNull(medicineIndex)
            readMedicationLogEntryBody(uuid = uuid, medicine = medicine)
        }
    }

    private fun DataOutputStream.writeTrackedDate(date: TrackedDate?) {
        writeBoolean(date != null)
        date ?: return
        writeString(date.id)
        writeString(date.name)
        writeString(date.icon.storageKey)
        writeLong(date.date.toEpochDay())
        writeNullableString(date.palette?.name)
        writeNullableString(date.heroBackground.storageKey)
        writeBoolean(date.pinnedOrder != null)
        date.pinnedOrder?.let { writeInt(it) }
        writeLong(date.createdAtEpochMillis)
    }

    private fun DataInputStream.readTrackedDate(): TrackedDate? {
        if (!readBoolean()) return null
        return TrackedDate(
            id = readString(),
            name = readString(),
            icon = AnchorIcon.fromStorageValue(readString()),
            date = LocalDate.ofEpochDay(readLong()),
            palette = readNullableString()?.let(MedicationGroupColorKey::fromStorageValueOrNull),
            heroBackground = HeroBackground.fromStorageValue(readNullableString()),
            pinnedOrder = if (readBoolean()) readInt() else null,
            createdAtEpochMillis = readLong(),
        )
    }

    private fun DataOutputStream.writeMedicationLogEntryBody(entry: MedicationLogEntry) {
        writeString(entry.category.name)
        writeString(entry.applicationType.name)
        writeDoseInstruction(entry.doseInstruction)
        writeNullableDouble(entry.equivalentE2Mg)
        writeNullableString(entry.sourceGroupUuid?.toString())
        writeNullableString(entry.scheduleTimeUuid?.toString())
        writeLong(entry.appliedAt.toEpochMilli())
        writeString(entry.appliedAtTimeZoneId)
        writeNullableString(entry.scheduledFor?.toString())
        writeInt(entry.count)
        writeNullableDouble(entry.doseAmountDelta)
        writeNullableString(entry.importSourceApp)
        writeNullableString(entry.importExternalId)
    }

    private fun DataInputStream.readMedicationLogEntryBody(
        uuid: UUID,
        medicine: Medicine?,
    ): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = uuid,
            medicine = medicine,
            category = MedicationCategory.fromStorageValue(readString()),
            applicationType = MedicationApplicationType.fromStorageValue(readString()),
            doseInstruction = readDoseInstruction(),
            equivalentE2Mg = readNullableDouble(),
            sourceGroupUuid = readNullableString()?.let(UUID::fromString),
            scheduleTimeUuid = readNullableString()?.let(UUID::fromString),
            appliedAt = Instant.ofEpochMilli(readLong()),
            appliedAtTimeZoneId = readString(),
            scheduledFor = readNullableString()?.let(LocalDateTime::parse),
            count = readInt(),
            doseAmountDelta = readNullableDouble(),
            importSourceApp = readNullableString(),
            importExternalId = readNullableString(),
        )
    }

    private fun DataOutputStream.writeNullableMedicine(medicine: Medicine?) {
        writeBoolean(medicine != null)
        medicine ?: return
        writeMedicine(medicine)
    }

    private fun DataInputStream.readNullableMedicine(): Medicine? {
        if (!readBoolean()) {
            return null
        }
        return readMedicine()
    }

    private fun DataOutputStream.writeMedicine(medicine: Medicine) {
        writeString(medicine.uuid.toString())
        writeMedicineSelection(medicine.selection)
        writeString(medicine.category.name)
        writeMedicinePreparation(medicine.preparation)
        writeNullableString(medicine.displayName)
        writeString(medicine.identityKey)
        writeBoolean(medicine.importedFromExternalTracker)
        writeLong(medicine.createdAt.toEpochMilli())
        writeLong(medicine.updatedAt.toEpochMilli())
        writeNullableLong(medicine.archivedAt?.toEpochMilli())
        writeString(medicine.displayDoseUnit.name)
        writeMedicineStock(medicine.stock)
    }

    private fun DataInputStream.readMedicine(): Medicine {
        val uuid = UUID.fromString(readString())
        val rawSelection = readMedicineSelection()
        val category = MedicationCategory.fromStorageValue(readString())
        val preparation = readMedicinePreparation()
        val displayName = readNullableString()
        val storedIdentityKey = readString()
        val importedFromExternalTracker = readBoolean()
        val createdAt = Instant.ofEpochMilli(readLong())
        val updatedAt = Instant.ofEpochMilli(readLong())
        val archivedAt = readNullableLong()?.let(Instant::ofEpochMilli)
        val displayDoseUnit = MedicineDisplayDoseUnit.fromStorageValue(readString())
        val stock = readMedicineStock()
        // PATCH_OFF preparation is the discriminator for the sentinel selection,
        // mirroring MedicineEntityMappers.toMedicineModel — the cache writer
        // emits CATALOG selectionKind for storage convenience and we fix it up
        // here on read so a round-tripped PatchOff medicine survives intact.
        val selection = if (preparation is MedicinePreparation.PatchOff) {
            MedicineSelection.PatchOff
        } else {
            rawSelection
        }
        if (importedFromExternalTracker && storedIdentityKey.startsWith("E|")) {
            // Imported keys embed sourceApp + route, which are not recoverable
            // here; verify the dose-key segment, which is derivable from the
            // preparation, so a cached key cannot disagree with the dose it backs.
            check(
                storedIdentityKey.substringAfterLast("|") ==
                    MedicineIdentityKey.importedDoseKey(preparation)
            ) {
                "Cached imported medicine $uuid dose key does not match its preparation."
            }
        } else {
            val expectedIdentityKey = when (selection) {
                is MedicineSelection.Catalog ->
                    MedicineIdentityKey.catalog(selection.medicationKey, preparation)

                is MedicineSelection.Custom ->
                    MedicineIdentityKey.custom(selection.medicationName, preparation)

                is MedicineSelection.PatchOff -> MedicineIdentityKey.patchOff()
            }
            check(storedIdentityKey == expectedIdentityKey) {
                "Cached medicine $uuid identityKey does not match selection and preparation."
            }
        }
        return Medicine(
            uuid = uuid,
            selection = selection,
            category = category,
            preparation = preparation,
            displayName = displayName,
            identityKey = storedIdentityKey,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = archivedAt,
            displayDoseUnit = displayDoseUnit,
            stock = stock.normalizedFor(preparation),
            importedFromExternalTracker = importedFromExternalTracker,
        )
    }

    private fun DataOutputStream.writeMedicineStock(stock: MedicineStock) {
        writeBoolean(stock.trackingEnabled)
        writeNullableDouble(stock.unitsRemaining)
        writeNullableDouble(stock.unitsLastTotal)
        writeNullableDouble(stock.openContainerAmount)
        writeInt(stock.warnAtDaysRemaining)
        writeLong(stock.generation)
    }

    private fun DataInputStream.readMedicineStock(): MedicineStock {
        return MedicineStock(
            trackingEnabled = readBoolean(),
            unitsRemaining = readNullableDouble(),
            unitsLastTotal = readNullableDouble(),
            openContainerAmount = readNullableDouble(),
            warnAtDaysRemaining = readInt(),
            generation = readLong(),
        )
    }

    private fun DataOutputStream.writeMedicineSelection(selection: MedicineSelection) {
        writeString(selection.kind.name)
        when (selection) {
            is MedicineSelection.Catalog -> writeString(selection.medicationKey.name)
            is MedicineSelection.Custom -> writeString(selection.medicationName)
            // PATCH_OFF reuses the CATALOG kind tag for storage; the read path
            // recognises it from the PatchOff preparation marker. Write a
            // placeholder string for the catalog branch's medicationKey slot so
            // the binary layout matches the catalog read.
            is MedicineSelection.PatchOff -> writeString(PATCH_OFF_SELECTION_MARKER)
        }
    }

    private fun DataInputStream.readMedicineSelection(): MedicineSelection {
        return when (MedicationSelectionKind.fromStorageValue(readString())) {
            MedicationSelectionKind.CATALOG -> {
                val keyName = readString()
                if (keyName == PATCH_OFF_SELECTION_MARKER) {
                    MedicineSelection.PatchOff
                } else {
                    MedicineSelection.Catalog(
                        medicationKey = checkNotNull(MedicationKey.fromStorageValue(keyName))
                    )
                }
            }

            MedicationSelectionKind.CUSTOM -> MedicineSelection.Custom(
                medicationName = readString()
            )
        }
    }

    private fun DataOutputStream.writeMedicinePreparation(preparation: MedicinePreparation) {
        writeString(preparation.type.name)
        when (preparation) {
            is MedicinePreparation.Pill -> writeDouble(preparation.strengthMgPerTablet)
            is MedicinePreparation.Capsule -> writeDouble(preparation.strengthMgPerCapsule)
            is MedicinePreparation.InjectionSingleUseVial -> writeDouble(preparation.strengthMgPerVial)
            is MedicinePreparation.InjectionMultiUseVial -> {
                writeDouble(preparation.concentrationMgPerMl)
                writeDouble(preparation.vialVolumeMl)
            }

            is MedicinePreparation.GelSachet -> {
                writeDouble(preparation.concentrationPercent)
                writeDouble(preparation.sachetWeightGrams)
            }

            is MedicinePreparation.GelContainer -> {
                writeDouble(preparation.concentrationPercent)
                writeDouble(preparation.containerWeightGrams)
            }

            is MedicinePreparation.ImportedInjection -> {
                writeDouble(preparation.administeredMg)
                writeString(preparation.ester.name)
            }

            is MedicinePreparation.ImportedGel -> {
                writeDouble(preparation.appliedEstradiolMg)
            }

            is MedicinePreparation.Patch -> when (val specification = preparation.specification) {
                is MedicinePreparation.PatchSpecification.TotalMg -> {
                    writeByte(PATCH_SPECIFICATION_TOTAL_MG)
                    writeDouble(specification.valueMg)
                }

                is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> {
                    writeByte(PATCH_SPECIFICATION_RELEASE_RATE)
                    writeDouble(specification.valueMcgPerDay)
                }
            }
            // No payload — the type tag (PATCH_OFF) is enough to round-trip.
            is MedicinePreparation.PatchOff -> Unit
        }
    }

    private fun DataInputStream.readMedicinePreparation(): MedicinePreparation {
        return when (MedicinePreparationType.fromStorageValue(readString())) {
            MedicinePreparationType.PILL -> MedicinePreparation.Pill(
                strengthMgPerTablet = readDouble()
            )

            MedicinePreparationType.CAPSULE -> MedicinePreparation.Capsule(
                strengthMgPerCapsule = readDouble()
            )

            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = readDouble()
            )

            MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = readDouble(),
                vialVolumeMl = readDouble(),
            )

            MedicinePreparationType.GEL_SACHET -> MedicinePreparation.GelSachet(
                concentrationPercent = readDouble(),
                sachetWeightGrams = readDouble(),
            )

            MedicinePreparationType.GEL_CONTAINER -> MedicinePreparation.GelContainer(
                concentrationPercent = readDouble(),
                containerWeightGrams = readDouble(),
            )

            MedicinePreparationType.IMPORTED_INJECTION -> MedicinePreparation.ImportedInjection(
                administeredMg = readDouble(),
                ester = checkNotNull(MedicationKey.fromStorageValue(readString())) {
                    "Imported injection snapshot medicine is missing ester medicationKey."
                },
            )

            MedicinePreparationType.IMPORTED_GEL -> MedicinePreparation.ImportedGel(
                appliedEstradiolMg = readDouble(),
            )

            MedicinePreparationType.PATCH -> MedicinePreparation.Patch(
                specification = when (val discriminator = readByte().toInt()) {
                    PATCH_SPECIFICATION_TOTAL_MG ->
                        MedicinePreparation.PatchSpecification.TotalMg(valueMg = readDouble())

                    PATCH_SPECIFICATION_RELEASE_RATE ->
                        MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                            valueMcgPerDay = readDouble()
                        )

                    else -> error("Unknown patch specification discriminator: $discriminator")
                }
            )

            MedicinePreparationType.PATCH_OFF -> MedicinePreparation.PatchOff
        }
    }

    private fun DataOutputStream.writeDoseInstruction(instruction: DoseInstruction) {
        writeString(instruction.kind.name)
        when (instruction) {
            is DoseInstruction.TabletFraction -> {
                writeInt(instruction.numerator)
                writeInt(instruction.denominator)
            }

            DoseInstruction.WholeUnit -> Unit
            is DoseInstruction.VolumeMl -> writeDouble(instruction.valueMl)
            is DoseInstruction.WeightGrams -> writeDouble(instruction.valueGrams)
            DoseInstruction.Noop -> Unit
        }
    }

    private fun DataInputStream.readDoseInstruction(): DoseInstruction {
        return when (DoseInstructionKind.fromStorageValue(readString())) {
            DoseInstructionKind.TABLET_FRACTION -> DoseInstruction.TabletFraction(
                numerator = readInt(),
                denominator = readInt(),
            )

            DoseInstructionKind.WHOLE_UNIT -> DoseInstruction.WholeUnit
            DoseInstructionKind.VOLUME_ML -> DoseInstruction.VolumeMl(valueMl = readDouble())
            DoseInstructionKind.WEIGHT_GRAMS -> DoseInstruction.WeightGrams(valueGrams = readDouble())
            DoseInstructionKind.NOOP -> DoseInstruction.Noop
        }
    }

    private fun DataOutputStream.writeLocalTime(time: LocalTime) {
        writeInt(time.hour)
        writeInt(time.minute)
        writeInt(time.second)
        writeInt(time.nano)
    }

    private fun DataInputStream.readLocalTime(): LocalTime {
        return LocalTime.of(readInt(), readInt(), readInt(), readInt())
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size >= 0) { "String length must not be negative." }
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        value?.let { resolvedValue -> writeString(resolvedValue) }
    }

    private fun DataInputStream.readNullableString(): String? {
        return if (readBoolean()) readString() else null
    }

    private fun DataOutputStream.writeNullableDouble(value: Double?) {
        writeBoolean(value != null)
        value?.let(::writeDouble)
    }

    private fun DataInputStream.readNullableDouble(): Double? {
        return if (readBoolean()) readDouble() else null
    }

    private fun DataOutputStream.writeUserProfile(profile: UserProfile?) {
        writeBoolean(profile != null)
        profile ?: return
        writeNullableDouble(profile.weightKg)
        writeNullableDouble(profile.weightOriginalValue)
        writeString(profile.weightOriginalUnit.name)
        writeNullableLong(profile.updatedAt?.toEpochMilli())
    }

    private fun DataInputStream.readUserProfile(): UserProfile? {
        if (!readBoolean()) return null
        return UserProfile(
            weightKg = readNullableDouble(),
            weightOriginalValue = readNullableDouble(),
            weightOriginalUnit = WeightUnit.fromStorageValue(readString()),
            updatedAt = readNullableLong()?.let(Instant::ofEpochMilli),
        )
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        value?.let(::writeLong)
    }

    private fun DataInputStream.readNullableLong(): Long? {
        return if (readBoolean()) readLong() else null
    }

    private fun <T> DataOutputStream.writeList(
        values: List<T>,
        writeItem: DataOutputStream.(T) -> Unit,
    ) {
        writeInt(values.size)
        values.forEach { value -> writeItem(value) }
    }

    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> {
        val size = readInt()
        require(size >= 0) { "List size must not be negative." }
        return List(size) { readItem() }
    }
}

internal interface HomeSnapshotCrypto {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

private class AndroidHomeSnapshotCrypto : HomeSnapshotCrypto {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encryptedBytes = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteBuffer
            .allocate(ENCRYPTED_MAGIC_BYTES.size + VERSION_LENGTH_BYTES + IV_LENGTH_BYTES + iv.size + encryptedBytes.size)
            .put(ENCRYPTED_MAGIC_BYTES)
            .put(ENCRYPTED_CONTAINER_VERSION.toByte())
            .put(iv.size.toByte())
            .put(iv)
            .put(encryptedBytes)
            .array()
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(ciphertext)
        val magic = ByteArray(ENCRYPTED_MAGIC_BYTES.size)
        buffer.get(magic)
        require(magic.contentEquals(ENCRYPTED_MAGIC_BYTES)) {
            "Unsupported Home snapshot container."
        }
        val version = buffer.get().toInt() and BYTE_MASK
        require(version == ENCRYPTED_CONTAINER_VERSION) {
            "Unsupported Home snapshot container version: $version."
        }
        val ivLength = buffer.get().toInt() and BYTE_MASK
        require(ivLength > 0 && ivLength <= MAX_GCM_IV_LENGTH_BYTES && buffer.remaining() > ivLength) {
            "Invalid Home snapshot IV."
        }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        return cipher.doFinal(encryptedBytes)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(AES_KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return keyGenerator.generateKey()
    }
}

private const val TAG = "HomeSnapshotStore"

// v15 carries MedicineStock inside cached Medicine values; older caches are
// rejected by codec-version mismatch so tracked stock is never defaulted away.
// v17 appends the deduped pkEntries pool so the snapshot path can re-simulate
// the E2 curve locally when the cached projection expires.
// v18 appends archivedGroups (after activeGroups) so Home/widget mirror Plan.
// v19 appends doseAmountDelta to medication log entries for stock deltas.
// v20 appends importedFromExternalTracker to cached Medicine and adds imported
// injection/gel preparation payloads.
// v21 appends medication log import provenance.
// v22 appends the cached Home hero anchor tracked date.
// v23 appends the calibration route log-scales the projections were simulated with.
// v24 appends the calibration predictive band over the Home projection window.
private const val SNAPSHOT_CODEC_VERSION = 24
private const val POLICY_DISCRIMINATOR_INTERVAL = 0
private const val POLICY_DISCRIMINATOR_BUDGET = 1
private const val PATCH_SPECIFICATION_TOTAL_MG = 0
private const val PATCH_SPECIFICATION_RELEASE_RATE = 1

// Reserved sentinel string in the medicationKey slot for a PATCH_OFF row in
// the snapshot codec. Distinct from every MedicationKey.name.
private const val PATCH_OFF_SELECTION_MARKER = "__PATCH_OFF__"
private val HOME_SNAPSHOT_GENERATION_KEY = longPreferencesKey("generation")
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val MASTER_KEY_ALIAS = "hrt_home_snapshot_key"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_SIZE_BITS = 256
private const val GCM_TAG_LENGTH_BITS = 128
private const val ENCRYPTED_CONTAINER_VERSION = 1
private const val VERSION_LENGTH_BYTES = 1
private const val IV_LENGTH_BYTES = 1
private const val MAX_GCM_IV_LENGTH_BYTES = 32
private const val BYTE_MASK = 0xff
private val ENCRYPTED_MAGIC_BYTES = byteArrayOf(
    'H'.code.toByte(),
    'O'.code.toByte(),
    'M'.code.toByte(),
    'E'.code.toByte(),
)
