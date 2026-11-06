# Liquid-APK-Updater 0.4

Native Android update manager based on [APKUpdater](https://github.com/rumboalla/apkupdater), developed in [Hyvan187's fork](https://github.com/Hyvan187/apkupdater). GPLv3; copyright rumboalla and contributors. See [LICENSE](LICENSE) and [upstream README](docs/UPSTREAM-README.md).

Package: `com.hyvan187.apkupdater`, version code 1004, Android API 23–37, compile/target 37.

## Version 0.4

- Single appearance entry and correct nested back navigation; screen transitions no longer overlay each other. Scroll areas respect the bottom system bar.
- Recommended glass preset (Standard, 46% opacity, blur/refraction enabled), retaining custom colors; separate full default reset.
- Shared All / General / System / Google System categories. Google packages require a system flag plus a recognized package namespace. This grouping is not cryptographic identity verification; System includes all system apps.
- Central Alpha/Beta/Preview policy across catalogs, search, cached actions and actual-file installation. Unmarked prereleases cannot be inferred reliably.
- Public manual-download archive: **Download/Liquid-APK-Updater** on Android 10+. Older versions use a user-selected SAF folder. Automatic temporary checks remain private.
- A keep-downloads preference controls public-copy deletion only after confirmed successful installation. Failure/cancellation retain copies. Private checked bytes remain the installation input. Existing public copies are reused by SHA-256; browser downloads are outside this policy.

## What's new

- **One card per installed package:** discovery from different providers is grouped without sharing signatures or compatibility verdicts between files. Pick a provider and, when available, an individual variant before manually downloading. Names from different catalogs no longer create duplicate cards.
- **Appearance:** dedicated page with System/Light/Dark, Material You, independent free button/accent and background colors (HEX and HSV controls), system font families, additional text scaling, reduced motion, blur/refraction switches, navigation blur strength, quality and opacity. Settings are persisted and applied immediately. No proprietary fonts are bundled.
- **Consistent categories:** All, General apps, System apps. Android's system and updated-system flags determine membership. One shared selection drives settings, app list and update queries. The separate OPlus category is removed; its old setting migrates to System.
- **Concise update information:** old → new version, ARM64/ARMv8 where known, sources and state. Technical evidence and SHA-256 are in an optional details dialog.
- **Automatic technical checks:** after searching, up to six unresolved direct APKs are downloaded sequentially and inspected. Default: unmetered connection, 64 MiB maximum per APK. Settings allow disabling this or selecting 32/64/128 MiB. Background searches use the same policy. Manual single and batch checking remain available. This never silently installs an app or guarantees runtime functionality.
- **Actionable source errors:** HTTP 401/403/404/429/5xx, DNS, TLS, connection and timeout failures are described in the app, without raw URLs or private exception messages.
- **Dark glass launcher icon** and the new application name.

## Safety

A larger version code alone does not establish compatibility. The exact reported `DocumentsUI 17 / 374200020 → 14 / 990090000` case is rejected as an older system branch. Actual files repeat version, package, SDK, ABI, signature and split checks. Device-ROM detection uses available firmware signals; language, location and SIM do not establish the firmware region. Missing or contradictory evidence remains unknown. Matching signatures do not prove ROM compatibility.

Each provider's APK keeps its own verdict. Checked files are retained privately, hashed and re-inspected before PackageInstaller receives those same bytes. Unknown system-package compatibility blocks installation. Android confirmation and unknown-app permission remain required. No root, security bypass, advertising or added tracking.

## Install

Extract `Liquid-APK-Updater-Debug.zip` and install `Liquid-APK-Updater-Debug.apk`.

The 0.4 distribution reuses the retained 0.2 development signing key and package ID. It can update that 0.2 build in place, retaining preferences under the existing preference-store name. A different independently signed build cannot update it. The older 0.1 build used a different key and requires a one-time uninstall. Private keys are never included in the public source or application ZIP.

## Use

1. Search from Start. Eligible direct APKs are checked automatically according to your network/size settings.
2. Updates groups apps into Ready, Check and Blocked. Open a card for concise details or its technical report.
3. Select a source/variant for manual inspection; installation is enabled only after successful inspection.
4. Apps offers General/System filters, search, Android app settings and per-app exclusion.
5. Settings → Appearance customizes colors and effects. Reset restores the default dark violet/blue design.
6. APK Analyzer inspects a locally selected file without installing it.

## Build

Use JDK 21 for the Android 17 Robolectric test runtime; production bytecode targets Java 17. Gradle 9.4, AGP 9.1.1, Kotlin and Compose versions are pinned in the build files.

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For a debug APK signed with your existing development key, set `GLASS_DEBUG_KEYSTORE` to its absolute path. Otherwise Gradle uses its local debug key. Release signing uses `GLASS_KEYSTORE`, `GLASS_STORE_PASSWORD`, `GLASS_KEY_ALIAS`, `GLASS_KEY_PASSWORD`; no production release key is provided or stored here.

GitHub Actions builds tests, lint and APK artifacts. Its separate Android 17 emulator job is configured; the local report does not claim it ran. Independently generated CI debug keys differ between clean runners unless a persistent key is configured privately.

## Limits and evidence

- Some providers supply only web pages, missing SDK/ABI/signature metadata or unsupported archive formats. APKMirror remains a manual page download followed by local inspection. Access controls and CAPTCHAs are not bypassed.
- APKM/XAPK/split sets remain blocked for installation until complete dependency checking is supported. Single APKs are supported; no unsupported format is represented as verified.
- OEM-region provenance is often unavailable. The app cannot infer an external APK's region from your device, its signature or its version number.
- Automatic checking is bounded to six apps per search and the configured per-file limit. Files without direct supported downloads stay open for manual action.
- Automated checks establish technical package conditions, not how an app will behave after installation. Root-only functions are unavailable.
- Physical OnePlus 15 installation, GPU/energy profiling and every provider's live service status are not verified in this environment. See [test results](docs/TEST-RESULTS.md), [architecture](docs/ARCHITECTURE.md), [Android 17 notes](docs/ANDROID17.md), [changelog](CHANGELOG.md).
