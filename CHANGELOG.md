# Change log

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
