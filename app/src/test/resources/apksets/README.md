# APK set fixtures

These are signed, code-free Android test applications under `org.example.apkset` (and one deliberate foreign package). They use disposable test signing keys unrelated to the app release key. No private keys are included.

The archives cover a complete set, an explicitly required feature, a missing required feature, mixed signers, mixed version codes, mixed package names, and two alternative base APKs. They are input data for `ApkSetInspectorTest`, never runtime app assets.

The host tests use actual binary manifests and cryptographically verify the signed APK bytes. PackageManager archive metadata is injected because Android's framework mmap parser accesses Android-specific JDK internals. Physical-device archive parsing and installation are separate, still-required checks.
