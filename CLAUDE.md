# NextLevel GymRat

Monorepo: `ios/` (SwiftUI app + `GymRatKit` Swift package), `android/` (Jetpack Compose app +
`core` Kotlin module) and `backend/` (FastAPI), which both apps share.
`backend/CLAUDE.md` covers the backend architecture. It was copied from the
python-webservice-template and its patterns apply unchanged.

## Principles (inherited from the templates repo)
- Test against real services, not mocks. Backend tests use testcontainers. The
  iOS API client is contract-tested against the real backend in CI (`_contract.yml`).
  The Android API client (`android/core`) is contract-tested the same way.
  A hand-written stand-in (`HealthChecking` conformer/implementation) is acceptable only where
  a live backend is not available (macOS runners and emulators have no backend). When you use one, say why.
- Leverage the compiler: Swift 6 language mode with strict concurrency, and Swift
  warnings are errors. Kotlin warnings are errors too (`allWarningsAsErrors`). Pyright (standard mode) for Python must report zero errors:
  fix the types rather than adding `# type: ignore`.
- All backend functionality is exposed over REST. The apps talk to it only through
  `GymRatKit.APIClient` (iOS) and `com.nextlevel.gymrat.core.ApiClient` (Android).

## iOS ↔ Android parity (read docs/FEATURE_PARITY.md)
- The two apps must look, behave and feel the same. Any user-facing change is made on
  **both** platforms in the same PR, and the feature matrix in `docs/FEATURE_PARITY.md` is updated.
- Mirror names and structure. `Features/<Name>/<Name>View.swift` + `<Name>ViewModel.swift` ↔
  `features/<name>/<Name>Screen.kt` + `<Name>ViewModel.kt`. `GymRatKit` ↔ `android/core`.
  Tests mirror each other file for file.
- Every iOS `.accessibilityIdentifier("x")` has an identical Android `Modifier.testTag("x")`.
  CI enforces this (`.github/scripts/check_ui_parity.py`). Use string literals, not constants,
  so the check can find them.
- Copy is identical; Android keeps it in `res/values/strings.xml`. Colours come from the shared
  brand palette (`android/.../ui/theme/Theme.kt` = iOS system colours; no dynamic colour).
- Native, not shared UI: follow each platform's conventions (Material 3 / HIG) where users expect them.

## iOS conventions
- `ios/project.yml` (XcodeGen) is the source of truth. Never commit `GymRat.xcodeproj`.
- Put code that doesn't need UIKit/SwiftUI (networking, models, domain logic) in
  `Packages/GymRatKit`, where it is testable on Linux. The app target holds views,
  view models and app wiring.
- One folder per feature under `GymRat/Features/<Name>/` (View + `@Observable` ViewModel).
- Keep Kit models in sync with `backend/schemas/`. The contract tests catch drift.
- Build configuration goes in `Config/*.xcconfig`, reaches `Info.plist`, and is read by `AppConfiguration`.

## Android conventions
- Single Gradle build in `android/` with a version catalog (`gradle/libs.versions.toml`). AGP 9
  has built-in Kotlin, so don't add the `org.jetbrains.kotlin.android` plugin.
- `:core` is pure Kotlin/JVM (no Android APIs): networking, models, domain logic. It is testable
  anywhere and contract-tested against the backend. `:app` holds Compose screens, ViewModels
  (`StateFlow`) and wiring.
- One folder per feature under `app/src/main/kotlin/com/nextlevel/gymrat/features/<name>/`.
  Split each screen into a stateful `<Name>Screen(model)` and a stateless `<Name>Content(state, …)`.
  The stateless one is what previews and Robolectric tests render.
- Build configuration lives in `app/build.gradle.kts` (`buildConfigField` per build type) and is
  read by `AppConfiguration`. The debug network config allows cleartext only to the local backend.
- Keep Kotlin models in sync with `backend/schemas/` and with GymRatKit. The contract tests catch drift.

## Commands
- Backend: `cd backend && pytest tests/ --cov`, `ruff check .`
- iOS: `cd ios && make test`, `make kit-test`, `make lint`
- Android: `cd android && ./gradlew spotlessApply :app:lintDebug :core:test :app:testDebugUnitTest`
  (UI on emulator: `:app:connectedDebugAndroidTest`)
- Backend over HTTP for the Simulator and contract tests:
  `cd backend && docker compose -f docker-compose.yml -f docker-compose.http.yml up -d --build --wait`

## CI
`.github/workflows/ci.yml` is the entry point and calls `_backend.yml`, `_ios.yml`, `_android.yml`
and `_contract.yml` based on which paths changed. `CI OK` is the single required status
check. Pin third-party actions to a commit SHA with a `# vX.Y.Z` comment.

## ToDo
- [ ] **MCP Host allow-list.** The `/mcp` endpoint accepts only `localhost`, `127.0.0.1` and `[::1]`
  Host headers. Anything else gets `421 Invalid Host header`, including `app` inside Docker
  Compose and any production domain. This is the MCP SDK's DNS-rebinding protection, which it
  enables automatically for a localhost bind. `backend/main.py` calls
  `mcp.streamable_http_app(streamable_http_path="/")` without `host` or `transport_security`,
  so that default applies (the same as in `mcp` 1.x). Before MCP is used from anywhere but
  localhost, add a `Settings` field listing the allowed hosts/origins (defaulting to
  localhost-only). Pass it as `transport_security=TransportSecuritySettings(...)`
  (`mcp.server.transport_security`), and test that an allowed host gets 200 and any other gets 421.
