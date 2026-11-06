# Validation — 0.2.0

## Tests and rendering

The test suite contains 42 tests:

| Suite | Tests | Purpose |
|---|---:|---|
| CompatibilityEngineTest | 15 | Signatures/evidence, IN vs CN, SDK, ABI, splits, package and numeric-code rules |
| RegressionTest | 16 | Exact reported DocumentsUI downgrade, platform target regression, numeric version ordering and automatic ROM/conflict/locale rules |
| ArtifactMergeTest | 4 | Hash identity and keeping unproven regional variants separate |
| SourceHealthTest | 2 | Empty source cannot block another source; failed requests remain visible without raw private error details |
| GlassUiTest | 4 | Android 17 app launch/navigation, fixed settings back action, 320-dp layout, light theme, and blocked DocumentsUI card without download/install actions |
| Inherited ExampleUnitTest | 1 | Upstream arithmetic smoke test |

The native UI suite uses Robolectric 4.17 on API 37 with Java 21. Native PixelCopy rendering captures the actual Compose/Android code. PNG encoding uses host ImageIO to avoid a native PNG encoder truncation seen during the initial attempt. The UI test does not replace the app with HTML or generated mockups.

Screenshots in the delivery package:

1. Dashboard, dark: explicit readable foregrounds and floating navigation.
2. Updates: Ready/Check/Blocked sections and a real empty state.
3. Settings: native grouped controls and fixed back navigation.
4. Search: unified source-search layout.
5. Compact dashboard: 320-dp width, all three navigation tabs and search remain reachable.
6. Light dashboard: explicit dark text and darker semantic status colors.
7. DocumentsUI regression fixture: reported 17 → 14 values are rejected, without download/install actions.

The reference device name in these host-rendered screenshots is the Robolectric test device. An unknown ROM there is expected; synthetic property tests separately exercise IN/CN/EU/GLO detection and conflicts. The screenshot fixture is not an actual new scan of the user's phone.

## Confirmed final result

`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` completed with **BUILD SUCCESSFUL** on 2026-11-06. All 42 tests passed (0 failures/errors). Lint reports **0 errors and 245 warnings**. No lint baseline or disabled-error workaround was added. The API-23 compatibility error found during review was corrected in code.

## Exact build and lint outcome

The final output, test XML, full lint report, signature verification, APK SHA-256 and source commit are included in the delivery package. The associated PR records the confirmed final run. Earlier failed toolchain/test-harness attempts are not counted as successful tests.

## Limits

No physical OnePlus 15 is connected. PackageInstaller confirmation/ownership behavior, actual firmware-property readability on the user's ROM, provider end-to-end coverage, TalkBack, the full font/orientation/tablet matrix, FPS, memory and battery measurements are not claimed by these tests.

The Android 17 host test runtime required Java 21 module exports and an explicit compatible Espresso 3.7 test dependency. These changes apply only to the test process, not application permissions or hidden-API access.
