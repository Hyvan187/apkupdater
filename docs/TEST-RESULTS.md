# Validation — Liquid-APK-Updater 0.4.0

Local Gradle build/test/lint validation on 6 November 2026. JDK 21, SDK 37, target 37, min 23; application bytecode Java 17.

Observed suite: 78 tests, 0 failures, 0 errors. Lint: 0 errors, 248 warnings. Final build log and reports accompany the distribution.

| Suite | Tests |
|---|---:|
| ExampleUnitTest | 1 |
| ArtifactMergeTest | 4 |
| CompatibilityEngineTest | 15 |
| DownloadArchiveTest | 6 |
| GlassUiTest | 9 |
| LiquidRegressionTest | 16 |
| RegressionTest | 16 |
| ReleasePolicyTest | 9 |
| SourceHealthTest | 2 |

The nine native API-37 UI tests use Robolectric and actual Compose rendering. They cover navigation, compact layout, light theme, DocumentsUI downgrade rejection, source confirmation, persisted appearance, a single appearance entry, recommended and full resets, Google-system scope, and immediate hiding of a cached beta candidate. These are host tests, not physical-phone tests.

Six archive tests use a controlled ContentProvider and real file streams to verify relative Download/Liquid-APK-Updater placement, byte preservation, reuse, keep/delete behavior and safe names. Failed-install deletion is checked as a decision rule. Actual PackageInstaller confirmation, MediaStore behavior and file-manager behavior on OnePlus are not tested here.

Release-policy tests cover Alpha/Beta/RC/Preview tokens, independent controls, provider prerelease flags and Google-system classification. Unlabelled prereleases cannot be reliably inferred. Google package namespaces plus system flags are organizational classification, not signature-based Google authentication.

The APK must verify with certificate SHA-256 6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6, matching the retained 0.2/0.3 development signer. Version 1004 / 0.4.0-liquid, package com.hyvan187.apkupdater.

## Limits

- Physical OnePlus 15/IN, OEM installation/ownership behavior, notifications/background timing and real download-folder permissions are untested.
- SAF folder selection on API 23–28 is implemented but not device-tested.
- CPU/GPU/frame rate, power consumption, tablets/landscape and the complete accessibility matrix were not measured.
- Live availability of every source and every remote APK is not proven by unit tests. CAPTCHA/access controls are not bypassed.
- Unknown ROM provenance remains unknown; matching signatures do not prove regional functionality. Unsupported split/APKM/XAPK sets remain blocked.
- Temporary automatic checks stay private; manually downloaded/installed APKs get a public copy. Copies are deleted only after confirmed success with the keep setting off. External browser downloads are not managed.
- GitHub Actions is configured. No remote CI execution result is implied by this local build report.
