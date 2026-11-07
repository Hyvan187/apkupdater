# Android 17 review

Official references reviewed on 2026-11-06:

- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/build/releases/agp-9-1-0-release-notes

AGP 9.1.1 documents support for API 37.0, JDK 17 and Gradle >=9.3.1. The existing Gradle 9.4 wrapper is retained. This is a compatible supported baseline, not a claim to use every newest dependency.

| Area | Treatment and remaining validation |
|---|---|
| App memory limits | Bounded APK/manifest staging, one shared glass backdrop; actual memory profiling still needed |
| MessageQueue/static final reflection | No new hidden API reflection or static-final mutation; upstream/transitive runtime validation still needed |
| Certificate transparency | No bypass or trust weakening in app networking; verify each provider on API 37 |
| Native dynamic loading | No added dynamic native loading; packaged Compose native dependency must still be exercised on device |
| Large-screen resizability | No forced orientation; layouts use available width and scrolling. Full tablet/adaptive navigation audit remains outstanding |
| Edge-to-edge | Existing enableEdgeToEdge and Scaffold insets retained; portrait, landscape and compact host visual tests pass; real-device acceptance remains required |
| Package visibility | QUERY_ALL_PACKAGES is justified by installed-app update discovery; API-33 flags used where available |
| Installation and ownership | PackageInstaller USER_ACTION_REQUIRED, no silent-update permission, owner shown when public APIs allow it |
| Notifications | Existing notification permission request retained; notifications describe metadata candidates; APK verification/download remains an explicit action |
| Background work | WorkManager with connected/unmetered constraints, no exact-alarm permission added |
| OTP/contacts/IME/widgets/medical domains | No new use of these APIs; changes generally outside this app's scope |
| Old Android | Minimum API 23 retained; RenderEffect guarded at 31, AGSL at 33, package flags at 33, owner at 34 |

Compilation against API 37 does not prove complete Android 17 behavior compliance. Actual emulator/physical-device evidence is recorded separately in TEST-RESULTS.md.

## Revision 0.2 validation

The active UI now has an Android 17 native-graphics Robolectric test suite. This JVM Android runtime requires JDK 21; application bytecode remains Java 17. Espresso 3.7 is explicitly selected for the unit-test configuration to avoid the old InputManager hidden-method lookup in earlier transitive versions. Module export flags are test-only. Full physical-device installation and performance validation are still separate work.
