# Validation: Liquid-APK-Store 0.6.0-rc2

7 November 2026. Package `com.hyvan187.apkupdater`, version `1007 / 0.6.0-rc2`, min/target/compile API `23 / 37 / 37`.
Build baseline: JDK 21.0.2, Gradle 9.4.0, AGP 9.1.1, Compose compiler 2.3.10; application bytecode targets Java 17.

**167 host tests passed: 0 failures, 0 errors, 0 skipped.** These are newly executed RC2 results, not the historical RC1 count.

| Suite | Tests |
|---|---:|
| ExampleUnitTest | 1 |
| DiagnosticStoreTest | 7 |
| ApkLimitsTest | 3 |
| ApkSetInspectorTest | 7 |
| AppLanguagesTest | 3 |
| ArtifactMergeTest | 4 |
| CompatibilityEngineTest | 15 |
| DownloadArchiveTest | 7 |
| GlassTransparencyUiTest | 3 |
| GlassUiTest | 19 |
| LiquidRegressionTest | 16 |
| PlayDownloadTest | 9 |
| RC2RegressionTest | 20 |
| RegressionTest | 16 |
| ReleasePolicyTest | 9 |
| SecureMigrationTest | 2 |
| SourceHealthTest | 2 |
| StoreFeaturesTest | 24 |

## What the tests establish

- Actual `UpdatesViewModel.refresh` and `UpdatesWorker.doWork` do not resolve Play files or request APK bytes even when the retired automatic-download preference is true. Migration disables that preference persistently. Missing verified cache files never trigger a replacement download.
- A Play selection stays enabled, but opening source choices, risk explanations and reports performs no lazy resolution. Explicit download resolves BASE/SPLIT metadata once, fetches all components, checks length/digest, limits aggregate bytes and cleans partial files. Signed, code-free fixtures exercise real binary XML and actual APK signature checks, including mixed signers, missing features, inconsistent versions/packages and multiple bases.
- Reconciliation removes all already-installed source variants, handles late provider emissions and retains a still-higher candidate. Aborted or failed installations do not create success history. Missing packages during Android replacement are retried. Persisted pending self-updates are confirmed only from actual versions and public-copy cleanup also survives process replacement.
- Individual checks are cancellable, discovery/check progress is serialized, stale/duplicate callbacks are rejected and failed attempts have separate counters. Cache leases protect active work; pending external-installer files and diagnostic records survive cache clearing.
- Diagnostics tests cover rotation/size bounds, seven-day retention inside mixed-age segments, credential/header/URL/email/path redaction, oversized-event fallback, full/short exports and saturation of the bounded writer. A flush barrier cannot disappear silently. Crash capture has a bounded wait before passing control to Android.
- 22 API-37 native-graphics Compose tests cover navigation, compact/landscape layouts, source choices, readable themes, saved appearance, glass transparency, distinct risk/procedure explanations and the full-window drawing of every concrete animation mode in portrait and landscape.
- Layout/profile tests preserve configured Android window dimensions and system font scale; manual regions never replace actual installation facts. Seasonal/random policy cannot override a fixed animation choice. Slider preview/commit and theme persistence are tested.

Host APK tests inject PackageManager archive metadata because the framework mmap/signature parser is not a normal host-JDK parser. The app's manifest reader, APK signature verifier and set consistency checks still process actual signed bytes. Test network responses and document providers are controlled fixtures, not live Google Play or OEM services.

## Release artifacts and lint

The final Gradle command `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease` completed successfully in 6m 7s. Lint reports **0 errors and 313 warnings**, with no concealment through a lint baseline. The warnings mostly cover inherited translation resources, icon-shape/duplicate guidance, style and dependency recommendations. The full XML/HTML/text reports are included.

All four optimized release APKs independently passed v1/v2/v3 signature verification, version/package/SDK checks, a non-debuggability check, 16-KiB ZIP alignment and ELF LOAD alignment checks. Their signer is the retained certificate `6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6`. Application/activity icon resource references match. The complete reconstructed support URL is absent from the APK DEX/resources.

| APK | Native ABIs | Bytes |
|---|---|---:|
| arm64-v8a.apk | arm64-v8a | 3759531 |
| armeabi-v7a.apk | armeabi-v7a | 3756686 |
| x86_64.apk | x86_64 | 3760183 |
| universal.apk | arm64-v8a, armeabi-v7a, x86, x86_64 | 3809860 |

The universal APK retains x86 as well as the three requested split architectures. Exactly four APKs are distributed. The certificate subject retains its historical Android Debug name; the APKs themselves are release builds with `debuggable=false`.

## Remaining device acceptance

- Cem's OnePlus 15 with IN-ROM: actual installation, update/cancel flows, ROM detection, Play authentication/delivery, exported diagnostic ZIP through the phone's file picker and the OEM installation logo.
- Actual Android Keystore, PackageInstaller user confirmation/ownership, notifications, external installer handlers and all supported physical Android versions.
- Measured scrolling/FPS, GPU/CPU/memory use and battery impact. Host drawing tests do not establish device performance.
- All live provider responses. The prior unauthenticated APKMirror API probe returned HTTP 401; no shared credential is restored. Browser fallback remains available. This is an external access constraint, not a repaired API integration.
- Complete translation and accessibility/tablet matrix. German/English/system language selection exists; some technical explanations remain German.
- OBB expansion files and ambiguous variant archives remain intentionally unsupported. Unknown provenance/dependencies never become a compatibility approval through a simulated device.

The supplied APKs are release builds. RC2 remains a release candidate until real-device acceptance; no native-device pass or remote CI pass is claimed by these host results.
