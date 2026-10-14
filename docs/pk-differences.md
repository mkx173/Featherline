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
[`PkCatalog`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
trace back to that reference. Anyone wanting the math itself —
compartment equations, parameter provenance, literature anchors —
should read the upstream README and its `pk_research/` workspace.

## What Featherline reuses

- **Model shape.** Two parallel depots → esterase hydrolysis →
  clearance for injections; first-order Bateman absorption for oral,
  gel, and fallback patches; constant-rate input for labelled patches;
  dual-pathway (fast mucosal + slow swallowed-oral) for sublingual.
  Implemented in
  [`ThreeCompartmentModel`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L849)
  and consumed by
  [`PkSimulationEngine`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L586).
- **Route catalog and compound metadata.** `PkRoute`, `PkCompound`,
  and `PkCatalog`'s `compounds` / `twoPartDepot` / `formationFraction`
  / `hydrolysisK2` / `oralKAbs` / `oralBioavailability` mirror the
  upstream catalog for the routes it supports.
- **Population-average parameter values.** Clearance constants
  (`kClear`, `kClearInjection`), volume-of-distribution (`vdPerKg =
  2.0` for both hormones), and the
  per-compound `k1Fast` / `k1Slow` / `k2` / formation fractions are
  taken from upstream's anchored parameters, except the formation
  fractions, which share one value described below, and the cypionate
  depot rates. There are two more exceptions.
  **Estradiol undecylate (EU)** takes its depot parameters from
  [`Transmtf-HRT-Tracker`](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker)
  (the "im-depot-v1" model), because the LaoZhong upstream has no EU
  model. **Oral and sublingual estradiol** use Featherline's own
  values, described below.

## What Featherline changes

- **Single-hormone simulation runtime.** Upstream's catalog and
  simulator drive both estradiol and testosterone end-to-end.
  Featherline keeps the testosterone constants in
  [`PkCatalog`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1203)
  for parity but wires only the estradiol path through
  [`toEstradiolPkDoseEvent`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1069),
  `buildEstradiolEvents`, and
  [`simulateMainEstradiolTrend`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L314);
  the testosterone surface stays dormant until the planned PK engine
  swap lands.
- **Planned-dose projection.** Upstream simulates the doses you give
  it. Featherline synthesizes virtual future-dose events for
  unfulfilled scheduled slots via
  [`buildEstradiolPkSimulationEntries`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkPlannedEntries.kt#L25)
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
  ([`mainChartSampleTimeH`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L490))
  and projection
  ([`mainProjectionSampleTimeH`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L533))
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
  [`WidgetSnapshotBuilder`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/widget/WidgetSnapshotBuilder.kt)
  with its own expiry check on the widget render path. Upstream has
  no equivalent because it has no persistent home snapshot.
- **Active-mg conversion on the data path.** Upstream resolves dose
  events directly inside its simulator. Featherline computes the
  per-event active-mg-as-E2 upstream of the simulator in
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/DoseInstructionCalculator.kt),
  which resolves a `Medicine`'s `MedicinePreparation` +
  `DoseInstruction` into mg-as-E2 using the calculator's own
  per-compound molecular-weight constants and persists it on the log
  row as `equivalentE2Mg`. Inside the simulator,
  [`pkDoseAmounts`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1106)
  prefers the snapshotted value for non-patch routes (falling back to
  `DoseInstructionCalculator.perUnitEquivalentE2Mg` for planned
  entries or legacy rows that have no snapshot) and reads the patch
  specification directly for `PATCH_APPLY` / `PATCH_REMOVE`; the
  engine's `formationFraction` values are calibrated against
  active-E2 input, matching upstream's convention. `PkCatalog`'s
  per-compound `activeFactor` is *not* applied at dose-event
  construction or in the current runtime simulation path; equivalent-E2
  input comes from the snapshotted value or `DoseInstructionCalculator`.
- **Imported injection/gel preparations skip fabricated package
  geometry.** External tracker imports do not know vial concentration,
  vial volume, gel sachet weight, or gel concentration percent. Rather
  than inventing those fields, Featherline stores imported injection
  and gel support medicines as `MedicinePreparation.ImportedInjection`
  and `MedicinePreparation.ImportedGel`. `DoseInstructionCalculator`
  reads the administered/applied mg directly for whole-unit imported
  logs, converts imported injection esters to E2-equivalent mg with the
  same molecular-weight table as other logged doses, and treats
  imported gel as already-applied estradiol. `PkSimulation` maps these
  preparations directly to `PkCompound` (`ImportedInjection.ester` or
  `E2` for gel), so imported rows feed the simulator without requiring
  a `Catalog` medicine selection.
- **Duplicated molecular-weight constants.** Estradiol
  molecular-weight constants live in `PkCatalog.compounds` and again
  in
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/DoseInstructionCalculator.kt),
  which log creation uses to snapshot `equivalentE2Mg` and planned PK
  entries use to rederive it when no snapshot exists. Medication UI
  code also uses the calculator for raw dose amount and patch
  release-rate display, but not for a dose-equivalence label. The two
  copies disagree at the second decimal
  (`PkCatalog` uses 272.38 / 376.5 / 356.5 / 396.58 / 384.56 / 440.66;
  `DoseInstructionCalculator` uses 272.4 / 376.4 / 356.5 / 396.6 /
  384.5 / 440.66 — the estradiol undecylate value is the one that
  agrees across both copies). The disagreement is below the resolution any user cares
  about, but the redundancy is flagged — both copies collapse into
  the planned PK engine.
