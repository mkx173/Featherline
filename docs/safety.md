# Safety and Disclaimer

This document is the canonical statement of what Featherline is, what it is not, and how to read its pharmacokinetic projection. The short banner in the README links here. If anything in the README and this document conflict, this document is the source of truth.

## Not medical advice

Featherline is a tracking tool. It is not a medical device, and installing or using it does not create a clinician–patient relationship between you and the app's developers, contributors, or distributors.

The app does not diagnose any condition. It does not prescribe, recommend, or contraindicate any medication or dose. It does not treat any condition. Decisions about your hormone therapy — whether to start, change, pause, or stop a regimen — are between you and a clinician you trust.

## Not a medical device

Featherline is not approved, registered, cleared, or certified as a medical device under any regulatory regime — including the U.S. FDA, the UK MHRA, the EU MDR (CE marking), Japan's PMDA, China's NMPA, or any equivalent authority elsewhere.

It is not subject to medical-device quality controls, clinical validation, or post-market surveillance. If your jurisdiction regulates health-related apps, Featherline should be treated as a personal log — equivalent in regulatory status to a paper notebook with arithmetic in it — and nothing more.

## How to read the pharmacokinetic projection

The estradiol curve Featherline shows is a **model estimate**, not a measurement.

The model uses population-average parameters: absorption rates, distribution volumes, elimination half-lives, and metabolic constants drawn from published studies. Your body's actual absorption, distribution, metabolism, and clearance may differ substantially from those averages — sometimes by a factor of two or more in either direction.

Known limitations of the model:

- **Estradiol-only projection.** The simulated curve covers estradiol only. The app *records* blood tests for testosterone, progesterone, prolactin, FSH, LH, and user-defined custom analytes — and tracks the medications that affect them — but it does not simulate or project levels for anything other than estradiol. Use the projection as an E2 reference; read every other analyte from your actual lab results.
- **Three-compartment approximation.** Real human pharmacokinetics involves many more compartments and pathways. The three-compartment model is a tractable simplification, not a faithful biological simulation.
- **Calibration is a scale, not a personal model.** Since 1.4.0 Featherline can scale the estradiol curve to your own E2 results (see [Estimate calibration](#estimate-calibration) below). It only changes how high each route's contribution sits. Absorption timing, peak time, and curve shape stay at the population values.

Things you should **not** use the projection for:

- Deciding a dose
- Changing a regimen
- Interpreting a symptom
- Timing labs around an expected peak — the model's peak timing may not match yours

Blood tests, drawn at the appropriate time relative to your dosing and interpreted by a clinician, remain the source of truth for what your levels actually are.

## Estimate calibration

When you log estradiol (E2) blood test results, Featherline fits one multiplier per route (injection, patch, gel, oral, sublingual) so the curve sits closer to your measured values. Read the calibrated curve with these limits in mind:

- **E2 only.** Only estradiol results are used. Other analytes are never fed into the model.
- **Level, not shape.** Each route's contribution is scaled up or down. Timing and shape are not personalized, so a curve that matches your trough can still be wrong about your peak.
- **No endogenous estradiol term.** The model assumes every picogram it predicts comes from your logged doses. If your body still makes a meaningful amount of estradiol, or a result was entered in the wrong unit, the fitted scale absorbs that error.
- **Warnings are not blocks.** A route with a warning (a large correction, a weak signal, a poor fit, too few results, or results that point two ways) is still applied to the curve and the widget. Read the warnings on the Calibration screen before trusting a calibrated route.
- **The shaded band is not a target.** On Home, the shaded 68% / 95% band shows where a new result would likely fall if the model and your dosing hold. It says nothing about where your levels should be.
- **Past dates can change.** Every new result refits every route it touches, so the calibrated curve for earlier days can move.
- **Results it sets aside.** Results whose modeled drug signal at collection time is below 5 pg/mL, or whose value is zero or below, are not used. Results that disagree strongly with the rest go into a review list, where you can keep them at a lower weight, exclude them, or undo that choice.

Do not change a dose, a regimen, or the timing of a blood draw because of the calibrated curve. Discuss your actual results with a clinician.

## What Featherline does NOT do

- It does not recommend doses.
- It does not interpret lab results.
- It does not alert you to concerning trends or anomalies. (The reference-range view does flag entered lab values as below / in / above range, but it does not diagnose anything or recommend actions from those classifications.)
- It does not detect side effects.
- It does not check for interactions with other medications.
- It does not replace any clinical visit, lab draw, or in-person care.

If a feature looks like it might do one of these things, it doesn't — read the feature's documentation for what it actually does.

## Self-care and clinical care

Decisions about your therapy are yours.

Featherline is useful as a log and a projector even when you do not have regular clinician or lab access. That is a real situation many people on HRT live with — through gatekeeping, cost, geography, or systemic discrimination — and this app does not pretend otherwise. Tracking what you take and modeling what you'd expect is valuable on its own.

That said, a clinician's interpretation remains the only reliable way to:

- Confirm a model projection against reality (via a blood draw)
- Diagnose a side effect or a concurrent condition
- Adjust a regimen safely when something isn't working
- Manage interactions between hormone therapy and other medications

In an emergency, contact your local emergency services. The app, its maintainers, and the people who built it are not on-call resources and cannot help in real-time.

## No warranty

Featherline is released under the GNU General Public License, version 3.0. Sections 15 and 16 of GPL-3.0 disclaim all warranty and limit liability, and those disclaimers apply in full.

The maintainer is not liable for any decision made with the app's help, for any divergence between the model and reality, or for any harm — direct or indirect — arising from use or inability to use the app.
