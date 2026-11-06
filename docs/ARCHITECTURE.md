# Architecture and security

## Existing architecture

Upstream already uses Kotlin, Compose/Material 3, repository classes for eight sources, Koin injection, StateFlow view models, KryptoPrefs for persisted settings and WorkManager for background checks. These are retained. A wholesale rewrite or decompilation is unnecessary because the source is available.

`AppsRepository → provider repositories → UpdatesRepository → UpdatesViewModel → GlassUpdatesScreen` remains the update discovery path. Provider metadata produces provisional evidence, never installation approval. `UpdatesViewModel.verify` downloads an APK and evaluates actual bytes through `ApkAnalyzer`. The final installer independently repeats preflight, even if UI state was previously green.

## Components

| Component | Responsibility |
|---|---|
| CompatibilityEngine | Pure numeric/evidence-based decision; a known failure overrides unknowns |
| DeviceDetection | Public Build and package information; persistent manual device region; conservative namespace rules |
| compatibility-rules.json | Versioned package families, heuristic confidence and provenance; never grants regional compatibility |
| ApkAnalyzer | PackageManager archive parsing, apksig verification, current certificate set, SHA-256, ABI, binary manifest and owner information |
| ManifestReader | Size-bounded binary-XML reading; malformed/unsupported input fails closed |
| ArtifactMerge | Identical package/code/hash grouping; labels alone cannot prove byte identity |
| SessionInstaller | One preflight path, user-required PackageInstaller session, timeout and cleanup |
| GlassScene/GlassPanel | Shared recorded scene, backdrop blur and optional GPU refraction; separate sharp content |

## Decision rules

- RED: wrong package, non-increasing numeric code, invalid or conflicting signer without recognized lineage, unsupported minSdk/ABI, or missing required split.
- YELLOW: missing metadata, unresolved signature rotation, missing ROM provenance or unverified required dependencies.
- GREEN: all evaluated requirements pass. This is a technical preflight result, not a guarantee of runtime behavior.
- An UNKNOWN system/OEM result cannot be installed through Glass. Manually choosing IN proves only the user-selected device region.
- No provider ranking grants compatibility; no provider certificate string substitutes for verification of downloaded bytes.
- The target SDK is displayed; Android enforces additional target-version and ownership policies at commit. The current implementation does not predict every platform installation restriction.

## Signature handling

`ApkVerifier` validates APK bytes for the running platform. For an update with unchanged signers, the complete signer certificate set must equal the installed current signer set. Different signers with a lineage remain unknown until their platform update capabilities can be demonstrated. This deliberately rejects some legitimate rotations rather than falsely accepting them. Android always performs its own final signing and ownership checks.

## Installation lifecycle

Network downloads are HTTPS-only, redirects cannot downgrade TLS, calls have a timeout, and APK staging is bounded at 512 MiB. Staged files are inspected locally and removed in finally blocks. PackageInstaller requires user action, uses downloaded-file source classification and is serialized. Failure, cancellation and the three-minute confirmation timeout abandon the session. There is no silent-install permission, no root installation and no security bypass.

The local APK analyzer never starts installation. External APKMirror browsing is clearly labeled; Glass cannot enforce checks on an installation started in another app.

## Privacy

No advertising or analytics SDK is added. No account is required. Enabled providers receive package identifiers as necessary for update discovery; switching providers changes who receives them. Background checks start disabled. HTTP body logging is not attached. The preview does not yet provide the requested scrubbed diagnostic exporter. Do not share upstream raw logs as though they were automatically redacted.

## Upstream observations

Fork and upstream default branch matched at analysis time. Release 0.0.604-ci was the first item in the releases API. APKMirror previously reduced each package to the highest matching code before central review; Glass preserves returned candidates. Upstream XAPK installed every embedded `.apk`; Glass blocks this path. The self-update check referenced original APKUpdater releases even after changing the fork identity; this check is disabled. Empty change-log text previously called `last()` and could crash; this is fixed.

Relevant open upstream reports include #654 (dependency info block), #648 (Play dispenser default), #649 (update ordering), and #652 (workflow pinning). Dependency metadata is excluded from APKs and Play is opt-in. These observations are not proof that every upstream issue has been fixed.
