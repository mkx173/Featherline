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

Known limitations of the v1 model:

- **Single-analyte.** It projects estradiol only. It does not track testosterone, progesterone, SHBG, prolactin, or any other hormone, even when those are part of your regimen and relevant to interpretation.
- **Three-compartment approximation.** Real human pharmacokinetics involves many more compartments and pathways. The three-compartment model is a tractable simplification, not a faithful biological simulation.
- **No personal calibration.** The model is not tuned to your own lab results. There is no Bayesian update from your blood tests back into the model parameters in v1.

Things you should **not** use the projection for:

- Deciding a dose
- Changing a regimen
- Interpreting a symptom
- Timing labs around an expected peak — the model's peak timing may not match yours

Blood tests, drawn at the appropriate time relative to your dosing and interpreted by a clinician, remain the source of truth for what your levels actually are.

## What Featherline does NOT do

- It does not recommend doses.
- It does not interpret lab results.
- It does not alert you to concerning trends, out-of-range values, or anomalies.
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
