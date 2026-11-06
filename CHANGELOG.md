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
