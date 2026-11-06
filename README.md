# APKUpdater Glass 0.2

Native Android update manager based on [APKUpdater](https://github.com/rumboalla/apkupdater). Package ID: `com.hyvan187.apkupdater`; version code 1002. GPLv3, copyright rumboalla and contributors; see [LICENSE](LICENSE) and [original README](docs/UPSTREAM-README.md).

## What changed after the device feedback

The 0.1 implementation showed unreadable text, an almost unchanged navigation, a non-functional manual ROM selector and an Android 14 DocumentsUI candidate for an installed Android 17 component. These issues are explicitly addressed in this revision:

- **Branch-aware system update policy.** `DocumentsUI 17 / 374200020 → 14 / 990090000` is rejected before downloading. A larger code does not override an older recognizable system branch. Actual downloaded APKs repeat this check; lower target platforms for installed system packages are rejected too. This is a conservative system-component policy, not a claim that ordinary version names or target SDKs always identify an Android release.
- **Automatic ROM detection.** Reads a bounded output from unprivileged `/system/bin/getprop`, retains only a firmware-property allowlist, and maps explicit vendor region signals. No manual region chooser, locale/SIM/GPS inference, model-only guess or fixed IN default. Missing or conflicting signals remain unknown. Device region never proves an external APK's region.
- **New application structure.** Three floating main tabs (Start, Updates, Apps), separate search capsule, contextual settings, compact app rows and a unified settings surface.
- **Content-aware glass navigation.** A separate captured layer contains actual scrolling content. The floating navigation samples that layer with GPU blur/refraction; panels sample a separate background layer to avoid recursive rendering. Foreground colors are explicitly scoped, including high-contrast light/dark status colors.
- **Three result groups.** Ready, Needs checking and Blocked. A blocked candidate has no download or installation action. Discovery is never labeled verified.
- **Verify once, install the same file.** APKs are retained in private temporary storage, hashed, cryptographically checked and re-inspected immediately before installation. The installed bytes must match the inspected hash. Files are deleted when the view model is cleared. No second unverified network download is substituted.
- **Independent source results.** Empty providers cannot withhold other results. Source failures and timeouts are visible; cancellation is propagated.
- **Explicit batch verification.** A user-triggered action can inspect direct APK candidates sequentially. It downloads files and does not silently install anything.

## Install

Unzip `APKUpdater-Glass-Debug.zip` and install `APKUpdater-Glass-Debug.apk`.

**Migrating from the earlier 0.1 build:** its ephemeral debug private key was not retained by that environment. This revision has a different signer, so Android requires uninstalling 0.1 once before installing 0.2. This clears the old app's settings. The new development key is backed up separately and is never included in the public source or normal distribution ZIP. Later local builds can reuse it via `GLASS_DEBUG_KEYSTORE`.

The package still coexists with official APKUpdater. Android's unknown-app permission and installation confirmation remain required.

## Use

1. Search for updates on Start.
2. Open Updates. Ready contains verified candidates; Check contains unverified discovery results; Blocked explains rejected variants.
3. Download and inspect an unresolved APK, or run the explicit batch inspection. Only a successful preflight enables installation.
4. Use Apps to find packages, open Android's app settings, or exclude a package from checks.
5. Use the local analyzer to inspect an APK without starting installation.

## Build and verification

The application targets Java bytecode 17 and Android API 37, minimum API 23. **The Android 17 Robolectric test runtime requires JDK 21.** With a full JDK 21 and Android command-line tools:

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Android 17 package is named `platforms;android-37.0` in the SDK catalog. Gradle 9.4 / AGP 9.1.1 are retained as a compatible baseline. CI builds/test reports and native UI render outputs are configured in `.github/workflows/build.yml`.

Optional local development signing:

```sh
export GLASS_DEBUG_KEYSTORE=/absolute/private/path/APKUpdater-Glass-Development.keystore
./gradlew :app:assembleDebug
```

That variable selects the standard debug alias/password; it is **not** release signing. Release keys remain opt-in through `GLASS_KEYSTORE`, `GLASS_STORE_PASSWORD`, `GLASS_KEY_ALIAS` and `GLASS_KEY_PASSWORD`. No key is committed. A CI build without the private development key has its own debug signer.

## Technical limits

- No external OEM-region provenance catalog is fabricated. Manufacturer APKs with unproved regional dependencies remain unverified and cannot pass the guarded installer.
- Split archives (APKS/APKM/XAPK) still require a complete selector/validator and are blocked in this build.
- New-signing-key rotations without demonstrated update capabilities remain unknown.
- Provider availability and formats can change. Source failures are visible; complete end-to-end coverage of all provider variants is not claimed.
- Native UI tests render Android 17 on the JVM; they are not a physical OnePlus 15 test, a PackageInstaller system-dialog test or a GPU/FPS/battery benchmark.
- The glow/refraction is a native Android implementation, not Apple's proprietary renderer.

See [audit and fixes](docs/AUDIT-FIXES.md), [architecture](docs/ARCHITECTURE.md), [Android 17 notes](docs/ANDROID17.md), [validation](docs/TEST-RESULTS.md) and [change log](CHANGELOG.md).
