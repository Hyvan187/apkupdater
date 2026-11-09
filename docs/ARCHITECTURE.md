# Architecture and security — 0.2

The original Kotlin/Compose/Material 3, Koin, Coroutines/StateFlow, repository and WorkManager architecture is retained. Active screens are replaced by a unified native shell; old upstream screen components remain source-compatible but are not used as the primary UI.

`AppsRepository → provider repositories → UpdatesRepository → CompatibilityEngine → UpdatesViewModel → native screens`

## Decision model

`InstalledPackage` contains numeric code, version name, target SDK, system status and ROM sensitivity. `Candidate` contains independent evidence for signature, min/target SDK, ABI, splits, dependencies and external region provenance. `VersionPolicy` applies known system branch conventions and conservative OEM numeric-version regression guards. It never equates an ordinary app's marketing version or target SDK with the phone's Android OS version.

A known failure wins over missing evidence. Missing facts stay unknown. Source ranking, a larger code, matching package name and device-region recognition cannot by themselves grant compatibility.

The reported DocumentsUI case is rejected before download. If a provider mislabels a file, ApkAnalyzer evaluates its actual package/version/SDK/signature and the installer repeats those checks.

## Automatic ROM detection

RomDetector is pure/testable. DeviceDetection invokes `/system/bin/getprop` without root, with a 1.5-second limit, a bounded response, and a region-property allowlist. It does not reflect into hidden SystemProperties APIs. The snapshot is held in memory, not uploaded. Explicit IN/CN/EUEX/GLOBAL/US-type signals are normalized; missing or contradictory signals return unknown. Samsung CSC mappings are deliberately limited. Model number, language, location and SIM are never sole evidence.

Property references include:
- https://github.com/oxygen-updater/build-props/blob/master/op11r/india/CPH2487_11_A.07.prop (`ro.oplus.pipeline_key=IN`)
- https://github.com/oxygen-updater/build-props/blob/master/op10pro/intl/NE2215_11_C.01.prop (`ro.oplus.pipeline_key=US`)

These are provenance for conventions, not proof that every current ROM exposes these values. OEM APK region provenance is a separate, unresolved data problem.

## APK and installation guard

ApkAnalyzer uses PackageManager, apksig, binary manifest parsing, native-library inspection, required public shared-library names, SHA-256 and update-owner information where available. Different signers with an unproven rotation remain unknown. Manufacturer-service dependencies cannot be inferred from a signature and remain unknown.

UpdatesViewModel keeps inspected files in private staging, keys them by immutable candidate identity plus installed code, and updates state atomically. Installation re-inspects the staged bytes, compares the hash with the inspected hash and passes the same file through the single serialized PackageInstaller path. USER_ACTION_REQUIRED, timeout, session abandonment and cleanup remain in place. No silent-install permission or root path is enabled.

## Source health

SourceHealth provides initial emissions, running status and sanitized failure messages. Empty flows cannot stall other sources. Provider catches propagate cancellation. Failure messages shown to users do not expose raw host names, tokens or request URLs. Partial source failures do not delete independent results.

## Graphics

GlassScene records the decorative scene separately from the full-screen content capture. MainScreen records actual scrolling content into a second layer; floating navigation samples that layer. This avoids capturing a component into its own input. GPU blur and optional AGSL refraction affect only the sampled backdrop; text remains sharp. Native foreground colors are scoped explicitly. API guards retain a Material fallback below API 31/33. No background animation loop is added.

## Privacy and signing

No analytics or advertising SDK is added. Enabled sources receive package identifiers for discovery. APK analysis is local. Public source/distribution archives contain no signing key. The development signing key has a separate private backup; CI debug builds have another signer unless explicitly configured with the backed-up key. No release signing is simulated with a debug fallback.


## 0.3 presentation and settings

`AppSelection` owns category migration and a shared StateFlow. `CategoryPolicy` uses both Android system flags. `UpdateGroup` groups by package strictly for presentation; candidates retain provider, version, ABI and verdict. No compatibility evidence is copied from a different provider. The source/variant sheet confirms manual download selection. `AppearanceStore` persists the full style state, while `LocalAppearance` feeds theme and glass rendering. The legacy preference namespace is stable despite the new app label.

`AutoCheckPolicy` selects at most six unresolved direct APKs, one per package, excluding known incompatible files, web-only providers, oversized files and already-ready groups. Both view model and WorkManager honor the same enabled/unmetered/per-file limit. The foreground cache retains checked bytes until view-model cleanup; worker files are removed after inspection. Background notification counts unique verified packages. No worker installs apps.


## 0.4 additions

ReleasePolicy combines recognizable version tokens with provider prerelease flags. ReleaseFilterStore propagates settings to search UI; update settings refresh discovery. Install preflight repeats the version-channel check. Google system classification is organizational, based on namespace plus Android system flags.

DownloadArchive uses MediaStore.Downloads on API 29+ and persisted document-tree permissions below 29. Writes finish before IS_PENDING is cleared. A global lock protects hash-keyed archive records. SessionInstaller never substitutes public bytes for its verified private input. Cleanup runs only after commit returns success and checks the current keep preference. Automatic inspection files are not published.

Incremental Kotlin compilation is disabled in this build baseline because repeated incremental rebuilds produced duplicate declaration diagnostics; clean full compilation is used for validation. This affects build speed, not app runtime.

## Version 0.5

`HistoryStore` records PackageInstaller success and separately describes observed external-handoff version outcomes. Pending reconciliation and file hashing run on IO, with a serialized store and bounded staging. Restricted FileProvider paths expose only staged checked files to the chosen handler. Android confirmation and actual-device preflight remain mandatory. A pending matching version is an observation, not proof of external execution provenance.

`FavoriteStore`, `AppLanguageStore` and `AppearanceStore` expose StateFlow. Contextual help and per-slider reset are shared controls. `UiEnglish` translates core controls/explanations through the app's selected locale; some diagnostics/help remain in their source language. Android 13+ LocaleManager owns native application locales, declared as de/en in LocaleConfig; older versions use the persisted app-local selector.

`MotionBackground` has bounded element counts and optional sensor input, using lifecycle, reduced-motion and power-saving gates. Default is off. OLED changes the base and static glow behavior; no energy measurement is implied. Color opacity blends in-app surfaces rather than enabling cross-app window transparency.

`ApkLimits` constrains decompressed manifest bytes; ManifestReader constrains cumulative decoded strings. APK ZIP preflight occurs before PackageManager parsing. Automatic requests use per-file/count/aggregate reservation budgets. Manual batch work can cover all eligible direct files while the private inspection cache is bounded; evicted bytes must be inspected again before installing.

Current-network DNS respects system/VPN policy, and APKMirror retries only transient DNS/timeouts once. Denials and rate limits stop further batches. ACCESS_NETWORK_STATE is declared for connectivity checks. No DNS service replacement or CAPTCHA bypass is implemented.

Manual region is a comparison hint. A chosen model is sent only in explicit Play authentication metadata. Actual Build API/ABI/firmware evidence always controls installation. The model option neither changes other applications' identities nor establishes APK ROM provenance.
