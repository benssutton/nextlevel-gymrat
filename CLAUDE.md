# NextLevel GymRat

Monorepo: `ios/` (SwiftUI app + `GymRatKit` Swift package) and `backend/` (FastAPI).
`backend/CLAUDE.md` covers the backend architecture. It was copied from the
python-webservice-template and its patterns apply unchanged.

## Principles (inherited from the templates repo)
- Test against real services, not mocks. Backend tests use testcontainers. The
  iOS API client is contract-tested against the real backend in CI (`_contract.yml`).
  A hand-written stand-in (`HealthChecking` conformer) is acceptable only where a
  live backend is not available (macOS runners have no Docker). When you use one, say why.
- Leverage the compiler: Swift 6 language mode with strict concurrency, and Swift
  warnings are errors. Pyright for Python, ratcheted by `backend/.pyright-baseline`.
  Lower the baseline number whenever you fix Pyright errors.
- All backend functionality is exposed over REST. The app talks to it only through `GymRatKit.APIClient`.

## iOS conventions
- `ios/project.yml` (XcodeGen) is the source of truth. Never commit `GymRat.xcodeproj`.
- Put code that doesn't need UIKit/SwiftUI (networking, models, domain logic) in
  `Packages/GymRatKit`, where it is testable on Linux. The app target holds views,
  view models and app wiring.
- One folder per feature under `GymRat/Features/<Name>/` (View + `@Observable` ViewModel).
- Keep Kit models in sync with `backend/schemas/`. The contract tests catch drift.
- Build configuration goes in `Config/*.xcconfig`, reaches `Info.plist`, and is read by `AppConfiguration`.

## Commands
- Backend: `cd backend && pytest tests/ --cov`, `ruff check .`
- iOS: `cd ios && make test`, `make kit-test`, `make lint`
- Backend over HTTP for the Simulator and contract tests:
  `cd backend && docker compose -f docker-compose.yml -f docker-compose.http.yml up -d --build --wait`

## CI
`.github/workflows/ci.yml` is the entry point and calls `_backend.yml`, `_ios.yml` and
`_contract.yml` based on which paths changed. `CI OK` is the single required status
check. Pin third-party actions to a commit SHA with a `# vX.Y.Z` comment.
