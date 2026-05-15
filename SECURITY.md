# Security Policy

HRTTracker is a personal-health tracker. Bugs that could expose locally stored
medication, blood-test, or calibration data — or that could weaken the app-lock
boundary — are taken seriously.

## Reporting a vulnerability

Please **do not** open a public issue for security reports. Use GitHub's private
vulnerability reporting:

1. Go to <https://github.com/mkx173/HRTTracker/security/advisories/new>.
2. Describe the issue, the affected version, and a reproduction if you have one.
3. The maintainer will acknowledge receipt within 7 days and aim to triage
   within 14 days.

If GitHub's flow is unavailable to you, contact
[@mkx173](https://github.com/mkx173) on GitHub directly.

## Supported versions

Only the latest released version on Google Play is supported with security
fixes. Earlier versions will not receive patches.

## Scope

In scope:

- Vulnerabilities that affect locally stored user data (Room database, backup
  files, preferences).
- Vulnerabilities in the app-lock / biometric prompt boundary.
- Vulnerabilities in the backup-restore pipeline (file parsing, decompression,
  validation).

Out of scope:

- Findings that require physical access combined with an unlocked device.
- Social-engineering scenarios against the maintainer.
- Vulnerabilities in third-party libraries that have no exploitable surface in
  this app.
