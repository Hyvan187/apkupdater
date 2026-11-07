# Universal APK Upgrader (UniAPK) 0.7.0-rc1

Native Android application for discovering APK updates and inspecting packages before requesting installation. Product identity: `org.uniapk.upgrader`, version code 1009, min/compile/target API 23/37/37. This new neutral package installs alongside the earlier application; it cannot read another package's private settings or history.

## Update workflow

Discovery requests metadata. Automatic APK downloads are off by default and require an explicitly selected persisted writable document-tree folder. Opted-in downloads inspect supported packages, save compatible files there and never install automatically. Metered-network restrictions are separately configurable.

A single preparation opens a live process dialog: bytes and percentage when the provider supplies a reliable total, inspection, evidence and an Install button. Dismissing a finished preparation leaves its checked bytes under Ready temporarily. Reinspection is mandatory before installation. A pure batch compatibility check deletes its inspection downloads and publishes no public copy; installation then requires a new explicit preparation. A missing cache file never causes a silent download.

Actual PackageManager versions, including equal visible version names, are reconciled across sources. Unknown origin, conflicting signatures, incompatible Android/ABI/branches and ambiguous or incomplete sets never become an approval through simulation. Split sets are checked together. OBB/variant archives remain unsupported. No inspection executes APK code or guarantees runtime or malware safety.

## Profiles and sources

Product and region are selected separately. Public model/device catalog facts come from manufacturers and Google's published supported-device catalog. Unsupported regional assignments are disabled rather than invented. Store requests use the selected model/manufacturer/device identifier and locale hint; account/IP country and store entitlement still apply. Actual install compatibility always uses the real phone. Region detection supports additional Samsung active CSC/OMC, Xiaomi firmware suffixes, OPlus, Huawei/Honor and Motorola signals; unreadable or contradictory firmware stays unknown. Language, SIM and GPS are not substituted as ROM evidence.

APKMirror credentials are optional and empty by default: no current provider-authorized general-purpose shared key has been established. Without a key, each update's source picker offers the APKMirror search for its exact package; provider-supplied release URLs are opened directly. Web search is not presented as a confirmed extra update. GitHub/GitLab tokens are optional; only the exact intended HTTPS host receives a credential. F-Droid, IzzyOnDroid and Aptoide have no personal-key fields. Google Play uses the existing anonymous Aurora authentication. Keys are kept in encrypted private preferences and never logged. Release notes are shown only when supplied by the source.

## Storage and performance

Default limits: 10 packages, up to 512 MiB per package set, 2 GiB reserved per batch; actual budget also reserves at least 1 GiB, allows for up to five temporary package copies and caps the raw download budget at 20% of available space. Unknown lengths reserve the per-file limit. These are engineering defaults, not a claimed industry standard. Google documents base modules up to 500 MB and larger cumulative deliveries; a small updater should not automatically fetch multi-gigabyte games.

Network cache: 64 MiB; image disk cache: 32 MiB; app-controlled checked-file cache: 1 GiB, aged after one hour and maintained on startup/scan. Active operations and pending installer handoffs are protected. Cache settings show a live count of the app-owned bytes that can be cleared. Explicit public downloads are user files and are not evicted by cache cleanup; confirmed installs remove managed copies when Keep APKs is off.

CPU selection limits concurrently running app-owned computational tasks, and reduces animated-background cadence/density and glass quality for small budgets. Android still schedules UI, network and renderer threads; it is not a hard whole-process core-affinity or CPU-percentage limit. Five extra colorful effects use a scrolling choice menu. Adaptive icons have a larger safety inset for different launcher masks.

## Diagnostics and privacy

Automatic recording stays in this app's private files directory; export is a separate explicit action using Android's document picker. General diagnostics retain error/operation context with minimal metadata; exports strip device profile, app package names, free-text exception messages and stack traces. Detailed diagnostics add technical device/display configuration, participating package names and longer cause/stack information, with explanation and explicit confirmation on selection and export. Credentials, unique hardware identifiers, personal contents, bodies, full URLs and private paths remain excluded/redacted.

Choose 3, 5 or 10 local rolling files, each up to 512 KiB, with a seven-day age bound. Exported ZIPs are independent user files. Bounded writer queues report dropped records. Diagnostic data is excluded from Android backup. No raw user logs, personal device snapshots or private signing keys are embedded in the APK/source distribution. The retained obfuscated donation destination is intentional; public model catalog identifiers are product facts, not user identifiers.

## Build and verification

JDK 21, Gradle 9.4.0, AGP 9.1.1, API 37. Application bytecode targets Java 17. Install `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`, run `python3 tools/normalize-sdk37.py`, then `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease`. Supply release signing through the `GLASS_KEYSTORE`, `GLASS_STORE_PASSWORD`, `GLASS_KEY_ALIAS` and `GLASS_KEY_PASSWORD` environment variables. Signing material is not distributed.

Test/lint/native-startup evidence and exact four-ABI package hashes accompany the release. Live Play delivery, OEM install/cancel, unreadable-ROM fallbacks, physical launcher masks and measured performance remain physical-device acceptance. No external certification or complete error-freedom is claimed.

## License

GPLv3; see LICENSE and NOTICE. Based on APKUpdater, copyright rumboalla and contributors. Original legal notices and docs/UPSTREAM-README.md are retained. Project modifications by UniAPK contributors. The Android robot attribution remains in NOTICE.
