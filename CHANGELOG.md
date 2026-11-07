# 0.6.0-rc3

- Review both supplied diagnostic formats; improve causal information, request/operation correlation, outcomes, timestamp and source-build metadata, cancellation/exit classification, safe short-export bounds and redaction.
- Place glass transparency only in Appearance; preserve all existing slider behavior and glass effects.
- Require an explicit purpose in every APK download call. Keep discovery and reports free of APK downloads.
- Cover Play networking in redacted diagnostics; bound calls, serialize authentication and avoid reauthenticating unchanged model selections.
- Debounce rapid settings changes, separate stale query status and share foreground/background update-operation serialization.
- Pause repeated auth/rate-rejected APKMirror API attempts and provide an explicit retry action.
- Unify application labels and verify architecture-specific native libraries and universal contents.
- Repair SDK setup and XML namespace preservation in CI. See validation records for the exact host/emulator coverage.

# 0.6.0-rc2

- Keep APK downloads behind explicitly named actions; migrate the retired automatic setting and never silently redownload a missing verified file.
- Enable Play BASE/SPLIT selection and complete-set download/inspection, with aggregate limits, checksums and temporary-file cleanup.
- Reconcile actual installed versions across all source variants and late emissions; preserve pending self-updates and confirm history only after observing the installed code.
- Add bounded, redacted local diagnostics, persisted capture levels and short/full exports; fix queue saturation, retention and crash-handler timing.
- Preserve and unify glass transparency, fix duplicate controls, full-window animations, seasonal precedence, cached effects and deferred slider persistence.
- Reorder settings, provide glass language/model choices, live actual/effective region information, safe cache clearing and confirmed history deletion.
- Serialize discovery and explicit checks, cancel individual/batch work, count unsuccessful checks accurately and reject stale progress callbacks.
- Ship four signed, optimized release variants with the existing signer, a unified launcher/installer logo and monochrome adaptive icon.
- See docs/TEST-RESULTS.md and docs/RELEASE-RC2.md for validation and remaining real-device acceptance checks.

# 0.6.0-rc1

- Build an optimized, resource-shrunk, non-debuggable universal release candidate with the retained signing identity for in-place updates.
- Replace question marks with small round info icons; retain explanations on technical options and remove redundant help from simple settings.
- Inspect explicit APKM/APKS/XAPK sets, validate all APK signatures/package/version/dependencies, reject missing/ambiguous components, and install the checked set in one Android PackageInstaller session.
- Migrate preferences to device-bound Android Keystore encryption with durable verification before legacy plaintext removal; exclude preferences from backup and transfer.
- Dispatch source flows independently; handle empty/changed APKMirror search markup without positional indexing.
- Remove the old shared APKMirror credential and update jsoup to 1.24.1. Unauthenticated APKMirror API access still returns 401; it is not declared fixed.
- Preserve reflected Gson models under R8. Add signed-set, migration and selective-help/landscape tests.
- This remains a release candidate. Physical OnePlus testing, live OEM installation, verified ROM provenance, full translation and performance acceptance remain open.

# 0.5.0-store

- Rename product to Liquid-APK-Store, replace robot/update icon and move credits to separate legal notices.
- Fix missing network-state permission, add current-network DNS, bounded provider retry and actionable diagnostics/web fallback.
- Add contextual explanations, per-slider defaults, HSV wheel/opacity, OLED and optional lifecycle-bound animation modes.
- Add favorites, installation history, external checked-APK handoff and folder selection on modern Android.
- Expand manual batch checks, configurable automatic resource budgets and bounded private cache.
- Add explicit region/model test preferences without weakening actual-device safety.
- Add native app-language selection (German/English/system; partial English descriptions) and owner donation link.
- Bound APK structure/manifest decoding before package analysis; retain signature, downgrade and split guards.

# 0.4.0-liquid

- Repair appearance navigation, duplicate entry text and system-bar-safe scrolling.
- Add recommended glass values and full default reset.
- Unify prerelease filtering and add Google-system scope with wrapping controls.
- Add public direct-APK downloads, SHA-256 reuse and success-only deletion controlled by a keep preference.

# 0.3.0-liquid

- Rename to Liquid-APK-Updater; new dark glass launcher icon.
- Add live persisted appearance controls, independent HEX/HSV colors, typography, motion and glass settings.
- Group updates by package and offer provider/variant selection without transferring evidence between files.
- Use shared General/System categories, including updated-system flags; migrate obsolete OPlus scope.
- Add bounded automatic APK preflight in foreground/background with unmetered default and size controls.
- Simplify update facts; move verbose evidence to an optional dialog.
- Explain provider errors; preserve provider identity during discovery and support release-wide ignore/reset.
- Retain 0.2 signing identity and preference storage for in-place updates.

# Change log

## 0.2.0-glass — 2026-11-06

- Rebuild the active native UI around three floating main tabs, separate search and contextual settings.
- Scope text colors correctly; add explicit light/dark contrast and unified settings/app rows.
- Capture actual scrolling content behind the glass navigation, with GPU blur/refraction.
- Remove the manual ROM selector; add bounded unprivileged firmware-property detection and conflict handling.
- Reject DocumentsUI Android 17 → 14 despite a larger source code, and reject system target-platform regressions.
- Separate Ready, Check and Blocked result categories; no download/install action for blocked candidates.
- Stage inspected APKs and install the same hashed bytes after another preflight.
- Fix empty-provider combination, surface source failures/timeouts and preserve coroutine cancellation.
- Add source-flow, automatic ROM and user-reported downgrade regression tests plus native Android 17 UI rendering/navigation tests.
- Back up the new development signing key outside public Git/distribution artifacts. The prior 0.1 signer is unavailable, requiring one uninstall for migration.

## 0.1.0-glass-dev — 2026-11-06

- Fork identity com.hyvan187.apkupdater and Android 17 build target.
- Conservative evidence-based compatibility engine and explicit unknown state.
- Persistent manual ROM region; no locale-based region inference.
- Local APK cryptographic/manifest/ABI analyzer and separate candidate verification action.
- Replace unguarded root/archive installation with a single preflight-guarded PackageInstaller path.
- Preserve APKMirror candidates instead of selecting solely the highest code.
- Disable upstream self-update lookup for the renamed fork.
- Shared glass backdrop, GPU blur, optional refraction, interactive highlights, dashboard and system-app inventory.
- Pure compatibility/merge regression tests, CI build and Android 17 smoke-test job.
- Explicitly document outstanding regional provenance, split support and device validation.
