package com.mkx.hrttracker.model.medication

enum class MedicationGroupColorKey {
    ROSE,
    ORCHID,
    INDIGO,
    TEAL,
    EMERALD,
    AMBER,
    CORAL,
    SLATE;

    companion object {
        val assignmentOrder: List<MedicationGroupColorKey> = listOf(
            ROSE,
            ORCHID,
            INDIGO,
            TEAL,
            EMERALD,
            AMBER,
            CORAL,
            SLATE,
        )

        fun fromStorageValue(value: String?): MedicationGroupColorKey {
            return entries.firstOrNull { it.name == value } ?: ROSE
        }
    }
}

fun nextAvailableMedicationGroupColor(
    usedColors: Collection<MedicationGroupColorKey>,
    seed: Int,
): MedicationGroupColorKey {
    MedicationGroupColorKey.assignmentOrder.firstOrNull { colorKey ->
        colorKey !in usedColors
    }?.let { return it }

    return MedicationGroupColorKey.assignmentOrder[
        Math.floorMod(seed, MedicationGroupColorKey.assignmentOrder.size)
    ]
}
