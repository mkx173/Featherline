# PK differences

HRTTracker's pharmacokinetic math borrows the model shape and many
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
[`PkCatalog`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
trace back to that reference. Anyone wanting the math itself —
compartment equations, parameter provenance, literature anchors —
should read the upstream README and its `pk_research/` workspace.

## What HRTTracker reuses

- **Model shape.** Two parallel depots → esterase hydrolysis →
  clearance for injections; first-order Bateman absorption for oral,
  gel, and fallback patches; constant-rate input for labelled patches;
  dual-pathway (fast mucosal + slow swallowed-oral) for sublingual.
  Implemented in
  [`ThreeCompartmentModel`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L849)
  and consumed by
  [`PkSimulationEngine`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L586).
- **Route catalog and compound metadata.** `PkRoute`, `PkCompound`,
  and `PkCatalog`'s `compounds` / `twoPartDepot` / `formationFraction`
  / `hydrolysisK2` / `oralKAbs` / `oralBioavailability` mirror the
  upstream catalog for the routes it supports.
- **Population-average parameter values.** Clearance constants
  (`kClear`, `kClearInjection`), volume-of-distribution (`vdPerKg =
  2.0` for both hormones), gel/patch absorption rates, the standard
  sublingual θ, and the per-compound `k1Fast` / `k1Slow` / `k2` /
  formation fractions are taken from upstream's anchored parameters.

## What HRTTracker changes

- **Single-hormone simulation runtime.** Upstream's catalog and
  simulator drive both estradiol and testosterone end-to-end.
  HRTTracker keeps the testosterone constants in
  [`PkCatalog`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
  for parity but wires only the estradiol path through
  [`toEstradiolPkDoseEvent`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1069),
  `buildEstradiolEvents`, and
  [`simulateMainEstradiolTrend`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L314);
  the testosterone surface stays dormant until the planned PK engine
  swap lands.
- **Planned-dose projection.** Upstream simulates the doses you give
  it. HRTTracker synthesizes virtual future-dose events for
  unfulfilled scheduled slots via
  [`buildEstradiolPkSimulationEntries`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkPlannedEntries.kt#L25)
  so the home chart can show a "if you take everything on schedule"
  curve to the right of the prediction marker. The `isPlanned` flag
  propagates to dose markers so the UI can distinguish logged from
  projected doses.
- **Adaptive chart-sample placement.** Upstream's `SimulationEngine`
  is a math reference, not a chart engine, so it samples on a single
  fixed step. HRTTracker drives a seven-day chart, so it mixes a dense
  backbone (`MainChartDenseSampleIntervalHours = 0.1 h`) with exact
  samples at the prediction instant, the previous-day instant, every
  logged-event instant, and ten post-dose offsets
  (`MainChartPostDoseSampleOffsetsHours`: 0.25 / 0.5 / 1 / 2 / 4 / 6 /
  8 / 12 / 24 / 48 hours).

  The result is a non-uniform sampling that resolves the post-dose
  peak without paying for dense sampling across the full seven-day
  window. The trend
  ([`mainChartSampleTimeH`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L490))
  and projection
  ([`mainProjectionSampleTimeH`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L533))
  paths share the strategy but differ in which "exact" instants they
  pin.
- **Chart-window rounding contract.** `simulateMainEstradiolProjection`
  rounds the window to local midnight in `zoneId` (start = today minus
  three days at 00:00, end = today plus `futureDays` at 00:00).
  `HomeSnapshotRepository.snapshotUsabilityFailure` independently
  recomputes those bounds and rejects cached snapshots whose stored
  window doesn't match. Upstream has no such contract because it has
  no cache.
- **Projection cache bundled into home snapshot.** The projection
  result is bundled into `HomeSnapshotRecord` alongside the
  plan-and-fulfillment cache, so every home-data mutation triggers a
  PK re-simulation even when no PK input changed. The leaky seam and
  its fallback path (`MainViewModel` re-simulates directly when the
  cached expiry is on-or-before the live `now`) are documented in
  [`architecture.md#home-snapshot-and-pk-projection-cache`](architecture.md#home-snapshot-and-pk-projection-cache).
  Upstream has no equivalent because it has no persistent home
  snapshot.
- **Active-mg conversion on the data path.** Upstream resolves dose
  events directly inside its simulator. HRTTracker keeps the active-mg
  conversion in `toEstradiolPkDoseEvent` and `activeEstradiolDoseMg`,
  pulling per-compound `activeFactor` from `PkCatalog.compounds`
  (active-vs-prodrug molecular-weight ratios) and passing already-mg-as-E2
  values into the engine. The route-aware split between
  [`medicineDoseMg()`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1164)
  (injections) and
  [`activeEstradiolDoseMg()`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1136)
  (everything else) lives at the call boundary, not inside the engine.
- **Duplicated molecular-weight constants.** Estradiol
  molecular-weight constants live in `PkCatalog.compounds` and again
  in
  [`EstradiolEquivalentCalculator`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/data/repository/EstradiolEquivalentCalculator.kt),
  which the medication UI uses to show dose-equivalence outside of
  any PK simulation. The two copies disagree at the second decimal
  (`PkCatalog` uses 272.38 / 376.5 / 356.5 / 396.58 / 384.56;
  `EstradiolEquivalentCalculator` uses 272.4 / 376.4 / 356.5 / 396.6 /
  384.5). The disagreement is below the resolution any user cares
  about, but the redundancy is flagged — both copies collapse into
  the planned PK engine.
- **Default gel application area baked in.** `DefaultGelAreaCm2 =
  750.0` is set at the dose-event factory; upstream's catalog exposes
  area-related parameters per event. HRTTracker doesn't expose this in
  the UI because the current gel model doesn't read `areaCm2` —
  `PkParameterResolver`'s `GEL` branch uses `core.gelK1` and
  `core.gelFMax` directly. The field is kept so a future skin-depot
  gel model has somewhere to put per-event area without a data-class
  bump.
- **Sublingual θ is fixed, not user-settable.** Upstream lets the user
  configure the sublingual θ — the fraction of a sublingual dose
  absorbed through the buccal mucosa (fast pathway) versus swallowed
  and absorbed orally (slow pathway). HRTTracker's
  [`PkDoseEvent`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L73)
  carries an optional `sublingualTheta: Double?` field for parity, but
  nothing in the UI ever sets it. The
  [`PkParameterResolver.SUBLINGUAL`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L820)
  branch always falls back to the catalog default
  [`PkCatalog.standardSublingualTheta = 0.11`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1204).
  Real per-person variation in mucosal-vs-swallowed fraction can be
  substantial; HRTTracker does not currently expose it for tuning.

## What HRTTracker plans to change

The PK engine swap gates three deferred refactors, all enumerated in
[`architecture.md#known-limitations-and-planned-refactors`](architecture.md#known-limitations-and-planned-refactors):

- Consolidate the fragmented PK math (constants, three-compartment
  simulation, dose-equivalence) so `PkCatalog` and
  [`EstradiolEquivalentCalculator`](https://github.com/mkx173/HRTTracker/blob/c300e0930621a1202a31ffc711fb27d80afd7655/app/src/main/java/com/mkx/hrttracker/data/repository/EstradiolEquivalentCalculator.kt)'s
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
