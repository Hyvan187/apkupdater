# Validation status

This file is updated after the final local validation run. Do not interpret the presence of tests or a CI workflow as proof that they have passed.

The suite includes synthetic IN-versus-newer-CN, signer mismatch, unknown region, missing/unknown splits, numeric version codes, unsupported ABI/minSdk, package mismatch, unresolved rotation, missing metadata, malformed manifest and hash-based merge cases. These are logic tests, not tests of a real regional OEM APK catalog.

Physical OnePlus 15 / CPH2745 / IN: not tested. No physical phone is attached to this environment. CPU/GPU/FPS/energy benchmarks and the full display/font/accessibility matrix: not measured.

## Confirmed local results

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: **BUILD SUCCESSFUL**, 2026-11-06.
- 20 JVM tests: 19 compatibility/merge regression tests plus one inherited smoke arithmetic test; 0 failures, 0 errors.
- Lint: no errors; existing and development warnings remain in the bundled report. No lint baseline was added to hide errors.
- APK signature: apksigner verification succeeded, v1 and v2 schemes, one locally generated Android Debug signer. v3/v4 and source stamps are not claimed.
- aapt: package `com.hyvan187.apkupdater`, version 0.1.0-glass-dev / 1001, compileSdk 37, targetSdk 37, minSdk 23.
- Host-side binary-manifest probe parsed the actual generated APK (49 XML tags) and correctly recovered its package and min/target SDK.
- Runtime emulator attempt and GitHub Actions execution status are recorded in the delivery report; no real device result is implied.

Build tooling obstacles resolved: full JDK added, actual Android SDK package name selected, environment-specific Java proxy/trust configuration applied locally, API-26 Duration overload removed for API-23 support, and invalid package flags corrected. Proxy settings and local trust configuration are excluded from the published source.
