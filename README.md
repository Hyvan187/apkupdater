# APKUpdater Glass

Native Android fork of [APKUpdater](https://github.com/rumboalla/apkupdater), based on commit `69b6fcdf52a7735ae17101efe1a0cd26222fb276` / `0.0.604-ci`.

**Development preview, not the completed master specification.** Package ID: `com.hyvan187.apkupdater`. Copyright of upstream code remains with rumboalla and contributors. All derivative code is GPLv3; see [LICENSE](LICENSE) and [upstream README](docs/UPSTREAM-README.md).

## Available functions

- Android 17 / API 37 build target, Java 17, AGP 9.1.1; existing Compose, Material 3, Koin, Coroutines and WorkManager architecture retained.
- Dashboard, searchable system-app inventory, updates, source search and settings.
- Persistent AUTO/EU/GLO/IN/CN/OTHER device-region choice. AUTO deliberately reports UNKNOWN until documented manufacturer-specific evidence is available. Neither language, SIM, country, model number nor matching certificate is a regional proof.
- Pure compatibility engine with separate evidence for package, numeric version code, SDK, ABI, signature, splits, dependencies and relevant ROM region.
- Unknown, incompatible and compatible candidates can be inspected separately. The default recommendations show compatible candidates only.
- Explicit download-and-verify operation for ordinary direct APK links. Cryptographic verification with Google's `apksig`, current signer comparison, SHA-256, binary manifest inspection, native ABI inspection and update-owner display.
- Verified identical byte hashes can merge source cards. Without a hash, source/variant identity remains separate.
- One guarded PackageInstaller path, mandatory Android confirmation, bounded downloads, HTTPS, cleanup, cancellation and a session timeout. Root installation is disabled.
- Dark violet/blue glass scene, actual in-app backdrop sampling with RenderEffect blur (API 31+), AGSL distortion (API 33+ high setting), pressure response, local light highlights and Material fallback. Text stays sharp. The sampled backdrop is the app's decorative scene, not arbitrary other scrolling UI or wallpaper from outside the app.
- Existing providers retained; no CAPTCHA circumvention or access-protection bypass added. APKMirror opens its source page and external downloads are outside Glass's installation guard.
- WorkManager checks on unmetered networks by default; verified candidates only generate update notifications.

## Important limits

- OEM and system APKs have **no bundled verified region provenance or proprietary-service compatibility database**. They remain UNKNOWN and are blocked by the guarded installer, even after manual device-region selection. This preview avoids false regional approvals; it does not yet provide a verified regional OEM update catalog.
- APKS/APKM/XAPK and multi-file split installations are blocked. Required splits detected in a standalone APK are rejected. No claim of complete bundle selection is made.
- Signature rotation is conservative: a different signer with a lineage is UNKNOWN, pending proof of update capability; matching history alone never grants an update.
- No real OnePlus 15 hardware validation or CPU/GPU/battery/FPS benchmark has been performed.
- Source reachability alone is not an end-to-end provider test. Authentication, metadata completeness, variant enumeration and downloadable results still need service-specific integration tests.
- Source priorities, full update history, per-app preferred source, automatic regional detection, fully adaptive tablet navigation, a redacted diagnostic-file exporter and complete split support remain outstanding.
- The upstream semantic-version searches are discovery hints; they are never sufficient to authorize installation.
- Debug keys differ between local and CI builds. Use a backed-up private release key for long-term updates. No private key or password is checked in.

## Install

Download `APKUpdater-Glass-Debug.apk` from a successful Actions run or the supplied deliverable. Android will ask for permission to install unknown apps. The app installs beside the original APKUpdater. Choose the actual ROM region in Settings, if known. To inspect unresolved candidates, change the Updates filter to include them. Download verification is separate from installation.

## Build

Install a full JDK 17 and Android command-line tools, then:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`compileSdk = 37` resolves the major Android 17 SDK. In the current SDK repository, its package name is `platforms;android-37.0`, not `platforms;android-37`.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. CI renames it, verifies its signature, records SHA-256 and the commit, and uploads test/lint reports. A separate CI job attempts an Android 17 emulator launch and retains logs and a screenshot.

Release signing is opt-in via environment variables `GLASS_KEYSTORE`, `GLASS_STORE_PASSWORD`, `GLASS_KEY_ALIAS`, `GLASS_KEY_PASSWORD`. An absent release key does not fall back to an official-looking debug-signed release.

See [architecture and security](docs/ARCHITECTURE.md), [Android 17 review](docs/ANDROID17.md), [test results](docs/TEST-RESULTS.md), and [change log](CHANGELOG.md).
