# GymRat Backend

The FastAPI service behind the GymRat iOS app, with MCP endpoints. It was copied from the
python-webservice-template and slimmed to Postgres only: ClickHouse, Redis, Solace, Arrow Flight
and the LSM stream store were removed.

This document describes the patterns the service follows.

## Architecture
```
main.py                         FastAPI app entry point; lifespan manages DB connections
settings.py                     Pydantic BaseSettings config; env vars override defaults
  certs/                        Self-signed SSL certificates & certificate generation script
  core/                         DI container, dependency getters, middleware (correlation ID, Server-Timing, body limit), retry
  docs/                         Folder for Claude to store specs and plans.  Not used by the application.
  mcp_routers/                  MCP tools, resources and prompts
  observability/                Prometheus scrape config + Grafana provisioning and dashboards
  persistence/
    transaction_store/
      postgres/                 Postgres via asyncpg connection pool
  routers/                      REST endpoints (health, config, metrics)
  schemas/                      Pydantic request/response models
  scripts/                      SQL DDL for Postgres (run at startup)
  services/                     All business logic (health, config, metrics)
  tests/                        Pytest integration tests + k6 performance tests
    performance/                k6 performance test scripts
      lib/                      Shared k6 check helpers, SLO threshold presets, Server-Timing parser
      profile/                  py-spy profiling script (Layer 2)
```

## Stack
- FastAPI + Pydantic
- asyncpg: Postgres transaction store (direct, no ORM)
- prometheus-fastapi-instrumentator: `/metrics`, scraped by Prometheus and charted in Grafana
- pytest + testcontainers: integration tests against a real Postgres container
- k6: performance tests (smoke, load, stress)
- Docker Compose: full local stack (Postgres, app, plus Prometheus and Grafana with `--profile observability`)
- GitHub Actions: lint, type-check, pytest, image scan and k6 as quality gates (see ../.github/workflows/_backend.yml)

## Key Patterns

**App Factory & Dependency Injection**
- `main.create_app(settings)` builds a fully isolated app with its own `Container`, `MCPServer` instance and lifespan. The only app-scoped object at module level is `app = create_app(get_settings())`, for uvicorn.
- This isolation is load-bearing. The MCP session manager can only `run()` once per instance, so any code path that needs a second app (e.g. a test with different `Settings`) must call `create_app` again. Never re-run a lifespan against an existing app.
- The custom `Container` in `core/container.py` holds one set of singletons per app, stored on `app.state.container`. Getters in `core/dependencies.py` resolve it from `request.app.state` and provide `Annotated` type aliases for routes.
- MCP tools run outside FastAPI's request DI, so `mcp_routers.tools.register(mcp, container)` receives the container explicitly and captures it in tool closures.
- Register singletons that depend on external connections (e.g. `ConfigService`) in the lifespan once the connection is established, not when the container is created.

**Adding a dependency** (e.g. a cache or a second store)
1. Add a client in `persistence/<kind>/<tech>/` as an async context manager that connects via `core.retry.connect_with_backoff` and smoke-tests the connection.
2. Enter it on the lifespan's `AsyncExitStack` in `main.py` and register the service that wraps it.
3. Give that service an async `health_check() -> ProbeResult`, and add a `_probe(...)` line to `HealthService._gather_dependencies`. It then appears in `/health/ready`, `/health/status`, the `dependency_up` metric and the Grafana dashboard automatically.
4. Add a testcontainers fixture in `tests/conftest.py`.

**Async**
- All I/O is async. Synchronous blocking calls (e.g. `Path.read_text()`) are acceptable outside of async context managers.

**Config**
- `settings.py` uses Pydantic `BaseSettings`: env vars override defaults and `.env` is auto-loaded.
- Declare constrained fields with `Field(default=..., ...)` rather than a positional default. Otherwise Pyright reports every `Settings(...)` call as missing arguments.

**Persistence -- Postgres**
- `PostgresClient` in `persistence/transaction_store/postgres/postgres_client.py` is an async context manager. `__aenter__` returns a live `asyncpg.Pool` and `__aexit__` closes it.
- In `main.py` the lifespan enters `PostgresClient(settings)` on its `AsyncExitStack`, runs the schema DDL, then registers `ConfigService` as a singleton holding the pool.
- The schema is in `scripts/postgres-init.sql` (DDL only, `CREATE TABLE IF NOT EXISTS`). The lifespan runs it at startup, so it is idempotent and needs no migration tooling.
- Services acquire a connection per operation with `async with pool.acquire() as conn:`. Sessions are never injected into routes.

**Routers**
- Routers implement minimal business logic and call service methods.
- Each router file exports `TAG` and `TAG_METADATA` constants (name + description). `main.py` assembles `openapi_tags` from these exports, so tag metadata stays in the router that owns it, not in `Settings`.

