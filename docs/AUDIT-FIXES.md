# Device-feedback audit and correction

Evidence: the user's current screenshots 71338, 71340 and visual reference 71342, plus inspection of the actual source. Screenshot-only observations are separated from logic/runtime tests. Original user screenshots are not published in this public repository.

| Step | Observed problem | Root cause | Correction |
|---|---|---|---|
| 1. Start/dashboard | Black text on dark blue glass, oversized hierarchy | MaterialTheme did not establish LocalContentColor outside a Surface | Explicit foreground scoping, light/dark palette, smaller hierarchy and grouped status summary |
| 2. Updates | DocumentsUI 17 → 14 listed despite larger versionCode | Installed/candidate version names and system platform branch were omitted from the compatibility model | VersionPolicy rejects older recognizable system branches before download and again during actual-byte preflight |
| 3. Settings | Manual EU/CN/IN selection implied a non-existent automatic capability | The previous region method only read a user preference | Selector removed; unprivileged firmware-property extraction, explicit signal mapping and conflict handling |
| 4. Navigation | Crowded five-item stock bar, clipped app label, no meaningful glass relationship | Stock BottomAppBar plus background-only sampled panels | Three floating main actions and detached search; navigation samples actual content while panels use a separate scene layer |
| 5. Source scan | Errors could look like no results; an empty GitHub flow could withhold all combined output | Silent catches and combine waiting for the first emission of every source | Initial source emission, independent combined updates, source health and timeout reporting |
| 6. Verification/install | Verification could be replaced by a new download; parallel updates could overwrite state | Checked file was deleted immediately; state read/replace races | Private staging cache, same-file hash recheck, atomic StateFlow updates and explicit blocked actions |

## Visual target interpretation

The supplied reference's useful relationship is a floating capsule with a rounded selected segment, a detached circular search action and tint drawn from content behind it. This is implemented natively in Compose. Update cards remain readable foreground surfaces; the navigation is the strongest backdrop effect. This does not copy unrelated music-player artwork or create a static background mock.

## Validation boundaries

Native screenshots come from the actual app code in Robolectric Android 17, not a web imitation or generated concept. The DocumentsUI-card screenshot is explicitly a regression fixture using the user's reported values; it is not a new scan of the user's phone. Physical device effects, system installation dialogs, TalkBack and performance remain separate tests.
