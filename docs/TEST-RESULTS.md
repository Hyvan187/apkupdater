# Validation — Liquid-APK-Store 0.5.0

Confirmed local Gradle result on 7 November 2026: BUILD SUCCESSFUL. JDK 21, Android SDK 37, Gradle 9.4, AGP 9.1.1; production bytecode Java 17.

113 tests; 0 failures, 0 errors, 0 skipped. Android-Lint: 0 errors, 280 warnings. Warnings remain in the distributed full report; no lint baseline was added to conceal errors.

| Suite | Tests |
|---|---:|
| ExampleUnitTest | 1 |
| ApkLimitsTest | 3 |
| AppLanguagesTest | 3 |
| ArtifactMergeTest | 4 |
| CompatibilityEngineTest | 15 |
| DownloadArchiveTest | 6 |
| GlassUiTest | 14 |
| LiquidRegressionTest | 16 |
| RegressionTest | 16 |
| ReleasePolicyTest | 9 |
| SourceHealthTest | 2 |
| StoreFeaturesTest | 24 |

## Evidence and scope

- Fourteen native API-37 Robolectric/Compose tests cover launch/navigation, compact layout, light theme, source selection, downgrade blocking, persisted appearance/category selection, recommended/full reset, cached-beta exclusion, help, animation/OLED controls, color picker/opacity, donation/legal separation and English core controls.
- Source/policy tests cover count/file/aggregate budgets, all-candidate manual selection, retry policy, favorite persistence, confirmed and unmatched external history, risk from system/prerelease/update ownership, real-device profile invariants, reduced-motion/lifecycle gates, manifest bounds and declared connectivity permission.
- Six archive tests use a controlled ContentProvider and real file streams. These test the app implementation, not an OEM file manager.
- LocaleManager tests set English and reset to the system default on the API-37 host runtime. Only German/English are declared; this is not arbitrary-language translation coverage.

## APK identity

Package com.hyvan187.apkupdater; version 1005 / 0.5.0-store; min SDK 23, compile/target SDK 37. The distributed APK must verify with retained development certificate SHA-256 6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6, matching 0.2–0.4. Actual APK hash and final commit accompany the ZIP.

## Explicitly unverified or constrained

- No physical OnePlus 15/IN installation, live OEM PackageInstaller/ownership flow, external installer app, SAF provider, folder permissions or notification delivery was executed here.
- No CPU/GPU/FPS/energy measurements, full tablet/landscape or accessibility matrix. OLED and animation settings are implemented, not benchmark guarantees.
- The supplied APKMirror DNS problem cannot be declared repaired on the user device. In this restricted host, direct DNS lookup was unavailable and a proxied API GET returned HTTP 403. This does not establish the phone’s network fault or actual authenticated API status. Current-network DNS, bounded retry and diagnostics are implemented; access restrictions are not bypassed.
- App language is German or English or follows the system; unsupported system languages fall back to English. Some help/diagnostic/legal descriptions remain German. Android does not automatically translate unsupported languages.
- Simulated model affects only explicit Play authentication metadata, not other apps or true installation facts. Manual region is a comparison hint, not verified APK provenance.
- External handoff history observes a matching installed version. It cannot prove which external actor executed installation. Public-copy deletion waits for confirmed state and the keep preference.
- Signature checks cannot prove runtime behavior or absence of malicious behavior. Unknown ROM/dependency information stays unknown. Unsupported split/APKM/XAPK installations remain blocked.
- Remote GitHub Actions/emulator completion is not claimed by this local build report.
