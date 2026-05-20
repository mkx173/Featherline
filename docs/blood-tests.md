# Blood tests

How Featherline models lab analytes, converts between units without
drift, and validates unit choices at the layer boundary.
The whole subsystem lives in
[`model/bloodtest/`](https://github.com/mkx173/Featherline/tree/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/model/bloodtest)
(three files, ~234 LOC).

## Analyte catalog

[`BloodTestCatalog.kt`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/model/bloodtest/BloodTestCatalog.kt)
exposes a single `object BloodTestCatalog` keyed by two enums and one
data class:

- `BloodAnalyteKey` — the six builtin analytes: `E2`, `T`, `PROG`,
  `PRL`, `FSH`, `LH`. Each has a stable `storageValue` used in Room
  rows, DataStore keys, and backup JSON.
- `BloodUnitKey` — the eight builtin units: `PG_ML`, `NG_ML`,
  `NG_DL`, `PMOL_L`, `NMOL_L`, `MIU_L`, `MIU_ML`, `IU_L`. Same
  stable-storage pattern.
- `BloodAnalyteDefinition` — the per-analyte record: `canonicalUnit`
  plus a `factorsToCanonical: Map<BloodUnitKey, Double>`.

The builtin catalog pins one canonical unit per analyte:

| Analyte | Canonical unit | Other allowed units |
| --- | --- | --- |
| `E2` | `PG_ML` | `PMOL_L`, `NG_DL` |
| `T` | `NG_DL` | `NMOL_L`, `NG_ML` |
| `PROG` | `NG_ML` | `NMOL_L` |
| `PRL` | `NG_ML` | `MIU_L` |
| `FSH` | `MIU_ML` | `IU_L` |
| `LH` | `MIU_ML` | `IU_L` |

The factor-table mappings live only in the catalog. Adding a new
**unit** for an existing analyte really is a single-place change —
extend the `BloodUnitKey` enum and add one row to the analyte's
`factorsToCanonical` map. Adding a new **analyte** is larger:
beyond the catalog `definitions` map and the `BloodAnalyteKey` enum,
`CalibrationEditorViewModel` declares the ordered analyte list,
`CalibrationScreen` maps each analyte to its display-name string
resource and to a `CalibrationCanonicalTarget`, and the calibration
default-unit maps are duplicated across `CalibrationScreen` /
`CalibrationEditorScreen` / `CalibrationUnitsScreen`. Each touch is
mechanical, but "single place" is true only for unit additions; treat
the calibration UI surfaces as the second seat.

## Factor-table pattern

Every definition stores its conversions as `factorsToCanonical` in one
consistent direction: `canonical = factor * input`. The init block on
`BloodAnalyteDefinition` enforces that the canonical unit appears in
its own table with a factor of exactly `1.0` — the invariant
`factorsToCanonical[canonicalUnit] == 1.0` is checked at construction
time, so a malformed definition fails fast at app start.

`toCanonical(analyte, value, unit)` and
`fromCanonical(analyte, canonicalValue, unit)` are one-liners that
look up the factor and multiply or divide; division reuses the same
factor instead of storing a second table, so the two directions cannot
drift. `isUnitAllowed` and `canonicalUnitFor` answer the two questions
the rest of the codebase asks about a unit choice.

The shape pays off in two places. First, the conversion logic itself
stays in one place — there are no scattered `if (unit == X)` arms in
repositories or UI code (calibration's per-analyte defaults and
display strings are a UI seat, not a conversion seat). Second,
**canonical-value persistence** (the `canonicalValue` column on
[`BloodTestResultEntity`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt#L70))
lets the trend chart sort and compare across rows that were entered in
different units without re-converting on every read.

## Custom analytes

Users can define their own analytes outside the catalog —
[`CustomBloodAnalyteEntity`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt#L33)
holds an abbreviation, a display name, and a free-text `unitLabel`.
See the entity blurb in
[`data-model.md`](data-model.md#custombloodanalyteentity) for the
schema and FK shape.

Custom analytes have no factor table, so there is nothing to convert
against. The write path in
[`BloodTestRepository.kt`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/data/repository/BloodTestRepository.kt)
splits on the result-input sealed type: the
`BloodTestResultInput.Builtin` branch calls `BloodTestCatalog.toCanonical(...)`,
and the `BloodTestResultInput.Custom` branch sets
`canonicalValue = value` directly and copies the custom analyte's
`unitLabel` into `unitSnapshot`. The catalog cannot answer for custom
analytes, so the split lives one layer up at the repository.

The trade-off: trend math on custom analytes is
unit-agnostic and only meaningful row-to-row within the same custom
analyte, since two rows for the same custom analyte are assumed to
share their `unitLabel`.

## `AllowedAnalyteUnit` validated type

[`AllowedAnalyteUnit`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/model/bloodtest/AllowedAnalyteUnit.kt)
is a `data class` with a private constructor and a smart constructor
`AllowedAnalyteUnit.of(analyte, unit)` that requires
`BloodTestCatalog.isUnitAllowed(analyte, unit)`. Once you hold an
`AllowedAnalyteUnit`, the type system says the pair has already been
validated against the catalog.

It crosses layer boundaries at exactly the points where an
out-of-catalog pair would be a bug:

- [`SettingsRepository.setCalibrationDefaultUnit`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/data/repository/SettingsRepository.kt)
  and `setHomeE2DisplayUnit` take `AllowedAnalyteUnit` rather than a
  raw `(BloodAnalyteKey, BloodUnitKey)` tuple — UI cannot push a
  nonsense pair into DataStore.
- `SettingsRepository.restoreSettings` accepts `Set<AllowedAnalyteUnit>`
  and a single `AllowedAnalyteUnit` for the E2 display unit. Backup
  restore reuses this entry point.
- [`BackupRestoreService.toValidatedSettings`](https://github.com/mkx173/Featherline/blob/c13fb98e8109fec775ea4722794475945d5165bb/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)
  decodes raw storage strings from a backup snapshot and constructs
  `AllowedAnalyteUnit.of(...)` per entry; an unsupported pair throws
  during validation, before any Room write happens.

Two pieces of unit handling are deliberately left in
`SettingsRepository`, not lifted into the validated type:

- **Read-path defense.** `preferencesToStoredSettingsState` decodes
  each persisted unit through `BloodUnitKey.fromStorageValue` and then
  re-checks `BloodTestCatalog.isUnitAllowed(...)`, dropping the entry
  (or falling back to `canonicalUnitFor(BloodAnalyteKey.E2)` for the
  home E2 display unit) if the persisted code no longer parses or is
  no longer allowed. This is the safety net for storage written by an
  older app version whose catalog disagrees with the current catalog.
- **Default-marker encoding.** `setCalibrationDefaultUnit` and
  `setHomeE2DisplayUnit` compare the chosen unit against
  `BloodTestCatalog.canonicalUnitFor(analyte)` and *remove* the
  DataStore key instead of writing it when the two match. Absence of a
  key encodes "use the canonical default," which keeps DataStore
  smaller and lets a catalog change to the canonical unit take effect
  without a migration.