**MCP**
- MCP tools, resources and prompts implement minimal logic and call service methods.

**Resilience**
- Critical dependencies are connected eagerly in the lifespan with `connect_with_backoff` (exponential backoff with jitter, bounded by `connect_max_attempts`). If one cannot be reached, startup aborts and the process exits (fail fast).

**Inbound Size Policy**
- The HTTP body limit (`max_request_body_bytes`, default 16 MiB) is enforced by `core/request_limits.MaxBodySizeMiddleware`, the outermost middleware. It returns 413 before the route handler runs, or after draining the body for chunked uploads without `Content-Length`.

**Security Posture**
- Credential-bearing settings (`postgres_url`) use Pydantic `SecretStr`, so their values never appear in `repr()`, `str()` or log output. Call `.get_secret_value()` only at the call site that needs the raw string. New credentials must be `SecretStr` too (`tests/test_secrets.py`).
- The health-probe endpoints (`/health/ready`, `/health/status`) return a generic `error: "unavailable"` (or `"timeout"`) for failed dependency checks, never the raw exception string. Full detail is logged server-side at ERROR level.
- CORS defaults to `cors_allow_origins=["*"]`, which suits local development. Tighten it per deployment by listing specific origins. A `@model_validator` rejects `cors_allow_credentials=True` when `"*"` is in the origins list.

**Testing**
- Tests invoke REST endpoints via an async HTTPX test client.
- Each client fixture builds its own isolated app with `tests/app_client.py::lifespan_test_client(settings)`. The helper calls `main.create_app(settings)`, runs the lifespan in a dedicated task (anyio cancel scopes must enter and exit in the same task), and fails fast if startup raises instead of hanging on a readiness event.
- Test behaviour is configured through the `Settings` passed to `create_app` (e.g. `status="testing"`). No monkeypatching or dependency overrides are needed.
- Each test session starts a fresh Postgres testcontainer (`postgres_container` fixture), which is torn down at session end. `tests/conftest.py::postgres_url()` builds its DSN.
- A test that must kill a dependency mid-test starts its own dedicated container (see `test_postgres_down_fails_readiness_with_generic_error`), so the shared session container is never disturbed.

**Observability**
- `/health/live` (process up), `/health/ready` (503 unless every dependency probe is up), `/health/status` (app, uptime, dependencies, requests, process and host stats).
- `/metrics` exposes the Prometheus HTTP metrics plus the custom gauges `dependency_up{name}`, `dependency_check_latency_seconds{name}`, `process_*` and `system_*`, refreshed on each scrape.
- `docker compose --profile observability up` adds Prometheus (:9090) and Grafana (:3000). The *Service Overview* dashboard is provisioned from `observability/grafana/`.

**Performance Tests**
- `tests/performance/lib/checks.js`: shared k6 check helpers (`checkStatus200`, `checkConfigList`).
- `tests/performance/lib/thresholds.js`: named SLO presets (`STRICT_SLO`, `NORMAL_SLO`, `RELAXED_SLO`) spread into `options.thresholds`.
- Three scripts: `smoke.js` (1 VU for 30 s, hard CI gate), `load.js` (ramping-vus + constant-vus, hard gate) and `stress.js` (ramping-arrival-rate, soft gate).
- k6 runs from an image built from `tests/performance/Dockerfile`, which avoids docker:dind volume-mount issues.
- The docker-compose project is named `gymrat-backend`, so the network is always `gymrat-backend_default`.

**Performance Profiling**
- Two-layer bottleneck triage; see `PERF_PROFILING_RUNBOOK.md`.
- Layer 1 (every run, report-only): `core/boundary_timing.py` emits a W3C `Server-Timing` response header built from `timed()` boundary samples held in a request-scoped `ContextVar`. `tests/performance/profile_reads.js` parses it into a ranked per-endpoint attribution table and `attribution.json`.
- Layer 2 (on demand): `tests/performance/profile/run_pyspy.sh` attaches py-spy to the app container and emits flamegraphs that show whether contention is CPU/GIL-bound or I/O-bound. It needs `docker-compose.profiling.yml` for the `SYS_PTRACE` capability.
- Boundary samples are request-scoped, so background work never pollutes a request's header and isolated test apps cannot collide.

**SQL Management**
- `scripts/postgres-init.sql`: DDL only (`CREATE TABLE IF NOT EXISTS`). The app lifespan runs it at startup.

## Database Investigation

When investigating a Postgres-related issue, always start a fresh container via `testcontainers` by running the relevant pytest test:

```bash
pytest tests/test_config.py -v -s
```

Never connect to a container a developer may have running locally, and never assume an existing container is safe to query or modify. Don't reuse containers between investigations: each pytest session starts a clean, isolated container that is destroyed when the session ends.
