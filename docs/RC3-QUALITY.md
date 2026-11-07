# RC3 quality changes

The diagnostic format was checked for structure, counts, operational context and sensitive-data handling. Format 2 follows Android exit semantics and OWASP logging guidance; it is not a claim of external security certification. User-provided logs are not committed to the public repository.

Implemented: one transparency control under Appearance; consistent branding; explicit download-purpose API; structured trace IDs and terminal outcomes; readable error categories and cause chains; cancellation-aware network diagnostics; Play coverage without URL/header/body logging; typed fields and UTF-8-safe short exports; configuration/build/native-ABI metadata; tolerant legacy log reading; authentication cooldown/retry; debounced settings; foreground/background operation coordination; SDK workflow/namespace fixes.

The split APKs differ only in their native code. Most of this app is shared DEX/resources. Matching one-decimal MiB labels do not establish identical files. The release verifier checks actual native directories, distinct hashes, package/version, signatures and alignment for every output.

Original SDK references: https://developer.android.com/reference/android/app/ApplicationExitInfo and https://developer.android.com/build/configure-apk-splits . Logging guidance: https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html . CI setup: https://github.com/android-actions/setup-android .

Real OnePlus installation/cancellation, all live services, broad accessibility coverage and measured FPS/energy remain device acceptance. Startup, live metadata queries, local APK inspection and diagnostic export were observed in the supplied RC2 logs; those observations do not prove every RC3 device flow.
