package com.mkx.hrttracker.model.medication

enum class MedicationGroupColorKey {
    ROSE,
    CORAL,
    AMBER,
    CITRON,
    SAGE,
    TEAL,
    SKY,
    INDIGO,
    VIOLET,
    PLUM;

    companion object {
        val assignmentOrder: List<MedicationGroupColorKey> = listOf(
            ROSE,
            TEAL,
            AMBER,
            INDIGO,
            SAGE,
            PLUM,
            CITRON,
            SKY,
            CORAL,
            VIOLET
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
