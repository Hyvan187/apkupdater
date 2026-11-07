# Liquid-APK-Store 0.6.0-rc2

A native Android APK discovery, inspection and update application with configurable liquid-glass visuals. Package `com.hyvan187.apkupdater`, version code 1007, minimum API 23, compile/target API 37.

## Updates and safety

- One update card per package; each source/variant retains its own evidence. Choose a source before manually downloading.
- **Ready:** the inspectable technical requirements passed. **Check:** important evidence is missing. **Blocked:** a concrete technical mismatch was found. Ready is not a runtime or malware-safety guarantee.
- Package name, numeric version code, recognizable system branch, signature, Android SDK, ABI, split requirements and detectable dependencies are checked. A higher code alone cannot authorize an older DocumentsUI branch (17 → 14).
- Downloaded APKs are never executed during inspection. ZIP/manifest size and decoded-text limits reject oversized or malformed structures. Installation re-inspects private bytes and requires Android confirmation.
- Manual batch checking covers eligible direct APKs sequentially, one per app. Web-only providers/unsupported archives are excluded. Cached evidence does not transfer between sources. Manual cancellation is available; an in-flight blocking network call may finish before stopping.
- No automatic APK downloads. Discovery, background work and reading reports only use metadata. Explicit batch defaults: up to 12 apps, 128 MiB per APK and 768 MiB total reserved budget. Limits are configurable. Unknown file sizes reserve the entire per-file limit. Private inspection cache and free-storage headroom are bounded.
- Risk is separate from technical status. Prereleases, system dependencies and another update owner raise warnings even when technical conditions pass.

## RC2 changes

- Play source selection resolves the entire BASE/SPLIT set only after an explicit action. Every component is inspected together; missing splits, mixed signers and unsupported expansion files are rejected.
- Successful installations are confirmed against PackageManager and reconciled across all sources, including late metadata results. Pending self-updates survive process replacement.
- Bounded local diagnostics provide standard/detailed capture and short/full ZIP exports. Settings include history deletion, safe cache clearing, language/model choices and a live actual/selected/effective region overview.
- Animated backgrounds span the full window. Appearance sliders preview without repeated preference writes; glass effects are cached and settings use reduced rendering work. The transparency setting remains effective in light/dark themes, dialogs and with blur disabled.

## Previous release candidate changes

- Source flows run independently on IO dispatchers.
- A single explicit APK set is checked as a unit and committed in one Android installation session; verification is repeated before installation.
- Shared preferences migrate to Android-Keystore-backed AES-GCM storage. Legacy plaintext is removed only after durable, verified migration; preferences are excluded from backup/transfer because their key is device-bound.
- jsoup is updated to 1.24.1; R8 retains persisted/reflected data models.
- The previous shared APKMirror Basic credential is removed. A direct unauthenticated API probe returned HTTP 401 on 7 November 2026. This source is not claimed repaired; browser fallback remains available.
- Host tests and release lint/build results are recorded in `docs/TEST-RESULTS.md`. Physical OnePlus/ROM/installer checks, full language coverage and measured graphics/energy performance remain acceptance gaps.

## Sources and diagnostics

APKMirror, GitHub, GitLab, F-Droid, IzzyOnDroid, APKPure, Aptoide and the existing experimental Play integration are enabled on the first 0.5 migration. Preferences persist afterward. Google Play uses the existing Aurora authentication service and shares device-profile fields needed by that integration; service availability is not guaranteed.

The missing ACCESS_NETWORK_STATE declaration has been added. DNS uses the current Android network and its private-DNS/VPN policy, without third-party DNS replacement. APKMirror transient DNS/timeouts get one bounded retry; denied/rate-limited API access is not retried. Partial batches can appear independently. Settings provides concrete failure advice, a DNS/HTTPS test and browser fallback. A working website does not prove API access. A network or provider failure on an unattached phone is not declared fixed by host tests.

## Interface

