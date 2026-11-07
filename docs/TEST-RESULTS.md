# Validation — Liquid-APK-Store 0.6.0-rc1

7 November 2026: local Gradle release build completed successfully with JDK 21, Gradle 9.4.0, AGP 9.1.1 and Android SDK 37. The production bytecode targets Java 17.

124 automated host tests passed: 0 failures, 0 errors, 0 skipped. Release lint: 0 errors/fatal findings and 283 warnings. No baseline conceals these warnings. The complete lint report accompanies the release archive.

| Suite | Tests |
|---|---:|
| ExampleUnitTest | 1 |
| ApkLimitsTest | 3 |
| ApkSetInspectorTest | 7 |
| AppLanguagesTest | 3 |
| ArtifactMergeTest | 4 |
| CompatibilityEngineTest | 15 |
| DownloadArchiveTest | 6 |
| GlassUiTest | 16 |
| LiquidRegressionTest | 16 |
| RegressionTest | 16 |
| ReleasePolicyTest | 9 |
| SecureMigrationTest | 2 |
| SourceHealthTest | 2 |
| StoreFeaturesTest | 24 |

## Verification scope

- API-37 Robolectric/Compose navigation, appearance, persistence, source picking, selective technical info icons and landscape layout. Screenshots were visually inspected. These are host tests, not a physical OnePlus test.
- Seven APK-set tests use actual signed, code-free APK bytes, binary manifests and the actual APK signature verifier. Only PackageManager archive metadata is injected to avoid Android-specific mmap/JDK internals on the host. They cover valid sets, explicit required features, missing features, mixed signers, version/package mismatches and multiple bases.
- Two preference-migration tests cover durable value transfer, plaintext-file removal and idempotence using an injected storage target. Actual Android Keystore execution is assessed separately.
- The release APK verifies with v1/v2/v3 signatures. Package com.hyvan187.apkupdater, version 1006 / 0.6.0-rc1, min/target SDK 23/37. No application-debuggable flag.
- The APK contains ARMv7, ARM64, x86 and x86_64 native libraries in one universal package. ZIP alignment passes the 16 KiB check. All native LOAD segments have 0x4000 alignment.
- The retained certificate SHA-256 is 6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6, matching prior 0.2–0.5 APKs. Certificate naming is retained for update continuity; the delivered application is the non-debuggable release variant.

## Acceptance gaps

- Direct unauthenticated APKMirror API access returned HTTP 401 in a live probe. Old shared credentials were removed. API access is not declared repaired; browser fallback remains available.
- Physical OnePlus 15 / IN-ROM detection, actual OEM update compatibility and ROM provenance are not verified here. Missing provenance continues to block approval.
- Full live-provider coverage, every install/cancel/storage-provider flow, measured glass FPS/energy, broad accessibility and physical Android-version coverage remain unverified.
- German/English/system language selection remains available; full translation of all messages and arbitrary languages is incomplete.
- A software Android-10/API-29 emulator booted, but installation of the prior development APK timed out after 90 seconds. No native release startup or Android Keystore pass is claimed. The additional emulator attempt did not yield a valid application test. This remains an acceptance gap.
- Variant archives, ambiguous or missing split requirements, OBB files and external-installer archive handoffs remain intentionally unsupported.
- This release candidate does not satisfy the complete requested production acceptance checklist.
