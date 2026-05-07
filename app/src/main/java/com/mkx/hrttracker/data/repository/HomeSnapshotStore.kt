package com.mkx.hrttracker.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
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
    val sourceFingerprint: String,
    val pkProjection: HomePkProjectionRecord?,
    val activeGroups: List<MedicationGroup>,
    val scheduleEntries: List<MedicationLogEntry>,
    val antiandrogenHistoryEntries: List<MedicationLogEntry>,
)

data class HomePkProjectionRecord(
    val generatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val sourceFingerprint: String,
    val payloadJson: String,
    val latestEstradiolEntry: MedicationLogEntry?,
)

internal data class HomeSnapshotState(
    val record: HomeSnapshotRecord?,
) {
    companion object {
        val Empty = HomeSnapshotState(record = null)
    }
}

internal class HomeSnapshotSerializer(
    private val crypto: HomeSnapshotCrypto,
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
        }.getOrDefault(HomeSnapshotState.Empty)
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
            stream.writeString(record.sourceFingerprint)
            stream.writeHomePkProjectionRecord(record.pkProjection)
            stream.writeList(record.activeGroups) { group -> writeMedicationGroup(group) }
            stream.writeList(record.scheduleEntries) { entry -> writeMedicationLogEntry(entry) }
            stream.writeList(record.antiandrogenHistoryEntries) { entry -> writeMedicationLogEntry(entry) }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): HomeSnapshotRecord {
        return DataInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val version = stream.readInt()
            require(version in 2..SNAPSHOT_CODEC_VERSION) {
                "Unsupported Home snapshot version: $version."
            }
            val schemaVersion = stream.readInt()
            HomeSnapshotRecord(
                schemaVersion = schemaVersion,
                generation = if (version >= 3) stream.readLong() else 0L,
                generatedAtEpochMillis = stream.readLong(),
                anchorDateEpochDay = stream.readLong(),
                zoneId = stream.readString(),
                sourceFingerprint = stream.readString(),
                pkProjection = stream.readHomePkProjectionRecord(),
                activeGroups = stream.readList { readMedicationGroup() },
                scheduleEntries = stream.readList { checkNotNull(readMedicationLogEntry()) },
                antiandrogenHistoryEntries = stream.readList { checkNotNull(readMedicationLogEntry()) },
            )
        }
    }

    private fun DataOutputStream.writeHomePkProjectionRecord(record: HomePkProjectionRecord?) {
        writeBoolean(record != null)
        record ?: return
        writeLong(record.generatedAtEpochMillis)
        writeLong(record.windowStartEpochMillis)
        writeLong(record.windowEndEpochMillis)
        writeString(record.sourceFingerprint)
        writeString(record.payloadJson)
        writeMedicationLogEntry(record.latestEstradiolEntry)
    }

    private fun DataInputStream.readHomePkProjectionRecord(): HomePkProjectionRecord? {
        if (!readBoolean()) {
            return null
        }
        return HomePkProjectionRecord(
            generatedAtEpochMillis = readLong(),
            windowStartEpochMillis = readLong(),
            windowEndEpochMillis = readLong(),
            sourceFingerprint = readString(),
            payloadJson = readString(),
            latestEstradiolEntry = readMedicationLogEntry(),
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
        writeMedicationDetails(medication.details)
        writeInt(medication.count)
    }

    private fun DataInputStream.readMedicationGroupMedication(): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = UUID.fromString(readString()),
            details = readMedicationDetails(),
            count = readInt(),
        )
    }

    private fun DataOutputStream.writeMedicationLogEntry(entry: MedicationLogEntry?) {
        writeBoolean(entry != null)
        entry ?: return
        writeString(entry.uuid.toString())
        writeMedicationDetails(entry.details)
        writeNullableDouble(entry.dosageMgAsEstradiol)
        writeNullableString(entry.sourceGroupUuid?.toString())
        writeNullableString(entry.scheduleTimeUuid?.toString())
        writeLong(entry.appliedAt.toEpochMilli())
        writeString(entry.appliedAtTimeZoneId)
        writeNullableString(entry.scheduledFor?.toString())
        writeInt(entry.count)
    }

    private fun DataInputStream.readMedicationLogEntry(): MedicationLogEntry? {
        if (!readBoolean()) {
            return null
        }
        return MedicationLogEntry(
            uuid = UUID.fromString(readString()),
            details = readMedicationDetails(),
            dosageMgAsEstradiol = readNullableDouble(),
            sourceGroupUuid = readNullableString()?.let(UUID::fromString),
            scheduleTimeUuid = readNullableString()?.let(UUID::fromString),
            appliedAt = Instant.ofEpochMilli(readLong()),
            appliedAtTimeZoneId = readString(),
            scheduledFor = readNullableString()?.let(LocalDateTime::parse),
            count = readInt(),
        )
    }

    private fun DataOutputStream.writeMedicationDetails(details: MedicationDetails) {
        writeString(details.category.name)
        writeString(details.applicationType.name)
        writeMedicationSelection(details.selection)
        writeMedicationDose(details.dose)
        writeString(details.gelApplicationArea.name)
        writeString(details.customDoseUnit.storageValue)
    }

    private fun DataInputStream.readMedicationDetails(): MedicationDetails {
        return MedicationDetails(
            category = MedicationCategory.fromStorageValue(readString()),
            applicationType = MedicationApplicationType.fromStorageValue(readString()),
            selection = readMedicationSelection(),
            dose = readMedicationDose(),
            gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(readString()),
            customDoseUnit = MedicationDoseUnit.fromStorageValue(readString()),
        )
    }

    private fun DataOutputStream.writeMedicationSelection(selection: MedicationSelection) {
        writeString(selection.kind.name)
        when (selection) {
            is MedicationSelection.Catalog -> writeString(selection.medicationKey.name)
            is MedicationSelection.Custom -> writeString(selection.medicationName)
        }
    }

    private fun DataInputStream.readMedicationSelection(): MedicationSelection {
        return when (MedicationSelectionKind.fromStorageValue(readString())) {
            MedicationSelectionKind.CATALOG -> MedicationSelection.Catalog(
                medicationKey = checkNotNull(MedicationKey.fromStorageValue(readString()))
            )

            MedicationSelectionKind.CUSTOM -> MedicationSelection.Custom(
                medicationName = readString()
            )
        }
    }

    private fun DataOutputStream.writeMedicationDose(dose: MedicationDose) {
        writeString(dose.kind.name)
        when (dose) {
            is MedicationDose.MgAsMedicine -> writeDouble(dose.valueMg)
            is MedicationDose.GelEquivalentEstradiolMg -> writeDouble(dose.valueMg)
            is MedicationDose.GelPercentAndWeight -> {
                writeDouble(dose.percent)
                writeDouble(dose.weightGrams)
            }
            is MedicationDose.PatchTotalMg -> writeDouble(dose.valueMg)
            is MedicationDose.PatchReleaseRateMcgPerDay -> writeDouble(dose.valueMcgPerDay)
            MedicationDose.None -> Unit
        }
    }

    private fun DataInputStream.readMedicationDose(): MedicationDose {
        return when (MedicationDoseKind.fromStorageValue(readString())) {
            MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(readDouble())
            MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> MedicationDose.GelEquivalentEstradiolMg(
                readDouble()
            )
            MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> MedicationDose.GelPercentAndWeight(
                percent = readDouble(),
                weightGrams = readDouble(),
            )
            MedicationDoseKind.PATCH_TOTAL_MG -> MedicationDose.PatchTotalMg(readDouble())
            MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> MedicationDose.PatchReleaseRateMcgPerDay(
                readDouble()
            )
            MedicationDoseKind.NONE -> MedicationDose.None
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

private const val SNAPSHOT_CODEC_VERSION = 3
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
