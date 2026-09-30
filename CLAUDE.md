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
  warnings are errors. Pyright (standard mode) for Python must report zero errors:
  fix the types rather than adding `# type: ignore`.
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
