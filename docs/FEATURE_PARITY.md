# iOS ↔ Android feature parity

The iOS app (`ios/`) and Android app (`android/`) must look, behave and feel the same.
Both are **native** apps: SwiftUI on iOS, Jetpack Compose on Android. Parity comes from
matching structure, copy, colours, behaviour and tests, not from shared UI code. Each app
still follows its platform's conventions where users expect them, e.g. the back gesture,
system dialogs, and Material vs. iOS controls.

## Rules

1. **One feature, one PR, both platforms.** A user-facing change lands in `ios/` and
   `android/` together, and this table is updated in the same PR.
2. **Mirror the structure.** Each feature has the same name and shape on both platforms:
   | iOS | Android |
   |---|---|
   | `ios/GymRat/Features/<Name>/<Name>View.swift` | `android/app/src/main/kotlin/…/features/<name>/<Name>Screen.kt` |
   | `…/<Name>ViewModel.swift` (`@Observable`) | `…/<Name>ViewModel.kt` (`ViewModel` + `StateFlow`) |
   | `ios/Packages/GymRatKit` (API client, models) | `android/core` (API client, models) |
   | `GymRatTests` (Swift Testing) | `app/src/test` (JUnit + Robolectric) |
   | `GymRatUITests` (XCUITest) | `app/src/androidTest` (Compose UI test on emulator) |
3. **Same UI identifiers.** Every iOS `.accessibilityIdentifier("x")` has an Android
   `Modifier.testTag("x")` with the same name, and vice versa. CI enforces this
   (`.github/scripts/check_ui_parity.py`, in the *Repo hygiene* job).
4. **Same copy.** User-facing strings are identical. On Android they live in
   `app/src/main/res/values/strings.xml`; on iOS they are inline in the views for now.
5. **Same colours.** Both use the brand palette in `android/.../ui/theme/Theme.kt`, which
   is taken from the iOS system colours: accent `#007AFF`, ready `#34C759`,
   not-ready `#FF9500`, error `#FF3B30`. Material You dynamic colour is off on Android.
6. **Same API contract.** Both clients' models mirror `backend/schemas/`. CI runs both
   clients' contract tests against the live backend (`.github/workflows/_contract.yml`).

## Feature matrix

| Feature | iOS | Android | Notes |
|---|---|---|---|
| Home: backend readiness (title, Refresh action, pull-to-refresh, per-dependency status) | ✅ `Features/Home` | ✅ `features/home` | Error text is the platform's own network-error message. |
| App icon | ⏳ placeholder | ⏳ placeholder | Add the real icon on both. |
| Release pipeline | ✅ TestFlight (`ios-release.yml`) | ✅ Play internal (`android-release.yml`) | Needs signing secrets (docs/CICD.md). |