- **Default gel application area baked in.** `DefaultGelAreaCm2 =
  750.0` is set at the dose-event factory; upstream's catalog exposes
  area-related parameters per event. Featherline doesn't expose this in
  the UI because the current gel model doesn't read `areaCm2` —
  `PkParameterResolver`'s `GEL` branch uses `core.gelK1` and
  `core.gelFMax` directly. The field is kept so a future skin-depot
  gel model has somewhere to put per-event area without a data-class
  bump. Järvinen 1997 gives the area dependence to wire when that
  happens: `F = gelFMax × (1 − 0.31 ln(area / 750))` over 200–1,600 cm².
- **Sublingual θ is fixed, not user-settable.** Upstream lets the user
  configure the sublingual θ — the fraction of a sublingual dose
  absorbed through the buccal mucosa (fast pathway) versus swallowed
  and absorbed orally (slow pathway). Featherline's
  [`PkDoseEvent`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L73)
  carries an optional `sublingualTheta: Double?` field for parity, but
  nothing in the UI ever sets it. The
  [`PkParameterResolver.SUBLINGUAL`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L820)
  branch always falls back to the catalog default
  [`PkCatalog.standardSublingualTheta = 0.08`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/pk/PkSimulation.kt#L1204).
  Real per-person variation in mucosal-vs-swallowed fraction can be
  substantial; Featherline does not currently expose it for tuning.
- **Oral estradiol is refit to blood levels.** Upstream's
  oral E2 values (`oralKAbs = 0.32`, `oralBioavailability = 0.03`)
  give a terminal half-life of about 2 h. Oral E2's is 13–20 h, because
  estrone sulfate keeps converting back to E2. With upstream's values,
  2 mg once daily falls to under 1 pg/mL by the next dose. Those
  near-zero troughs fall under the calibration floor, so trough results
  were set aside. Featherline uses `oralKAbs[E2] = 0.045` and
  `oralBioavailability[E2] = 0.030`. The slow absorption rate stands in
  for the estrone-sulfate reservoir and gives a single-dose peak at 6 h,
  an apparent half-life of 15 h and daily accumulation of 1.6×, which
  Aedo 1990 (1.53×) and Stadberg 1999 (1.4–1.6× after baseline
  correction) measured. The bioavailability is really F/CL at the
  catalog's clearance: Kuhnz 1993, Price 1997, Timmer 1999 and Stadberg
  1999 give CL/F of 26–31 L/h/kg, Cattani 2023 (LC-MS/MS, trans women)
  2.75%, Aedo 1990 2.8–3.6% and Düsterberg 1985 about 3%. The
  literature's "about 5%" is Kuhnz's figure against a clearance twice
  the catalog's. An earlier 0.043 was back-solved from the Zumenon 2 mg
  SmPC's steady-state figures (89 / 35 / 62.9 pg/mL, arithmetic means
  that include endogenous E2, cohort weight unstated), which now mark
  the top of the range. At
  70 kg, 2 mg once daily reaches 57 / 27 / 44 pg/mL (peak / trough /
  average); Cattani measured 62 / 35 / 42 on 2 mg valerate including
  endogenous E2. The swallowed part of a sublingual dose goes through
  this oral path. The derivation of every constant is in
  `notes/reports/Estradiol PK parameters final papers.md`.
- **The mouth-absorbed part of a sublingual dose clears faster than
  E2's own rate, and the swallowed part carries a small recycling
  share.** Upstream sends the mouth-absorbed part through E2's clearance
  (0.41/h), so a 1 mg dose stayed near its peak for hours. In Doll 2022,
  Price 1997, Burnier 1981 and Casper & Yen 1981, levels fall from their
  1 h value to about 0.4× by 2 h and 0.15× by 4 h, the same fall seen
  after an intravenous bolus (distribution plus clearance, not estrone
  conversion). Featherline uses `kElimSublingual = 1.2` for that part,
  keeps upstream's absorption rate, and sets θ, the share absorbed
  through the mucosa, to 0.08: a midpoint between the peak-matched 0.058
  (Fiet 1982, Casper & Yen 1981, Doll 2022) and the estrone-matched
  0.101 (Cirrincione 2021). Because every study puts the sublingual
  curve at or above the same-dose oral curve from 8 h on, and two limbs
  alone cannot, the swallowed limb also carries 0.4% of the dose at full
  bioavailability (`sublingualRecyclingShare`). At 70 kg a 1 mg dose
  peaks at 260 pg/mL at 0.7 h, is 122 at 2 h, 31 at 4 h and 19 at 8 h,
  and its 24 h exposure is 2.5× oral (Price measured 2.6×). Doll
  measured 120 above baseline at 1 h (LC-MS/MS, trans women); Price
  measured 441 (RIA, postmenopausal women). Cirrincione's LC-MS/MS
  steady-state median in trans women on 6 mg/day matches the model's
  interval median within 3%.
- **Estradiol valerate (EV) is modeled as E2 when swallowed or held
  under the tongue.** Doses are already converted to E2 content, and EV
  now uses E2's oral values and θ. Upstream used `oralKAbs[EV] = 0.05`
  and `oralBioavailability[EV] = 0.03`, and sent the mouth-absorbed part
  through the injection hydrolysis rate (`hydrolysisK2[EV] = 0.07`).
  In Wiegratz 2001, 2 mg of EV gave about 60 pg/mL at day 21, against
  80 for 2 mg of micronized E2. That is about the ratio of their E2
  content (0.76). The two products carried different progestins.
  Timmer 1999 found single doses of 1.5 mg E2 and 2 mg EV
  bioequivalent. No study has measured sublingual EV levels over time.
  TFS's [review](https://transfemscience.org/articles/sublingual-ev/)
  expects it to work like sublingual E2 at equal E2 content. In
  Düsterberg 1985, EV given into a vein was fully split into E2 within
  30 minutes.
