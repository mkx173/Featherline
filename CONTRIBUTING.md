# Contributing to Featherline

Thanks for considering a contribution. Here's how to get involved.

## Before you start

- Read the [Code of Conduct](CODE_OF_CONDUCT.md).
- For typos, doc fixes, and small bugfixes: skip the issue, open a PR.
- For features, refactors, or anything touching reminders / PK / backup: open an issue first and **wait for maintainer approval before writing any code**. The issue is where we pick the right approach; the PR is where we land code we already agree on.
- **Large feature PRs that were not agreed on in an issue will be closed without review.** This is not a judgment of the code — it protects your time as much as review time.

The maintainer is one person; turnaround can take days. A stale PR isn't a rejection — ping the issue if you'd like a nudge.

## Reporting bugs

Open a GitHub issue with:

- Device (manufacturer + model) and Android version.
- App version. The `versionCode` is visible on the About screen and in the APK filename if you sideloaded.
- Steps to reproduce, ideally short and exact.
- What you expected to happen vs. what actually happened.
- A screenshot or recording if the bug is visual.

**Featherline is not a medical advice tool.** If you have a concern about your own health, that is not a bug report — see [`docs/safety.md`](docs/safety.md) for the framing and consult a clinician.

## Proposing changes

For non-trivial changes (new features, refactors, anything touching the reminder pipeline, PK simulation, or backup format), open an issue first. The issue should say:

- What you want to change and why.
- A rough sketch of the approach.
- Whether you're asking for input or signaling you'd like to implement it yourself.

Then wait for the maintainer to agree on scope and approach before implementing. This avoids the situation where someone spends a weekend on a PR that conflicts with planned direction. The architectural seams worth knowing about are documented in [`docs/architecture.md`](docs/architecture.md).

Additional expectations for feature work:

- **Test on real hardware.** Features that depend on a physical device or companion service (Wear OS, Health Connect providers, widgets across launchers, cloud accounts) must be verified by you on real hardware before the PR. "Compiles and unit tests pass" is not sufficient, and the maintainer cannot absorb the device-testing burden for contributed features.
- **One concern per PR, and keep it reviewable.** A PR the maintainer cannot review in one sitting is likely to stall or be closed; split large work into agreed-upon stages in the issue first.

## Development setup

Prerequisites and Gradle commands are in [`docs/building.md`](docs/building.md). Contributors don't need release signing keys — debug builds work without `keystore.properties`.

## Tests

Run tests locally before opening a PR:

```bash
./gradlew testPlayDebugUnitTest
```

There is no CI test job today, so your local run is the gating signal. The test-suite layout and where to put new tests live in [`docs/testing.md`](docs/testing.md).

## Commit messages

This repo follows conventional-commits. The prefixes that have appeared so far:

- `feat:` — new user-facing capability
- `fix:` — bug fix
- `refactor:` — internal change with no behavior change
- `docs:` — documentation
- `chore:` — tooling, build config, or housekeeping
- `test:` — adding or changing tests

Keep the subject line under ~72 characters. Use the body to explain *why* the change is needed; the *what* is usually obvious from the diff. Named-thing density (concrete class/function names rather than vague pronouns) is appreciated — the same convention the `docs/` pages follow.

## Translations

New language translations are welcome. The full workflow — picking the resource qualifier, copying `strings.xml`, registering the locale in `AppLanguageOption`, reviewing date/time and widget formatters, and the post-translation validation checklist — is in [`docs/localization.md`](docs/localization.md).

## License

Featherline is released under [GPL-3.0](LICENSE). By opening a pull request, you confirm that you have the right to license your changes under GPL-3.0 and that you intend to do so. No CLA, no copyright assignment.
