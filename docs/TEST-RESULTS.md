# Validation — Liquid-APK-Updater 0.3.0

Local validation on 6 November 2026 uses JDK 21, Gradle 9.4, AGP 9.1.1 and Android SDK 37. Production bytecode targets Java 17. The complete command is:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -Pkotlin.incremental=false
```

The test suite contains **60 tests**, with zero failures/errors in the successful verification run:

| Suite | Tests | Purpose |
|---|---:|---|
| CompatibilityEngineTest | 15 | IN/CN evidence, signatures, SDK/ABI, splits, package/code rules |
| RegressionTest | 16 | Exact DocumentsUI 17 → 14 regression, target regression, numeric versions, ROM detection/conflicts |
| ArtifactMergeTest | 4 | Byte identity and distinct unproven variants |
| SourceHealthTest | 2 | Empty provider isolation; actionable sanitized failure |
| LiquidRegressionTest | 16 | Provider grouping, per-file evidence, package identity, categories, network/size policies, HEX validation, readable ABI labels, source diagnosis |
| GlassUiTest | 6 | API-37 navigation, compact screen, light appearance, DocumentsUI block, provider confirmation before manual download, persisted colors and shared category selection |
| Inherited ExampleUnitTest | 1 | Upstream arithmetic smoke test |

Native UI tests use Robolectric 4.17 on API 37. PixelCopy captures native Compose content, not an HTML replacement or generated mockup. Host rendering is not a physical-device installation. The source chooser test asserts its modal UI and verifies that selecting Aptoide reaches the callback only after confirming download. Main-window screenshots do not capture the separately rendered modal window.

The build produces an APK with package `com.hyvan187.apkupdater`, version code `1003`, version name `0.3.0-liquid`, minimum API 23 and target API 37. Android apksigner verifies its v1/v2 signatures. The certificate SHA-256 is `6d3055d0260d6afba37c6f8505b2e556e47d56d8363c0399fc43a5f0e11e28d6`, matching the retained 0.2 distribution certificate. The distribution includes exact APK SHA-256, source commit, native screenshots, test XML, lint HTML/XML and final build log.

## Important limits

- No physical OnePlus 15/IN installation or firmware interaction was executed here.
- API-37 Robolectric exercises launch/navigation/rendering; it does not prove a real PackageInstaller confirmation flow, OTA/OEM behavior, ownership behavior or notification delivery on the user's device.
- No live end-to-end provider availability, every remote APK, CPU/GPU/energy profiling, frame-rate measurement, tablet/landscape/accessibility matrix or background scheduling timing is claimed.
- Missing ROM provenance and dependencies remain unknown. Signature matching alone cannot establish regional runtime compatibility.
- Split/APKM/XAPK installation remains blocked; complete required splits are not guessed.
- Source authentication/403/CAPTCHA restrictions are not bypassed. APKMirror uses manual download plus local inspection.
- Automatic inspections are bounded to six unresolved direct APKs per search and the configured per-file limit, default 64 MiB/unmetered. No automatic installation occurs.
- GitHub Actions and an Android 17 emulator smoke job are configured. Their execution is separate from the confirmed local Gradle result; inspect the repository's Actions page before claiming CI success.