- Shared All / User / System / Google System filters, persistent favorite stars and filters.
- Small round info icons for technical settings and major checks; simple settings have no redundant help button; restoring hidden releases only clears exclusion records, never downgrades installed apps.
- Per-slider default reset; hue/saturation wheel, HEX input and independent color opacity.
- Recommended glass preset plus full appearance reset, font controls, OLED black base.
- Optional circles, rings, network, space, shapes, snow, grid, particles, lava lamp, none/random; speed, density, gravity parallax and local seasonal winter/New Year modes. Motion pauses outside RESUMED state, under reduced-motion preferences or power saving. No measured FPS/battery claim.
- Native Android 13+ app-language settings via LocaleManager and LocaleConfig, German/English selection and system default. Older Android has an app-local selector. English covers core controls and main help; some descriptions/technical/legal messages remain German. Unsupported system languages fall back to English. Arbitrary languages are not automatically translated or falsely declared supported.
- Voluntary coffee donation button opens the owner's PayPal page; the app initiates no payment.

## Files, installation and history

Manual direct APKs are copied to `Download/Liquid-APK-Store`, or a user-selected persisted document tree on any supported Android version. Android may restrict root directories; a subfolder can be created in its picker. Existing files are not moved. Inspection files stay private and are fetched only after an explicit download action.

Keep-APKs defaults off. Public copies are deleted only after confirmed installation success; aborted/failed attempts retain them. Identical files are reused by SHA-256. Private checked bytes remain the installation input. Browser downloads are outside app management.

Android PackageInstaller remains recommended. An optional installed, exported APK handler can receive an already-inspected file through a restricted FileProvider after explicit choice. External progress/success may not be available. Pending handoffs are reconciled with observed version codes on resume; those records explicitly say that the package state was observed, not that an external actor's execution was proven. Private staged files have size/time limits. No installer setting bypasses Android security.

The history records confirmed system-installer results and separately identified observed external-handoff outcomes, up to 500 entries. It is not a complete system history. Clearing it leaves apps and APKs unchanged.

## Region and test profiles

Automatic ROM detection remains independent from system language/location/SIM. Manual region is saved as a comparison hint. An explicitly simulated model is sent only on the next Play authentication request; resetting it invalidates cached Play authentication. No global Build identity, ABI or SDK is changed. Actual-device checks always govern installation. Unknown APK-region provenance is never approved by a manual choice.

## Install and build

Extract the release ZIP and install `arm64-v8a.apk` on a modern ARM64 phone such as OnePlus 15, or choose `universal.apk` for other devices. `armeabi-v7a.apk` and `x86_64.apk` are supplied separately. This is the optimized release build with `android:debuggable=false`; its version remains a release candidate because physical-device acceptance is incomplete. The retained 0.2/0.3/0.4 signer and package ID allow an in-place update. Independently signed CI builds may use another key. Private signing material is excluded from the distribution and source.

Use JDK 21 for API-37 Robolectric tests; app bytecode targets Java 17:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
python3 tools/normalize-sdk37.py
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Incremental Kotlin compilation is disabled for this pinned build baseline because its incremental cache produced duplicate declarations. For the retained development signer set `GLASS_DEBUG_KEYSTORE`; production release signing uses the existing `GLASS_KEYSTORE`/password/alias environment variables. No permanent release key is supplied.

See [task audit](docs/TASKS-0.5.md), [validation](docs/TEST-RESULTS.md), [architecture](docs/ARCHITECTURE.md) and [changelog](CHANGELOG.md). Physical OnePlus 15, real OEM file-manager/installer behavior, all live sources and performance/energy have not been tested here. Explicit APKM/APKS/XAPK sets can be inspected and installed through Android PackageInstaller when exactly one base, consistent package/version/signers and declared split dependencies are verified. Multiple base variants, incomplete/ambiguous requirements and OBB expansion files remain rejected. External APK handlers support individual APKs only.

## License and credits

GNU GPLv3; see [LICENSE](LICENSE). Includes APKUpdater code, copyright rumboalla and contributors, with modifications by Hyvan187 and contributors. Original notices and [upstream README](docs/UPSTREAM-README.md) are retained. Robot artwork attribution and modification information are in [NOTICE](NOTICE); in-app legal notices are separate from product settings copy.

Platform references: [app languages](https://developer.android.com/guide/topics/resources/app-languages), [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider), [Android robot](https://developer.android.com/distribute/marketing-tools/brand-guidelines).
