# Liquid-APK-Store 0.5 task audit (historical)

Current behavior and acceptance status: [RC2 audit](RELEASE-RC2.md). RC2 removes automatic APK downloads.

| Request | Implementation | Practical limit |
|---|---|---|
| APKMirror DNS/provider errors | Current-network DNS, one bounded transient retry, sequential chunks, no retries for denied access/rate limits, explicit diagnostic and browser fallback | A device/VPN/private-DNS/provider outage cannot be repaired by guessing addresses. Host test is not phone connectivity proof. |
| Explain blocked/check/restore | Per-control help dialogs, compact status and risk explanation, confirmation before restore | Technical preflight cannot guarantee app runtime or rule out malware. |
| Check all | Every eligible direct APK, one per app, sequentially, manual cancellation; failed candidates can retry | Web-only and unsupported archives remain manual/blocked. Private cache and free-space limits apply. |
| Sliders and color | Standard reset per slider, HSV circle plus HEX, independent accent/background opacity | Transparency blends in-app surfaces, not other apps behind Android. |
| OLED/animation | Black base, all 11 referenced choices, speed/density, optional gravity parallax and local seasonal mode | Effects pause with lifecycle/accessibility/power-saving. No measured battery-saving promise. |
| Update history | PackageInstaller success records; external handoff observed separately through installed version code | Observed version cannot prove which external actor performed the installation. |
| Alternative installer | Installed exported APK handlers; explicit consent; checked URI via restricted FileProvider | No bypass of Android restrictions; external progress/result may not be available. |
| Choose keep folder | Persisted SAF tree on all supported versions, or public default folder; success-only deletion | Android restricts some root directories; existing files do not move. |
| Favorites | Persisted stars and filters on installed-app and update screens | No modification of installed package state. |
| Modern-device automatic checks | 12 apps, 128 MiB each, 768 MiB aggregate default, configurable | Unknown sizes reserve full per-file limit; bounded sequential work protects resources. |
| Region/device simulation | Explicit saved manual comparison region; Play model request override after new authentication | Real device profile always controls install safety; no global Android identity spoofing. |
| Source defaults/branding | All eight sources enabled on first migration; Liquid-APK-Store; new robot/arrow logo | Account/source service restrictions still apply. GPL and copyright retained in Legal and source. |
| Donation | User-specified PayPal link, coffee button at bottom of Settings | Opens external PayPal; does not initiate payment. |

Final build/test outcome is recorded separately in TEST-RESULTS.md; implementation presence alone is not proof of validation.

Language integration: Android 13+ LocaleManager and the native App Language Settings page, with German and English declared in LocaleConfig and a system-default option. Older Android uses an app-local selection. Core English labels and primary explanations are present; some descriptions and diagnostics remain German. Unsupported system languages fall back to English. Arbitrary language selection does not supply a translation; no complete all-language coverage is claimed.

Device simulation sends only the explicitly chosen model field on the existing Play authentication request; it does not mutate the actual hardware profile, API level, ABI or installed-signature evidence. Regional comparison hints are never used to authorize unknown ROM APKs.
