# PK differences

Featherline's pharmacokinetic math borrows the model shape and many
parameter values from
[`LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test`](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test).
This page lists what's the same, what's different, and why; it does
not restate the math itself.

## Upstream reference

The upstream ships analytical PK models for estradiol and testosterone across
injection / patch / gel / oral / sublingual routes, a unified parameter
catalog (`PKSharedCatalog.json`), and Swift implementations
(`PKparameter.swift`, `PKcore.swift`, `SimulationEngine`) targeting iOS
and watchOS. The math, the route catalog, and the population-average
parameters in
[`PkCatalog`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
trace back to that reference. Anyone wanting the math itself —
compartment equations, parameter provenance, literature anchors —
should read the upstream README and its `pk_research/` workspace.

## What Featherline reuses

- **Model shape.** Two parallel depots → esterase hydrolysis →
  clearance for injections; first-order Bateman absorption for oral,
  gel, and fallback patches; constant-rate input for labelled patches;
  dual-pathway (fast mucosal + slow swallowed-oral) for sublingual.
  Implemented in
  [`ThreeCompartmentModel`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L849)
  and consumed by
  [`PkSimulationEngine`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L586).
- **Route catalog and compound metadata.** `PkRoute`, `PkCompound`,
  and `PkCatalog`'s `compounds` / `twoPartDepot` / `formationFraction`
  / `hydrolysisK2` / `oralKAbs` / `oralBioavailability` mirror the
  upstream catalog for the routes it supports.
- **Population-average parameter values.** Clearance constants
  (`kClear`, `kClearInjection`), volume-of-distribution (`vdPerKg =
  2.0` for both hormones), gel/patch absorption rates, the standard
  sublingual θ, and the per-compound `k1Fast` / `k1Slow` / `k2` /
  formation fractions are taken from upstream's anchored parameters.

## What Featherline changes

- **Single-hormone simulation runtime.** Upstream's catalog and
  simulator drive both estradiol and testosterone end-to-end.
  Featherline keeps the testosterone constants in
  [`PkCatalog`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
  for parity but wires only the estradiol path through
  [`toEstradiolPkDoseEvent`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1069),
  `buildEstradiolEvents`, and
  [`simulateMainEstradiolTrend`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L314);
  the testosterone surface stays dormant until the planned PK engine
  swap lands.
- **Planned-dose projection.** Upstream simulates the doses you give
  it. Featherline synthesizes virtual future-dose events for
  unfulfilled scheduled slots via
  [`buildEstradiolPkSimulationEntries`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkPlannedEntries.kt#L25)
  so the home chart can show a "if you take everything on schedule"
  curve to the right of the prediction marker. The `isPlanned` flag
  propagates to dose markers so the UI can distinguish logged from
  projected doses.
- **Option-aware chart-sample placement.** Upstream's
  `SimulationEngine` is a math reference, not a chart engine, so it
  samples on a single fixed step. Featherline drives a user-selectable
  home chart through `HomeE2ChartWindowOption`: `SEVEN_DAYS` renders a
  3-day-past / 4-day-future window on a 0.1 h dense interval, while
  `THIRTY_DAYS` renders a 16-day-past / 14-day-future window on a
  budgeted sampler (`segmentCount = 2240`) with extra post-dose
  offsets.

  The seven-day path preserves the original six-minute resolution.
  The 30-day path trades uniform density for a bounded sample budget
  and then pins absorption-phase offsets after each dose (0.25 h
  through 48 h) so fast-Tmax routes such as oral and sublingual do not
  underplot their local peaks. Both options also pin the prediction
  instant, the previous-day instant, and every logged-event instant.
  The trend
  ([`mainChartSampleTimeH`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L490))
  and projection
  ([`mainProjectionSampleTimeH`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L533))
  paths share the strategy but differ in which "exact" instants they
  pin.
- **Option-aware chart-window rounding contract.**
  `simulateMainEstradiolProjection` rounds the projection-cache window
  to local midnight in `zoneId`: start = today minus `option.pastDays`
  at 00:00, end = today plus `option.projectionFutureDays()` at
  00:00. `projectionFutureDays()` adds a 10-day forward buffer beyond
  the visible future span, so the 30-day chart stores a 40-day
  projection while rendering only the 30-day visible window.
  `HomeSnapshotRepository.snapshotUsabilityFailure` independently
  recomputes the visible window and rejects cached snapshots whose
  coverage or sampling fingerprint (`chartWindowHours`, dense policy,
  post-dose-offset flag) does not match the selected option. Upstream
  has no such contract because it has no cache.
- **Projection cache bundled into home snapshot.** Two projection
  records are bundled into `HomeSnapshotRecord` alongside the
  plan-and-fulfillment cache: `pkProjection` (the chart's
  option-sized projection — 7-day or 30-day visible window with the
  forward buffer) and `widgetPkProjection` (a smaller projection
  sized for the widget's E2 estimate, which has its own validity
  window). Every home-data mutation re-simulates both, even when no
  PK input changed. The leaky seam and its fallback path
  (`MainViewModel` re-simulates the chart projection directly when
  the cached expiry is on-or-before the live `now`) are documented
  in
  [`architecture.md#home-snapshot-and-pk-projection-cache`](architecture.md#home-snapshot-and-pk-projection-cache);
  the widget consumes `widgetPkProjection` via
  [`WidgetSnapshotBuilder`](https://github.com/mkx173/Featherline/blob/642ffa739a76211a3e9dd422d66f329296055bf2/app/src/main/java/com/mkx/hrttracker/widget/WidgetSnapshotBuilder.kt)
  with its own expiry check on the widget render path. Upstream has
  no equivalent because it has no persistent home snapshot.
- **Active-mg conversion on the data path.** Upstream resolves dose
  events directly inside its simulator. Featherline computes the
  per-event active-mg-as-E2 upstream of the simulator in
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/8e46ab59d3328a389c20e588bd1e62174dcb8b19/app/src/main/java/com/mkx/hrttracker/data/repository/DoseInstructionCalculator.kt),
  which resolves a `Medicine`'s `MedicinePreparation` +
  `DoseInstruction` into mg-as-E2 using the calculator's own
  per-compound molecular-weight constants and persists it on the log
  row as `equivalentE2Mg`. Inside the simulator,
  [`pkDoseAmounts`](https://github.com/mkx173/Featherline/blob/8e46ab59d3328a389c20e588bd1e62174dcb8b19/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1106)
  prefers the snapshotted value for non-patch routes (falling back to
  `DoseInstructionCalculator.perUnitEquivalentE2Mg` for planned
  entries or legacy rows that have no snapshot) and reads the patch
  specification directly for `PATCH_APPLY` / `PATCH_REMOVE`; the
  engine's `formationFraction` values are calibrated against
  active-E2 input, matching upstream's convention. `PkCatalog`'s
  per-compound `activeFactor` is *not* applied at dose-event
  construction or in the current runtime simulation path; equivalent-E2
  input comes from the snapshotted value or `DoseInstructionCalculator`.
- **Duplicated molecular-weight constants.** Estradiol
  molecular-weight constants live in `PkCatalog.compounds` and again
  in
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/8e46ab59d3328a389c20e588bd1e62174dcb8b19/app/src/main/java/com/mkx/hrttracker/data/repository/DoseInstructionCalculator.kt),
  which log creation uses to snapshot `equivalentE2Mg` and planned PK
  entries use to rederive it when no snapshot exists. Medication UI
  code also uses the calculator for raw dose amount and patch
  release-rate display, but not for a dose-equivalence label. The two
  copies disagree at the second decimal
  (`PkCatalog` uses 272.38 / 376.5 / 356.5 / 396.58 / 384.56;
  `DoseInstructionCalculator` uses 272.4 / 376.4 / 356.5 / 396.6 /
  384.5). The disagreement is below the resolution any user cares
  about, but the redundancy is flagged — both copies collapse into
  the planned PK engine.
- **Default gel application area baked in.** `DefaultGelAreaCm2 =
  750.0` is set at the dose-event factory; upstream's catalog exposes
  area-related parameters per event. Featherline doesn't expose this in
  the UI because the current gel model doesn't read `areaCm2` —
  `PkParameterResolver`'s `GEL` branch uses `core.gelK1` and
  `core.gelFMax` directly. The field is kept so a future skin-depot
  gel model has somewhere to put per-event area without a data-class
  bump.
- **Sublingual θ is fixed, not user-settable.** Upstream lets the user
  configure the sublingual θ — the fraction of a sublingual dose
  absorbed through the buccal mucosa (fast pathway) versus swallowed
  and absorbed orally (slow pathway). Featherline's
  [`PkDoseEvent`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L73)
  carries an optional `sublingualTheta: Double?` field for parity, but
  nothing in the UI ever sets it. The
  [`PkParameterResolver.SUBLINGUAL`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L820)
  branch always falls back to the catalog default
  [`PkCatalog.standardSublingualTheta = 0.11`](https://github.com/mkx173/Featherline/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1204).
  Real per-person variation in mucosal-vs-swallowed fraction can be
  substantial; Featherline does not currently expose it for tuning.

## What Featherline plans to change

The PK engine swap gates three deferred refactors, all enumerated in
[`architecture.md#known-limitations-and-planned-refactors`](architecture.md#known-limitations-and-planned-refactors):

- Consolidate the fragmented PK math (constants, three-compartment
  simulation, dose-equivalence) so `PkCatalog` and
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/8e46ab59d3328a389c20e588bd1e62174dcb8b19/app/src/main/java/com/mkx/hrttracker/data/repository/DoseInstructionCalculator.kt)'s
  overlapping molecular-weight constants collapse into one source of
  truth.
- Untangle the projection cache from `HomeSnapshotRepository` so PK
  re-simulation isn't triggered by unrelated home mutations.
- Bring per-user PK calibration back. The EKF draft was tested and
  dropped from `main` to wait for the new engine's calibration shape;
  the reference is parked outside this repo's history.

## Out of scope

- **The PK math itself.** Compartment equations, parameter
  derivations, and the literature anchors for each constant live in
  the upstream README and its `pk_research/` workspace.
- **Personal-PK calibration.** The drafted-and-paused EKF code lives
  outside this repo; it isn't tracked in `docs/`.
- **Testosterone as a user-facing simulation.** `PkHormone.TESTOSTERONE`
  exists in the catalog with full parameters, but no user-logged
  category currently produces testosterone PK events, so testosterone
  trends are not rendered.
