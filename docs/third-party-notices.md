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

The generated report only sees Gradle dependencies. These entries cover copied,
adapted, or design-derived material that Gradle cannot detect.

### Wear OS and Google Play Services

The optional Wear OS companion uses AndroidX Wear Tiles/ProtoLayout and the
Google Play Services Wearable Data Layer. The phone dependency is scoped to the
`play` flavor and is not present in F-Droid or GitHub sideload phone builds.
AndroidX components are licensed under Apache-2.0; Google Play Services is
distributed under the Android/Google APIs terms supplied with that dependency.

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
