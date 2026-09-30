# NextLevel GymRat

Monorepo for the GymRat iOS and Android apps and their shared backend. The two apps are
native (SwiftUI / Jetpack Compose) and kept at feature parity: see
[docs/FEATURE_PARITY.md](docs/FEATURE_PARITY.md).

```
ios/                 SwiftUI app (iOS 18+), generated with XcodeGen
  GymRat/            App target: App/ (entry point, config), Features/ (one folder per screen)
  GymRatTests/       Unit tests (Swift Testing), hosted in the app
  GymRatUITests/     UI tests (XCTest)
  Packages/GymRatKit Swift package: API client + models. No UIKit, so it also builds and tests on Linux
  Config/            xcconfig per build configuration (bundle ID, API base URL)
  project.yml        XcodeGen spec. The .xcodeproj is generated and not committed
android/             Jetpack Compose app (Android 8.0+ / API 26), mirrors ios/ screen for screen
  app/               App module: MainActivity, AppConfiguration, features/ (one folder per screen), ui/theme
  core/              Pure Kotlin/JVM module: API client + models (the equivalent of GymRatKit)
  gradle/libs.versions.toml  Version catalog (AGP, Kotlin, Compose BOM, …)
backend/             FastAPI service, copied from the python-webservice-template
.github/             CI/CD workflows, Dependabot, CODEOWNERS, PR template
docs/CICD.md         What the pipeline runs, plan limits, release setup
docs/FEATURE_PARITY.md  iOS ↔ Android parity rules and feature matrix
```

## Quick start

### Backend

Requires Docker and Python 3.12+. See [backend/GETTING_STARTED.md](backend/GETTING_STARTED.md) for full details.

```bash
cd backend
# Full stack, app over plain HTTP on :8000 (what the iOS Simulator talks to)
docker compose -f docker-compose.yml -f docker-compose.http.yml up -d --build --wait
curl http://localhost:8000/health/ready

# Tests use a real Postgres via testcontainers
pip install -r requirements.txt -r requirements-dev.txt
python certs/generate_self_signed_cert.py
pytest tests/ --cov
```

### iOS

Requires Xcode 26+ and Homebrew.

```bash
cd ios
make bootstrap   # installs XcodeGen + SwiftLint
make open        # generates GymRat.xcodeproj and opens it
make test        # unit + UI tests on the newest iPhone simulator
make kit-test    # GymRatKit package tests
GYMRAT_API_BASE_URL=http://localhost:8000 make kit-test   # also runs the contract tests against the live backend
```

Debug builds call `http://localhost:8000` and Release builds call the URL in
`ios/Config/Release.xcconfig`. To override settings locally (e.g. your Team ID),
create an untracked `ios/Config/Local.xcconfig`.

Before your first device or TestFlight build:
- set `APP_BUNDLE_IDENTIFIER` in `ios/Config/Shared.xcconfig`
- add a 1024×1024 image to `ios/GymRat/Resources/Assets.xcassets/AppIcon.appiconset`
- set the production `API_BASE_URL`

### Android

Requires Android Studio (or JDK 21 + the Android SDK). Open the `android/` folder in Android Studio, or:

```bash
cd android
./gradlew :app:installDebug              # build and install on a running emulator/device
./gradlew :core:test :app:testDebugUnitTest   # unit + Robolectric UI tests (no emulator)
./gradlew :app:connectedDebugAndroidTest # UI tests on a running emulator
./gradlew spotlessApply :app:lintDebug   # format (ktlint) + Android Lint
GYMRAT_API_BASE_URL=http://localhost:8000 ./gradlew :core:test   # also runs the contract tests against the live backend
```

Debug builds call `http://10.0.2.2:8000` (the host machine as seen from the emulator; start the
backend with the HTTP overlay shown above). Release builds call `https://api.example.com`. Override
either with `-Pgymrat.apiBaseUrl=...` (e.g. in `~/.gradle/gradle.properties`).

Before your first Play upload:
- set `applicationId` in `android/app/build.gradle.kts`
- replace the placeholder launcher icon in `android/app/src/main/res/`
- set the production API URL (release `buildConfigField`)

## CI/CD

GitHub Actions runs on every PR and on every push to `main`. Only the parts of the
repo that changed are built. Details are in [docs/CICD.md](docs/CICD.md).

| Area | Gates |
|---|---|
| Repo | actionlint, gitleaks secret scan, iOS ↔ Android UI-identifier parity check |
| Backend | ruff, pyright (zero errors), pip-audit, pytest against a real Postgres (coverage ≥ 94%), image build + Grype CVE scan + SBOM, k6 smoke and load tests; image pushed to GHCR on `main` |
| iOS | SwiftLint, GymRatKit tests on Linux, app unit + UI tests on the Simulator, unsigned Release device build |
| Android | ktlint + Android Lint, core + Robolectric UI tests, debug APK + R8-minified release bundle, instrumented UI tests on an emulator (all on Linux runners) |
| Contract | Both clients (Swift GymRatKit, Kotlin `android/core`) tested against the live backend running in Docker Compose |
| Release | `ios-release.yml`: TestFlight (tag `ios/v*`). `android-release.yml`: Play internal track (tag `android/v*`) |
| Scheduled | Nightly backend run with the k6 stress test; weekly Dependabot updates |
