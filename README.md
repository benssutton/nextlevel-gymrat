# NextLevel GymRat

Monorepo for the GymRat iOS app and its backend.

```
ios/                 SwiftUI app (iOS 18+), generated with XcodeGen
  GymRat/            App target: App/ (entry point, config), Features/ (one folder per screen)
  GymRatTests/       Unit tests (Swift Testing), hosted in the app
  GymRatUITests/     UI tests (XCTest)
  Packages/GymRatKit Swift package: API client + models. No UIKit, so it also builds and tests on Linux
  Config/            xcconfig per build configuration (bundle ID, API base URL)
  project.yml        XcodeGen spec. The .xcodeproj is generated and not committed
backend/             FastAPI service, copied from the python-webservice-template
.github/             CI/CD workflows, Dependabot, CODEOWNERS, PR template
docs/CICD.md         What the pipeline runs, plan limits, release setup
```

## Quick start

### Backend

Requires Docker and Python 3.12+. See [backend/GETTING_STARTED.md](backend/GETTING_STARTED.md) for full details.

```bash
cd backend
# Full stack, app over plain HTTP on :8000 (what the iOS Simulator talks to)
docker compose -f docker-compose.yml -f docker-compose.http.yml up -d --build --wait
curl http://localhost:8000/health/ready

# Tests use real Postgres/ClickHouse/Redis via testcontainers
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

## CI/CD

GitHub Actions runs on every PR and on every push to `main`. Only the parts of the
repo that changed are built. Details are in [docs/CICD.md](docs/CICD.md).

| Area | Gates |
|---|---|
| Repo | actionlint, gitleaks secret scan |
| Backend | ruff, pyright ratchet, pip-audit, pytest against real containers (coverage ≥ 94%), image build + Grype CVE scan + SBOM, k6 smoke and load tests; image pushed to GHCR on `main` |
| iOS | SwiftLint, GymRatKit tests on Linux, app unit + UI tests on the Simulator, unsigned Release device build |
| Contract | GymRatKit's Swift client tests against the live backend running in Docker Compose |
| Release | `ios-release.yml`: archive, sign and upload to TestFlight (tag `ios/v*` or manual) |
| Scheduled | Nightly backend run with the k6 stress test; weekly Dependabot updates |
