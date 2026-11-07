# Validation: Liquid-APK-Store 0.6.0-rc3

7 November 2026. Package `com.hyvan187.apkupdater`, version `1008 / 0.6.0-rc3`, min/target/compile API `23 / 37 / 37`.
Build baseline: JDK 21.0.2, Gradle 9.4.0, AGP 9.1.1, Compose compiler 2.3.10; application bytecode targets Java 17.

**183 host tests passed: 0 failures, 0 errors, 0 skipped.** These are newly executed RC3 results, not the historical RC1/RC2 counts.

| Suite | Tests |
|---|---:|
| ExampleUnitTest | 1 |
| DiagnosticQualityTest | 9 |
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
| RC3QualityTest | 7 |
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

## RC3 release validation

- All 183 current host tests passed: zero failures, errors or skips.
- Release lint completed with zero errors and 292 disclosed warnings (including historical translation/resource/style warnings). Full reports are distributed; no new blanket suppression or baseline was added.
- Four optimized, non-debuggable release APKs passed signature v1/v2/v3 checks with the retained certificate SHA-256 `6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6`, package/version/SDK and branding checks, actual native-ABI inspection, 16-KiB ZIP and ELF LOAD alignment, installer/launcher icon-reference equality and plaintext support-destination checks.
- Exact byte sizes: ARM64 3,759,211; ARMv7 3,756,377; x86_64 3,759,872; universal 3,809,551. Their hashes are different. Matching rounded 3.6-MiB labels reflect the small native payload rather than duplicated APK files.
- GitHub run [42397252659](https://github.com/Hyvan187/apkupdater/actions/runs/42397252659), implementation commit `bd7e60927065261e982fb4ff0f550ae642c72cc0`, passed both build (host tests/lint/debug APK) and native Android-17 smoke jobs. The emulator installed the debug APK, started the Activity with `Status: ok`, and confirmed a running process after the wait without the checked application fatal-exception marker. Evidence includes job logs, screenshot and logcat. This is startup validation, not an end-to-end installer or performance measurement.
- The final documentation-only commit does not change application sources or build fingerprint `388d96eb7d692255dbd085eb9e4855c10558648b317c2f38ac608bccd904eb72`. Its exact commit/tree are recorded in the delivery manifest. CI debug signing is separate from the retained release signer.

SDK bootstrap and namespace-preserving normalization were also independently verified in GitHub run 42395989264 on the retained RC2 application baseline. The RC3 result above is its own current-implementation run.

## Remaining device acceptance

- Cem's OnePlus 15 with IN-ROM: actual installation, update/cancel flows, ROM detection, Play authentication/delivery, exported diagnostic ZIP through the phone's file picker and the OEM installation logo.
- Actual Android Keystore, PackageInstaller user confirmation/ownership, notifications, external installer handlers and all supported physical Android versions.
- Measured scrolling/FPS, GPU/CPU/memory use and battery impact. Host drawing tests do not establish device performance.
- All live provider responses. The prior unauthenticated APKMirror API probe returned HTTP 401; no shared credential is restored. Browser fallback remains available. This is an external access constraint, not a repaired API integration.
- Complete translation and accessibility/tablet matrix. German/English/system language selection exists; some technical explanations remain German.
- OBB expansion files and ambiguous variant archives remain intentionally unsupported. Unknown provenance/dependencies never become a compatibility approval through a simulated device.

The supplied APKs are release builds. RC3 remains a release candidate until physical-device acceptance; host results alone do not establish a native-device or remote CI pass.
