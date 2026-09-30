# GymRat Backend

A **FastAPI** service for the GymRat iOS app, backed by **Postgres**, with
Prometheus metrics and a Grafana dashboard. It started as a copy of the
python-webservice-template and was slimmed down: ClickHouse, Redis, Solace,
Arrow Flight and the LSM stream store were removed. The template's patterns for
testability, observability and resilience remain, ready for GymRat's domain logic.

This document is a **map of the features**. Each section says what the feature
is, why it's here, and where it lives in the code, so you can jump straight to it.

- New to the repo and want to run it? → [GETTING_STARTED.md](GETTING_STARTED.md)
- Want the architectural rationale and patterns in depth? → [CLAUDE.md](CLAUDE.md)

---

## Table of Contents

1. [Core Framework — FastAPI, Pydantic, REST-first, MCP](#1-core-framework)
2. [Backing Technologies](#2-backing-technologies)
3. [Observability](#3-observability)
4. [Resilience](#4-resilience)
5. [Testing](#5-testing)
6. [Performance & Profiling](#6-performance--profiling)
7. [Configuration](#7-configuration)
8. [Project Layout](#8-project-layout)

---

## 1. Core Framework

The heart of the service is the **application factory** in
[main.py](main.py). `create_app(settings)` builds a
*fully isolated* app — its own DI container, its own MCP server, its own
lifespan — so several apps can run side-by-side in one process (this is what lets
the whole test suite run in a single pytest session).

| Feature | What it does | Entry point |
| --- | --- | --- |
| **FastAPI app factory** | Builds an isolated app; lifespan eagerly opens & smoke-tests every dependency before serving traffic | [`create_app` / `create_lifespan`](main.py) |
| **Pydantic settings** | All config is a typed `BaseSettings` model; env vars / `.env` override defaults; secrets use `SecretStr` | [settings.py](settings.py) |
| **Dependency injection** | A small custom `Container` holds singletons per app; route params resolve them via typed `Annotated` aliases | [core/container.py](core/container.py), [core/dependencies.py](core/dependencies.py) |
| **REST-first routers** | Every capability — config, health, metrics — is a REST endpoint. Routers are thin; logic lives in services | [routers/](routers/) |
| **Pydantic schemas** | Request/response models give automatic validation + OpenAPI docs | [schemas/](schemas/) |
| **OpenAPI / Swagger** | Tag metadata is co-located with each router and assembled in the factory; docs served at `/docs` | [`openapi_tags`](main.py) |
| **MCP server** | An `MCPServer` (MCP Python SDK 2.x) is mounted at `/mcp` as a **starting point** for exposing capabilities to AI agents. One example tool (`get_health_status`) mirrors a REST endpoint by calling the same service; `resources.py` / `prompts.py` are stubs to fill in. MCPs are optional and add no new logic | [mcp_routers/tools.py](mcp_routers/tools.py), [`app.mount("/mcp")`](main.py) |

> **Design note — REST-first, MCP as a starting point.** Application management,
> configuration, observability and functional calls are *all* REST endpoints.
> The MCP layer is a thin adapter intended to let an AI agent call the same
> capabilities by delegating to the services; it never holds business logic of
> its own. It ships as a worked example (one tool) to extend, not a complete
> mirror of every endpoint.

---

## 2. Backing Technologies

Each external dependency is wrapped in an **async context manager** that opens
the connection in `__aenter__` and closes it in `__aexit__`. The lifespan in
[main.py](main.py) enters them in order, so a
failed dependency aborts startup (fail-fast) and a clean/exception shutdown
always closes them.

| Technology | Role | Client (entry point) | Service |
| --- | --- | --- | --- |
| **Postgres** (asyncpg, no ORM) | Transactional config store | [postgres_client.py](persistence/transaction_store/postgres/postgres_client.py) | [services/config.py](services/config.py) |

SQL is kept as a plain, idempotent DDL file that the lifespan runs at startup:
[scripts/postgres-init.sql](scripts/postgres-init.sql). To add another backing
store, follow *Adding a dependency* in [CLAUDE.md](CLAUDE.md).

---

## 3. Observability

The application exposes the data points needed for monitoring and debugging.

| Feature | What it gives you | Entry point |
| --- | --- | --- |
| **Liveness / readiness / status** | `/health/live` (always 200), `/health/ready` (503 if any dependency down), `/health/status` (full detail incl. system metrics) | [routers/health.py](routers/health.py), [services/health.py](services/health.py) |
| **Prometheus metrics** | `/metrics` exposes dependency up/latency, process & host gauges, request histograms | [routers/metrics.py](routers/metrics.py), [services/metrics.py](services/metrics.py) |
| **Correlation middleware** | Adopts or generates an `X-Request-ID`, stamps every log line via a logging filter, echoes it back. Propagates across `to_thread` into worker threads | [core/correlation.py](core/correlation.py) |
| **Server-Timing header** | Each response carries a W3C `Server-Timing` header built from `timed()` boundary samples — per-request latency attribution with no external tooling | [core/boundary_timing.py](core/boundary_timing.py), [`timed()`](core/correlation.py) |
| **System metrics** | Process & host CPU / memory / threads / open files via psutil | [core/system_metrics.py](core/system_metrics.py) |
| **Grafana + Prometheus stack** | `docker compose --profile observability up` brings up Prometheus (`:9090`) scraping `/metrics` and a pre-provisioned Grafana dashboard (`:3000`) | [observability/](observability/), [docker-compose.yml](docker-compose.yml) |

---

## 4. Resilience

Enterprise-grade stability through fail-fast startup and self-recovery.

| Measure | Behaviour | Entry point |
| --- | --- | --- |
| **HTTPS / TLS** | Uvicorn serves over TLS using a key/cert pair; a script generates a self-signed pair for local dev | [`ssl_keyfile`/`ssl_certfile`](settings.py), [certs/generate_self_signed_cert.py](certs/generate_self_signed_cert.py) |
| **Fail-fast startup** | Critical dependencies are eagerly connected and smoke-tested in the lifespan; if one cannot be reached after retries, startup aborts and the process exits | [main.py lifespan](main.py) |
| **Reconnect with randomised backoff** | Startup connections retry with exponential delay **+ up to 25% jitter**, capped, for `connect_max_attempts` before giving up | [`connect_with_backoff`](core/retry.py) |
| **Max-payload middleware** | Outermost middleware rejects oversized HTTP bodies with **413** (fast-path on `Content-Length`, byte-counting for chunked uploads) — before any handler runs | [core/request_limits.py](core/request_limits.py), [wiring](main.py) |
| **Secret hygiene** | All credentials are `SecretStr` — never printed in `repr`/logs; health probes return generic `"unavailable"` tokens, never raw exception strings | [settings.py](settings.py), [`_probe`](services/health.py) |

---

## 5. Testing

Faithful, comprehensive tests are the core guardrail of this template. The suite
targets **>90% coverage** and follows a few firm rules.

- **Real dependencies, not mocks.** Tests spin up a **real** Postgres container
  via `testcontainers`; assertions go through the actual client. Setup: [tests/conftest.py](tests/conftest.py).
- **Real HTTP endpoints.** Tests drive the app through an async HTTPX client
  against a real ASGI app, not by calling functions directly:
  [tests/app_client.py](tests/app_client.py)
  (`lifespan_test_client` runs the full lifespan, fails fast if startup raises).
- **Async & parallel.** Tests are `async` and run concurrently to keep the cycle
  short ([pytest.ini](pytest.ini)).
- **Multiple application instances in one process.** Because every app is built
  by `create_app`, fixtures with different `Settings` each get their own
  isolated app — "run all tests" works from the IDE. See the multi-app
  observability tests in
  [tests/test_observability.py](tests/test_observability.py).
- **Failure-path coverage.** Resilience is tested by killing dedicated
  containers mid-test and asserting readiness flips to 503 with a *generic* error
  (e.g. `test_postgres_down_fails_readiness_with_generic_error` in
  [tests/test_observability.py](tests/test_observability.py)).
- **Configured by `Settings`, not monkeypatching.** Test behaviour is set by the
  `Settings` passed to `create_app` — no patching, no DI overrides.

Run them:

```bash
pytest tests/ -v --cov --cov-report=html   # report at htmlcov/index.html
```

---

## 6. Performance & Profiling

- **k6 load tests** live in
  [tests/performance/](tests/performance/) and double
  as CI quality gates:
  - `smoke.js` — 1 VU / 30 s, hard gate
  - `load.js` — ramping + constant VUs, hard gate
  - `stress.js` — ramping arrival rate, soft gate
  - Shared check helpers and named SLO presets in
    [lib/](tests/performance/lib/).
- **Two-layer bottleneck attribution** (see the
  [runbook](PERF_PROFILING_RUNBOOK.md)):
  - *Layer 1 (always on, report-only):* the `Server-Timing` header is parsed by
    [profile_reads.js](tests/performance/profile_reads.js)
    into a ranked per-endpoint attribution table.
  - *Layer 2 (on demand):* attach **py-spy** to the running container for
    flamegraphs that separate CPU/GIL- from I/O-bound time —
    [profile/run_pyspy.sh](tests/performance/profile/run_pyspy.sh)
    + [docker-compose.profiling.yml](docker-compose.profiling.yml).

---

## 7. Configuration

Everything is configured through [settings.py](settings.py)
(Pydantic `BaseSettings`). Defaults are sane for local dev; override any field
with an environment variable or a `.env` file. Notable groups:

- **Connections:** `postgres_*`
- **TLS:** `ssl_keyfile`, `ssl_certfile`
- **Retry/backoff:** `connect_max_attempts`, `connect_base_delay`, `connect_max_delay`
- **Observability:** `metrics_enabled`, `health_check_timeout_seconds`
- **Size limits:** `max_request_body_bytes`
- **CORS:** `cors_allow_origins` / `…_methods` / `…_headers` / `…_credentials`
  (a validator forbids the unsafe `credentials=True` + `origins=["*"]` combo)

---

## 8. Project Layout

```
main.py                  App factory + lifespan (dependency wiring, fail-fast startup)
settings.py              Typed configuration (Pydantic BaseSettings)
core/                    Cross-cutting infra: DI container, correlation, retry,
                         request-size + server-timing middleware, system metrics
routers/                 REST endpoints (thin) — health, config, metrics
mcp_routers/             MCP starting point — one example tool; resources/prompts are stubs
schemas/                 Pydantic request/response models
services/                Business logic — health, config, metrics
persistence/             One package per backing store (postgres)
scripts/                 SQL DDL (run by the app lifespan at startup)
observability/           Prometheus config + Grafana dashboards & provisioning
certs/                   Self-signed TLS cert + generator
tests/                   Pytest integration tests; performance/ holds k6 scripts
docs/                    Design specs and plans
PERF_PROFILING_RUNBOOK.md  Two-layer performance profiling guide (Server-Timing + py-spy)
```

---

## Where to Start Reading

If you have ten minutes, read these four files in order — they show the whole
spine of the service:

1. [settings.py](settings.py) — what's configurable.
2. [main.py](main.py) — how everything is wired and
   started.
3. [services/health.py](services/health.py) — how the
   service reports on itself.
4. [tests/test_observability.py](tests/test_observability.py)
   — how the behaviour above is proven with real dependencies.