- **Patches entered by total content are fitted to Estrana tape.** A
  patch logged by the drug it contains, with no release rate, is modeled
  as first-order absorption at `patchFallbackK1`. Upstream's `0.0075`
  has no stated source. Estrana tape is the only common patch labelled
  by total content. Its Japanese package insert reports that 0.72 mg
  worn 48 h holds 52.0–53.8 pg/mL from 8–12 h until removal, and
  52.5–60.5 pg/mL at 48 h on repeated use, in postmenopausal women. At
  `0.0075` the model gave 85–111 pg/mL at 55 kg. Featherline uses
  `0.0035`, which gives about 48–54 pg/mL. That is about 14% of the
  content delivered over 2 days. Patches labelled by release rate
  (µg/day) use the zero-order path instead.
- **All four esters share one formation fraction.** An injection's
  total E2 exposure, divided by the catalog's clearance (`kClear =
  0.41`, 2 L/kg), gives the share of the dose that reached the blood;
  because injections clear at a tenth of that rate, the share is 10× the
  formation fraction and no ester can exceed 0.100. Upstream's values
  put that share above 1 for EB (1.09), EC (1.17) and EN (1.20), which
  is impossible. Oriowo 1980, the only head-to-head study (5 mg each in
  one cohort and assay), gives per-mg exposures for EB, EV and EC within
  7% of each other (shares 0.72 / 0.82 / 0.75); Zhou 1998's cypionate
  table gives 0.65–0.88, Schiavon 1988's enanthate peaks favour 0.78 over
  0.89, and Garza-Flores 1994's idealised curves give 0.71–0.80, so
  Featherline uses `0.078` for EB, EV, EC and EN. Valerate is bracketed
  by Aedo 1985's measured peak (fits 0.062) below and Düsterberg &
  Nishino 1982's 4 mg curve (needs 0.09–0.10, and integrates to more E2
  than the dose) above. Estrannaise and TFS give 0.68–0.94; GAHT Plotter
  gives 0.81–1.55 and is not a valid exposure vote. The cypionate depot
  is refit to Zhou 1998 and Rahimy 1999 (`fracFast 0.15`, `k1Fast 0.08`,
  `k1Slow 0.0035`): upstream's two rates differed by 12%, so the depot
  could not peak on day 1–2 as every study that sampled day 1 found. The
  valerate and enanthate depots are unchanged; the two primary valerate
  curves disagree in opposite directions on its tail, and enanthate peaks
  within 7% of Schiavon's 6.3 days.
- **Gel bioavailability is the 0.06% product on its label area, and gel
  is absorbed faster.** Upstream's `gelFMax = 0.06` and `gelK1 = 0.022`
  have no stated source. Featherline keeps `gelFMax = 0.060`: the
  Oestrogel UK SmPC's day-12 average of 76.8 pg/mL at 1.5 mg/day and the
  Melbourne trans cohort's median of 77.6 pg/mL on the same product and
  dose (Maggacis 2025) agree within 1%, and Järvinen 1997 interpolates
  0.057–0.063 at 750 cm². Bioavailability falls with application area
  rather than product class: in Järvinen 1997 the same 1 mg of 0.1% gel
  gave 0.081 on 200 cm², 0.068 on 400 cm² and 0.038 spread over both
  thighs, so small-area 0.1% products read above 0.060 as labelled and
  near 0.035 only when spread widely. `areaCm2` is carried on every gel
  event but not applied. `gelK1 = 0.04` is a lumped skin-to-blood rate:
  the steady-state peak is 4–5 h on 200–400 cm² (Järvinen 1997), the
  single-dose peak 6–8 h (Järvinen 1999), and steady state arrives by
  the third daily dose as four labels state; upstream's 0.022 reached
  only 79% by 72 h. At 70 kg, 0.75 mg/day averages 33 pg/mL above
  baseline and 1.5 mg/day averages 65.

## How the estradiol constants were derived

Everything needed to recompute the values in `PkCatalog` from the
sources, without the local research notes.

**Formulas.** Concentration is amount divided by `vdPerKg × weight`.
Clearance is `CL = kClear × vdPerKg = 0.82 L/h/kg` (57.4 L/h at 70 kg,
1,378 L/day). For a daily route, steady-state average
`Cavg = F × daily E2 mg / (CL × 24 h)`, so a source's implied
`F = Cavg(drug-attributable, pg/mL) × 1e-6 × CL(L/h) × 24 / daily mg`
at the cohort's weight; for a single dose use its AUC and
`F = AUC(pg·h/mL) × 1e-6 × CL(L/h) / mg`, or `F = CL / (CL/F)` when the
paper reports an apparent oral clearance. Injections clear at
`kClearInjection = kClear / 10`, so the share of an injected dose that
reaches the blood is `10 × formationFraction` and no ester can exceed
0.100; a source's share is `AUC × 1e-6 × CL / E2-equivalent mg`, with
E2-equivalent mg = ester mg × 272.38 / ester molar mass (EB 376.5, EV
356.5, EC 396.58, EN 384.56, EU 440.66). The mouth-absorbed part of a
sublingual dose has an exposure-effective fraction of
`θ × kClear / kElimSublingual` (0.08 × 0.342 = 0.027), and the swallowed
part carries `(1 − θ) × F + sublingualRecyclingShare`. Every
bioavailability in the catalog is therefore `F / CL` at 0.82 L/h/kg;
anyone changing `kClear` or `vdPerKg` must rescale all of them. Weights
in brackets below are the cohort means where the paper gives one, else
the assumption used. `PkSimulationTest` holds runnable anchors for the
oral steady state, the sublingual tail, Estrana, gel and cypionate.

**Clearance and volume (`kClear 0.41`, `vdPerKg 2.0`).** Kuhnz, Blode
& Zimmermann 1999, table 2, lists eleven human intravenous estradiol
clearances in mL/min/kg: Hembree 1969 9.3, 11.9, 14.9 and 16.3 (male);
Anderson 1993 10.5 and 15.0; Düsterberg 1985 11.2; Longcope & Williams
1974 13.5; Longcope 1985 14.8; Kuhnz 1993 29.9 (cohort on a combined
pill); Gabrielsson 1995 30.0 (truncated sampling). The catalog's 0.82
L/h/kg is 13.7 mL/min/kg, fifth of eleven. The same chapter gives IV
terminal half-lives of 1.7 h (Kuhnz 1993) and 2.2 h (Düsterberg 1985),
so 0.41/h is at the fast end of 0.32–0.41/h. Düsterberg 1985 gives the
only terminal-slope volume, 2.04–2.25 L/kg; steady-state volumes
(Kuhnz 1993: 1.03–1.62 L/kg) are lower, so 2.0 is a modelling choice
that pairs with `kClear`. Kuhl 2005's 580 L/day/m² metabolic clearance
rate cites Kuhl 1990, which contains no clearance figure; it is not
used.

**Oral (`oralKAbs 0.045`, `oralBioavailability 0.030`).** Implied F at
0.82 L/h/kg: Kuhnz 1993, 4 mg single dose, AUC 2,290 pg·h/mL [62 kg],
CL/F 28.2 L/h/kg: 2.9% (the paper's own 4.9 ± 5.0% is the same data
against its measured clearance of 1.5–1.8 L/h/kg). Price 1997, 1 mg,
reported oral clearance 27.2 L/h/kg: 3.0%. Timmer 1999, 1.5 mg, AUC0-30
750 pg·h/mL [64 kg]: 2.6%. Stadberg 1999, 1 mg single dose,
baseline-corrected AUC0-24 2.14 nmol·h/L [66 kg]: 3.2%. Cattani 2023,
2 mg valerate daily (1.53 mg E2), LC-MS/MS, AUCτ 1,003 pg·h/mL [67 kg],
minus 10 pg/mL endogenous: 2.75%. Vervalcke 2024 (ENIGI), 4 mg valerate
daily, LC-MS/MS, AUC 1,684 pg·h/mL, minus 10: 2.7%. Aedo 1990, 2 mg
valerate daily, day-21 peak 79 pg/mL with a ~19 pg/mL unsubtracted
baseline [64 kg]: 2.8–3.6%. Düsterberg 1985, 2 mg valerate, IV-referenced:
"about 3%". The top of the 0.025–0.045 range is the uncorrected
immunoassay steady state: Zumenon 2 mg SmPC Cav 62.9 pg/mL (4.3% at
70 kg, 3.4–4.0% after a 5–10 pg/mL baseline), Stadberg day-28 AUC0-24
3.92 nmol·h/L uncorrected (5.8%). `ka`: single-dose peak 6.5–6.7 h
(Kuhnz), 6.5 h (Price), 6.8–8.4 h (Timmer), 8 h (Doll 2022, Stadberg),
5.8 h at steady state (Zumenon); terminal half-life 13.5–15 h (Kuhnz),
10–16 h (Zumenon), 14 h (Lopreeza label); day-1 to steady-state
accumulation 1.53× (Aedo 1990, Table II), 1.42–1.63× (Stadberg after
baseline correction). The model at 0.045/h gives 6.05 h, 15.4 h and
1.59–1.62×; 0.06/h gave 5.5 h, 11.6 h and 1.37×. Kuhnz 1993 and the
1999 chapter both report linear absorption to 4 mg and incomplete
absorption at 8 mg.

**Sublingual (`standardSublingualTheta 0.08`, `kAbsSublingual 1.8`,
`kElimSublingual 1.2`, `sublingualRecyclingShare 0.004`).** θ from each
study's net peak, solved through the model's own mucosal limb at the
cohort's weight: Fiet 1982, 0.5 mg, 75 pg/mL net at 30 min [55 kg]:
0.037; Casper & Yen 1981, 2 mg, 382 net at 30 min (follicular) and 633
net at 45 min (hypogonadal) [60 kg assumed]: 0.057 and 0.091; Doll 2022,
1 mg, 120 net at 1 h [BMI 33, ~95 kg assumed]: 0.060; Price 1997, 1 mg,
441 net at 1 h [~80 kg]: 0.18; Burnier 1981, 0.5 mg, 750 net at 1 h:
0.48. The three studies with a sample at or before 30 min or with
LC-MS/MS give the 0.058 geometric mean; from the steady-state estrone
ratios (Cirrincione 2021: E1/E2 4.3 sublingual against 9.28 oral and 0.4
parenteral in the same population) θ solves to 0.101. Exposure: with
oral F 0.030 the measured sublingual-to-oral AUC ratios of 1.8× (Doll,
0–8 h, total) to 2.6× (Price, 0–24 h) need `θ × 0.342` between 0.027 and
0.051; 0.08 gives 0.027 and a 24 h ratio of 2.5×. Decline: 2 h / 1 h of
0.36 (Doll), 0.40 (Price), 0.52 (Burnier) against the model's 0.51; the
IV phases after a bolus are 0.3 h and 0.9 h (Kuhnz 1993), i.e. 2.3/h and
0.77/h, which bracket 1.2/h. Peak time 0.5 h (Casper & Yen, Fiet) to
0.75 h against the model's 0.68 h. Recycling share: swept against
Cirrincione's LC-MS/MS median of 154 pg/mL on 6 mg/day in three doses
(model median 150 at 0%, 167 at 0.4%, 195 at 1%) and Cortez 2024's
trough of 95 pg/mL on 6.2 mg/day (model 78, 89, 106); 0.4% is the
smallest value that keeps the 8–24 h sublingual curve at or above oral.
No primary source for an "11–14 h" sublingual half-life was found; the
chapter Cirrincione cites contains none.

**Injections (`formationFraction 0.078` for EB, EV, EC, EN).** Oriowo
1980 (5 mg of each ester in arachis oil, n = 9–10 per group, RIA after
chromatography, endogenous estradiol suppressed by a combined pill):
Table V's baseline-subtracted mean during the elevated period times
Table IV's duration gives EB 422 pg/mL × 4.5 d, EV 308 × 7.4 d, EC 159
× 11.8 d, i.e. 12,605 / 14,309 / 13,140 pg·h/mL per mg of E2 equivalent,
shares 0.72 / 0.82 / 0.75 at 57.4 L/h, F 0.072 / 0.082 / 0.075. Zhou
1998, Table 5 (5 mg cypionate, n = 9, 57.6 kg; cycle 1 at days 0, 1, 3,
5, 7, 14, 21, 28: 73, 290, 249, 255, 212, 149, 103, 91 pg/mL): F
0.065–0.081 single dose, 0.075–0.088 over three cycles, depending on
whether the day-0 or the day-28 value is subtracted. Rahimy 1999 (5 mg
cypionate, n = 14, 58.4 kg, immunoassay): raw AUC0-28 2,741 pg·d/mL
implies a share above 1 until the 46 pg/mL baseline is removed; then
F 0.047–0.049; peak 247 pg/mL, median tmax 1.0 d, half-life 8.4 d.
Schiavon 1988 (10 mg enanthate, n = 7 + 7, 56.6 and 61.6 kg): peaks 283
pg/mL at 6.3 d and 314 at 4.2 d; the model gives 322 and 295 at 0.078,
367 and 337 at 0.089. Garza-Flores 1994, Figure 2 (idealised
four-ester overlay), digitised: EN 0.071, EC 0.076, EV 0.080. Valerate:
Aedo 1985 (5 mg with norethisterone enanthate, n = 7, 57 kg) peak 428
pg/mL at day 2, which the model matches at 0.062 (419) and overshoots at
0.078 (525); Düsterberg & Nishino 1982, Figure 2 (4 mg, n = 2, no day-2
sample) peaks at 394 pg/mL on day 2.7–3.1 and integrates to 1.3–1.6× the
dose at this clearance, an upper bound; its 5 mg-scaled peak of 492–534
needs 0.090–0.097 at 70 kg. The reference calculators give shares of
0.695–0.926 (Estrannaise) and 0.680–0.936 (TFS); GAHT Plotter's EB
(1.55) and EC (1.06) exceed 1 and are not used. Cypionate depot
(`0.15 / 0.08 / 0.0035`): RMS residual against Zhou's trough-subtracted
curve is 61 pg/mL for the old depot, 44 at k1Fast 0.04, 31 at 0.08.
Enanthate's depot peaks at 5.9 d against Schiavon's 6.3 d. Valerate's
two primaries disagree on its tail (Oriowo: baseline by day 7–8;
Düsterberg & Nishino: 148 pg/mL at day 10), so its depot is unchanged.

**Gel (`gelFMax 0.060`, `gelK1 0.04`).** Implied F at 70 kg, uncorrected
then after a 5 pg/mL baseline: Oestrogel UK SmPC, 2.5 g (1.5 mg) daily,
day-12 average 76.8 pg/mL: 0.071 / 0.066. Maggacis 2025 (81 trans and
gender-diverse adults on EstroGel 0.06%), median 77.6 pg/mL at 1.5
mg/day (endogenous unknown): 0.071. Le Estrogel 0.06% JP insert, 1.8 g
(1.08 mg) for 14 days, Css 60.8 pg/mL: 0.078 / 0.071. EstroGel US
label, 1.25 g (0.75 mg), day-14 average 28.3 pg/mL, stated unadjusted:
0.052 / 0.043. Scott 1991, 1.5 mg, 68.1 pg/mL: 0.063 / 0.058. Järvinen
1997 (1 mg of 0.1% gel daily, n = 16, 62 kg, steady-state AUC0-24
5,830 / 4,930 / 2,750 pmol·h/L on 200 cm² / 400 cm² / both thighs):
0.081 / 0.068 / 0.038, a log-linear area dependence that passes through
0.057 at 750 cm². Järvinen 1999 (n = 15): 1.5 mg of the same gel gave
1.09× (CI 0.86–1.21) the steady-state AUC of an Estraderm 50 µg/day
patch, i.e. 0.036 if the patch delivers its nominal rate. `gelK1`:
steady-state peak 4 ± 1 h and 5 ± 1 h on 200 and 400 cm² (Järvinen
1997), single-dose peak 6 ± 1 h gluteal and 8 ± 1 h abdominal (Järvinen
1999), steady state "after the third daily application" (EstroGel), "72
hours after the first dose" (Le Estrogel), "3–5 days" (Oestrogel); the
model gives 5.0 h, 6.3 h and 94% of steady state after the third dose.

**Estrana (`patchFallbackK1 0.0035`).** Estrana tape package insert
(Hisamitsu, Japan): 0.72 mg worn 48 h holds 52.0–53.8 pg/mL from 8–12 h
until removal and 52.5–60.5 pg/mL at 48 h on repeated use, in
postmenopausal women; the model gives 48–53 pg/mL at 55 kg.

### Sources

Labels and inserts:

- Zumenon 2 mg film-coated tablets SmPC, section 5.2 (Exeltis UK),
  <https://www.medicines.org.uk/emc/product/1174/smpc>.
- Oestrogel 0.06% gel SmPC, section 5.2 (Besins UK),
  <https://www.medicines.org.uk/emc/product/353/smpc>.
- EstroGel 0.06% label, FDA NDA 021166, 2014,
  <https://www.accessdata.fda.gov/drugsatfda_docs/label/2014/021166s010lbl.pdf>.
- Le Estrogel 0.06% (ル・エストロジェル) package insert, Fuji Pharma,
  JAPIC PINS 00053549, <https://pins.japic.or.jp/pdf/newPINS/00053549.pdf>.
- Estrana tape (エストラーナテープ) package insert, Hisamitsu, JAPIC PINS.
- Progynon Depot 10 mg package insert, Fuji Pharma, JAPIC PINS 00054648
  (qualitative: peak 3–5 days, half-life 4–5 days, citing Düsterberg).
- Bijuva and Lopreeza prescribing information (DailyMed), used as
  oral cross-checks; Divigel clinical pharmacology review, FDA NDA
  022038, 2007.

Oral and clearance:

- Kuhnz W, Gansau C, Mahler M. Pharmacokinetics of estradiol, free and
  total estrone, in young women following single intravenous and oral
  administration of 17β-estradiol. Arzneimittelforschung
  1993;43(9):966–973. PMID 8240460.
- Kuhnz W, Blode H, Zimmermann H. Pharmacokinetics of exogenous natural
  and synthetic estrogens and antiestrogens. In: Oettel M, Schillinger
  E, eds. Estrogens and Antiestrogens II. Handbook of Experimental
  Pharmacology 135/II. Springer, 1999: ch. 35, 261–322.
- Düsterberg B, Schmidt-Gollwitzer M, Hümpel M. Pharmacokinetics and
  biotransformation of estradiol valerate in ovariectomized women. Horm
  Res 1985;21(3):145–154. doi:10.1159/000180039.
- Düsterberg B, Nishino Y. Pharmacokinetic and pharmacological features
  of oestradiol valerate. Maturitas 1982;4(4):315–324.
  doi:10.1016/0378-5122(82)90064-0.
- Timmer CJ, Geurts TBP. Bioequivalence assessment of three different
  estradiol formulations in postmenopausal women in an open, randomized,
  single-dose, 3-way cross-over study. Eur J Drug Metab Pharmacokinet
  1999;24(1):47–53. PMID 10412891.
- Aedo AR, Landgren BM, Diczfalusy E. Pharmacokinetics and
  biotransformation of orally administered oestrone sulphate and
  oestradiol valerate in postmenopausal women. Maturitas
  1990;12(4):333–343.
- Stadberg E, Westlund P, Landgren BM, et al. Bioavailability of
  norethisterone acetate alone and in combination with estradiol
  administered in single or multiple oral doses to postmenopausal women.
  Maturitas 1999;33(1):59–69.
- Cattani VB, Jalil EM, Eksterman L, et al. Estradiol and spironolactone
  plasma pharmacokinetics among Brazilian transgender women using HIV
  pre-exposure prophylaxis. Clin Pharmacokinet 2023;62(7):1031–1041.
  doi:10.1007/s40262-023-01248-0.
- Vervalcke J, et al. (ENIGI). Endocrine Abstracts 106:016,
  <https://www.endocrine-abstracts.org/ea/0106/ea0106016>.
- Leinung MC, Feustel PJ, Joseph J. Hormonal treatment of transgender
  women with oral estradiol. Transgend Health 2018;3(1):74–81.
- Nolan BJ, et al. Relationships between body mass index with oral
  estradiol dose and serum estradiol concentration in transgender adults
  undergoing feminising hormone therapy. Ther Adv Endocrinol Metab
  2020;11:2042018820924543.
- Wiegratz I, Fink T, Rohr UD, Lang E, Leukel P, Kuhl H. Cross-over
  comparison of the pharmacokinetics of estradiol under hormone
  replacement therapy with estradiol valerate or micronized estradiol.
  Zentralbl Gynakol 2001;123(9):505–512.
- Kuhl H. Pharmacology of estrogens and progestogens: influence of
  different routes of administration. Climacteric 2005;8 Suppl 1:3–63.
  doi:10.1080/13697130500148875.
- Kuhl H. Pharmacokinetics of oestrogens and progestogens. Maturitas
  1990;12(3):171–197.

Sublingual:

- Doll E, Gunsolus I, Thorgerson A, Tangpricha V, Lamberton N,
  Sarvaideo JL. Pharmacokinetics of sublingual versus oral estradiol in
  transgender women. Endocr Pract 2022;28(3):237–242.
  doi:10.1016/j.eprac.2021.11.081.
- Price TM, Blauer KL, Hansen M, Stanczyk F, Lobo R, Bates GW.
  Single-dose pharmacokinetics of sublingual versus oral administration
  of micronized 17β-estradiol. Obstet Gynecol 1997;89(3):340–345.
  doi:10.1016/S0029-7844(96)00513-3.
- Burnier AM, Martin PL, Yen SSC, Brooks P. Sublingual absorption of
  micronized 17β-estradiol. Am J Obstet Gynecol 1981;140(2):146–150.
  doi:10.1016/0002-9378(81)90101-0.
- Casper RF, Yen SSC. Rapid absorption of micronized estradiol-17β
  following sublingual administration. Obstet Gynecol 1981;57(1):62–64.
  PMID 7454177.
- Fiet J, Hermano M, Witte J, et al. Post-menopausal concentrations of
  plasma oestradiol, oestrone, FSH and LH and of total urinary
  oestradiol and oestrone after a single sublingual dose of
  oestradiol-17β. Acta Endocrinol (Copenh) 1982;101(1):93–97.
- Cirrincione LR, Winston McPherson G, Rongitsch J, et al. Sublingual
  estradiol is associated with higher estrone concentrations than
  transdermal or injectable preparations in transgender women and
  gender nonbinary adults. LGBT Health 2021;8(2):125–132.
  doi:10.1089/lgbt.2020.0249.
- Cortez et al. Sublingual versus transdermal estradiol in transgender
  women, a randomised trial. J Endocr Soc 2024.
  doi:10.1210/jendso/bvae108.
- Kariyawasam et al. Estrone and estradiol by route of administration
  in transgender adults. 2025. PMC12180117.

Injections:

- Oriowo MA, Landgren BM, Stenström B, Diczfalusy E. A comparison of
  the pharmacokinetic properties of three estradiol esters.
  Contraception 1980;21(4):415–424. PMID 7389356.
- Aedo AR, Landgren BM, Johannisson E, Diczfalusy E. Pharmacokinetic
  and pharmacodynamic investigations with monthly injectable
  contraceptive preparations. Contraception 1985;31(5):453–469.
- Zhou XF, Shao QX, Han XJ, Weng LJ, Sang GW. Pharmacokinetics of
  medroxyprogesterone acetate after single and multiple injection of
  Cyclofem in Chinese women. Contraception 1998;57(6):405–411.
- Rahimy MH, Ryan KK, Hopkins NK. Lunelle monthly contraceptive
  injection: steady-state pharmacokinetics of MPA and E2 in surgically
  sterile women. Contraception 1999;60(4):209–214.
  doi:10.1016/s0010-7824(99)00086-4.
- Rahimy MH, Cromie MA, Hopkins NK, Tong DM. Lunelle: effects of body
  weight and injection sites on pharmacokinetics. Contraception
  1999;60(4):201–208. doi:10.1016/s0010-7824(99)00085-2 (no estradiol
  data; MPA only).
- Fotherby K, Benagiano G, Toppozada HK, et al. A preliminary
  pharmacological trial of the monthly injectable contraceptive
  Cyclo-Provera. Contraception 1982;25.
- Schiavon R, Benavides S, Oropeza G, et al. Serum estrogens and
  ovulation return in chronic users of a once-a-month injectable
  contraceptive. Contraception 1988;37(6):591–598.
- Garza-Flores J. Pharmacokinetics of once-a-month injectable
  contraceptives. Contraception 1994;49(4):347–359.
  doi:10.1016/0010-7824(94)90032-9.

Gel and patch:

- Järvinen A, Granander M, Nykänen S, et al. Steady-state
  pharmacokinetics of oestradiol gel in post-menopausal women: effects
  of application area and washing. Br J Obstet Gynaecol 1997;104 Suppl
  16:14–18. doi:10.1111/j.1471-0528.1997.tb11562.x.
- Järvinen A, Nykänen S, Paasiniemi L. Absorption and bioavailability
  of oestradiol from a gel, a patch and a tablet. Maturitas
  1999;32(2):103–113. doi:10.1016/S0378-5122(99)00021-3.
- Scott RT Jr, Ross B, Anderson C, Archer DF. Pharmacokinetics of
  percutaneous estradiol: a crossover study using a gel and a
  transdermal system in comparison with oral micronized estradiol.
  Obstet Gynecol 1991;77(5):758–764. PMID 2014090.
- Rohr UD, Nauert C, Stehle B. 17β-estradiol delivered by three
  different matrix patches 50 µg/day: a three way cross-over study in 21
  postmenopausal women. Maturitas 1999;33(1):45–58.
- Maggacis, Cheung AS, Nolan BJ. Estradiol concentrations with
  transdermal estradiol gel in trans and gender-diverse adults. Clin
  Endocrinol (Oxf) 2025. doi:10.1111/cen.15217.

Reference calculators (parameters read from their shipped source):

- Estrannaise, <https://estrannai.se>, source
  <https://github.com/WHSAH/estrannaise.js>.
- Transfeminine Science injectable estradiol simulator,
  <https://transfemscience.org/misc/injectable-e2-simulator/>, and its
  meta-analysis, <https://transfemscience.org/articles/injectable-e2-meta-analysis/>.
- GAHT Plotter, <https://gahtplotter.com> (no public source; model read
  from the shipped bundle).

## Route-scale estimate calibration

Featherline 1.4.0 adds a per-user estimate calibration of its own, not taken
from upstream, on top of the unchanged population engine, in
[`model/pk`](https://github.com/mkx173/Featherline/tree/main/app/src/main/java/com/mkx/hrttracker/model/pk)
(`PkCalibration*`, `PkE2ForwardModel`). It does not touch the
compartment parameters: it fits one log-scale `beta_r` per route and
multiplies that route's population contribution by `e^{beta_r}`.

- **Forward model.** For each E2 result `i`, the population engine
  splits the modeled drug-attributable E2 at the collection time into
  per-route parts `d_ir`. The adjusted prediction is
  `m_i(beta) = sum_r e^{beta_r} d_ir`, so a result informs every route
  in proportion to its modeled share.
- **Evidence filter.** A result is set aside when the modeled total is
  below 5 pg/mL (`drugMinInformativePgml`), when its value is zero or
  below, or when the forward model cannot be evaluated. Results the user
  excluded are skipped. There is no endogenous-E2 term. The fit reads
  estradiol doses from 180 days before the earliest result; if every dose
  is older than that, the latest one is kept, so those results read as
  below the modeled signal rather than as having no dose logged.
- **Objective.** A joint MAP over the supported routes (some result
  draws at least 20% of its modeled signal from the route) with a
  Student-t (`nu = 4`) likelihood on the log residual
  `log y_i - log m_i`, scaled by `R_LOG = 0.0225` (about 15% log-SD),
  plus a Gaussian prior `beta_r ~ N(0, 0.30^2)`. Every other route keeps
  `beta_r = 0` and shows as population.
- **Solver.** Deterministic multi-start damped Newton. Starts are
  `beta = 0`, each route's 1-D conditional minimum (grid plus
  bisection), and every pairwise combination of those. If two distinct
  minima survive, the best one is used and every route is flagged
  ambiguous.
- **Uncertainty.** A Laplace approximation at the MAP gives each route's
  posterior SD. The Home band is the 68% and 95% prediction interval
  for a new result (Laplace covariance plus Student-t
  observation noise, integrated with Gauss-Hermite quadrature). It is
  not a therapeutic range.
- **Warn, don't gate.** Every supported route is applied. Warnings are
  attached when the scale is outside the route's usual
  range (0.5 to 2 for injection, patch, and oral; 0.25 to 3 for gel and
  sublingual) or is a large shift backed by fewer than three results,
  when the posterior SD is above 0.20 or the supporting results span
  less than a 2x signal range, when the weighted log RMSE exceeds
  `2 * sqrt(R_LOG)`, when a supporting result has a Student-t weight
  below 0.25 and hasn't been reviewed, or when the fit is ambiguous.
- **What "calibrated" means on screen.** The Home hero and the calibration status
  row call the estimate calibrated when a fitted route is at least 20%
  (`PROMOTION_SUPPORT_SHARE_MIN`) of the drawn signal at some knot of
  Home's visible chart window; the calibration page judges over the
  same window, so the two agree. The band is drawn only over the times
  where that holds, so "calibrated" and a visible band go together, and
  each band knot carries its contiguous-run tag so the chart breaks the
  band at gaps instead of bridging them. The routes sheet marks a fitted
  route that never reaches that share with a "Small share" pill. An old route's exponential tail stays
  positive for months, so "any contribution" would label a curve from a
  new, uncalibrated route as calibrated.
- **Review.** Per-result choices (`REVIEWED`, `EXCLUDED`) live in
  `e2_calibration_metadata`, `AUTO` as no row; see [data-model.md](data-model.md).
  `REVIEWED` only hides the review tip and never changes the fit.

The fit runs entirely on device, inside the Home snapshot build, so the
Home chart, the band, and the widgets all draw from the same adjusted
parameters. Hipparchus supplies the bisection and Brent root solvers,
Cholesky decomposition, Gauss-Hermite integration, and the Student-t
distribution (the band uses the closed-form t4 CDF, falling back to the
library for any other degrees of freedom); see
[third-party-notices.md](third-party-notices.md). The band solves its
quantile law once per distinct variance, with variance + R_LOG rounded to a
0.5% geometric grid (under 0.25% in SD, below a pixel at chart scale), so a
dense multi-route chart costs a few hundred solves at most.

## What Featherline plans to change

The PK engine swap gates three deferred refactors, all enumerated in
[`architecture.md#known-limitations-and-planned-refactors`](architecture.md#known-limitations-and-planned-refactors):

- Consolidate the fragmented PK math (constants, three-compartment
  simulation, dose-equivalence) so `PkCatalog` and
  [`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/DoseInstructionCalculator.kt)'s
  overlapping molecular-weight constants collapse into one source of
  truth.
- Untangle the projection cache from `HomeSnapshotRepository` so PK
  re-simulation isn't triggered by unrelated home mutations.

## Out of scope

- **The PK math itself.** Compartment equations live in the upstream
  README and its `pk_research/` workspace; the estradiol constants'
  derivations and sources are in the section above.
- **Testosterone as a user-facing simulation.** `PkHormone.TESTOSTERONE`
  exists in the catalog with full parameters, but no user-logged
  category currently produces testosterone PK events, so testosterone
  trends are not rendered.
