# Third-party notices

Featherline is released under [GPL-3.0](../LICENSE). This page lists third-party
runtime dependencies, assets, and adapted source material used by the app.

## Generated dependency inventory

The runtime dependency inventory is generated from Gradle metadata:

- [HTML dependency report](generated/dependency-license-report.html)
- [CSV dependency report](generated/dependency-license-report.csv)

Regenerate these files before a release:

```bash
./gradlew generateLicenseReport --no-parallel
```

## Manual notices

The generated report inventories Gradle dependencies. These entries add explicit
implementation notes for critical dependencies and cover copied, adapted, or
design-derived material that Gradle cannot detect.

### Hipparchus Core

The calibration solver uses the bisection root solver and Cholesky
decomposition, and the predictive-band renderer uses the Brent root solver,
Gauss-Hermite integration and Student-t distribution APIs, from
`org.hipparchus:hipparchus-core:4.0.3`.

- License: Apache License, Version 2.0
- Source: [Hipparchus](https://www.hipparchus.org/)
- Local license text: [APACHE-2.0.txt](licenses/APACHE-2.0.txt)

### Radix Colors

Featherline's medication group color palettes adapt selected scale values from
[Radix Colors](https://github.com/radix-ui/colors).

- License: MIT License
- Copyright: Copyright (c) 2021-2022 Modulz; Copyright (c) 2022-Present WorkOS
- Local license text: [MIT.txt](licenses/MIT.txt)

### Material Symbols

Featherline includes Material Symbols vector icons from Google.

- License: Apache License, Version 2.0
- Source: [Material Symbols guide](https://developers.google.cn/fonts/docs/material_symbols?hl=en)
- Local license text: [APACHE-2.0.txt](licenses/APACHE-2.0.txt)

### Health Connect logo

Featherline includes the monochrome Health Connect logo vector
(`ic_health_connect.xml`) from the Android Open Source Project's Health
Connect app, unchanged, to label Health Connect sync.

- License: Apache License, Version 2.0
- Copyright: Copyright (C) 2022 The Android Open Source Project
- Source: [platform/packages/modules/HealthFitness](https://android.googlesource.com/platform/packages/modules/HealthFitness/+/refs/heads/main/apk/res/drawable/ic_health_connect.xml)
- Local license text: [APACHE-2.0.txt](licenses/APACHE-2.0.txt)

### miuix

The top-level pager's swipe handling (`ui/components/PagerSwipe.kt`), which
takes sideways swipes from page content even while a list is still scrolling,
and its settle spring are adapted from `horizontalPagerSwipeOverride` and
`PagerNavigationSpringSpec` in miuix's `PagerGestureUtils.kt` (v0.9.4).

- License: Apache License, Version 2.0
- Copyright: Copyright 2026 compose-miuix-ui contributors
- Source: [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/PagerGestureUtils.kt)
- Local license text: [APACHE-2.0.txt](licenses/APACHE-2.0.txt)

### Oyama's HRT Tracker

Featherline adapts plot display logic from
[Oyama's HRT Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker).

- License: MIT License
- Copyright: Copyright (c) 2025 Joseph Smirnova Oyama
- Local license text: [MIT.txt](licenses/MIT.txt)

### HRT-Recorder-PKcomponent-Test

Featherline's pharmacokinetic projection draws on the model and math reference
from [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test).
This is listed here as a source attribution for the PK reference material.

### Roboto (modified metrics)

The app bundles Roboto Regular, Medium, SemiBold and Bold
(`app/src/main/res/font/roboto_featherline_*.ttf`), instanced from the official
Roboto variable font (version 3.015) with the `hhea`/`OS/2` vertical metrics
raised to 1.50/0.76 em and the family renamed "Roboto Featherline". The taller
line box covers every CJK fallback font, so Chinese text no longer moves the
baseline. The family is used only on devices whose system CJK fallback font is
taller than their Latin font (see `ui/theme/CjkFallbackMetrics.kt`).

- License: SIL Open Font License, Version 1.1 (no Reserved Font Name)
- Source: [google/fonts, ofl/roboto](https://github.com/google/fonts/tree/main/ofl/roboto)
- Local license text: [OFL-1.1.txt](licenses/OFL-1.1.txt)
