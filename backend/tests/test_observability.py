import asyncio
import logging
import time

import pytest
from httpx import AsyncClient, Response
from testcontainers.postgres import PostgresContainer

from settings import Settings
from tests.app_client import lifespan_test_client
from tests.conftest import PG_IMAGE, postgres_url

pytestmark = pytest.mark.observability


def _settings(pg, **overrides) -> Settings:
    return Settings(status="testing", postgres_url=postgres_url(pg), **overrides)


async def _poll_ready(client: AsyncClient, expected_code: int, timeout: float = 10.0) -> Response:
    """Poll /health/ready until it returns expected_code or the timeout elapses;
    returns the last response either way so the caller's assertion shows it."""
    deadline = time.monotonic() + timeout
    last = await client.get("/health/ready")
    while last.status_code != expected_code and time.monotonic() < deadline:
        await asyncio.sleep(0.1)
        last = await client.get("/health/ready")
    return last


async def test_liveness_always_200(postgres_container):
    async with lifespan_test_client(_settings(postgres_container)) as client:
        resp = await client.get("/health/live")
        assert resp.status_code == 200
        assert resp.json()["status"] == "alive"


async def test_readiness_all_up_returns_200(postgres_container):
    async with lifespan_test_client(_settings(postgres_container)) as client:
        resp = await _poll_ready(client, 200)
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "ready"
        by_name = {c["name"]: c for c in body["checks"]}
        assert by_name["postgres"]["status"] == "up"
        assert by_name["postgres"]["latency_ms"] >= 0.0


async def test_status_structure(postgres_container):
    async with lifespan_test_client(_settings(postgres_container)) as client:
        await _poll_ready(client, 200)
        await client.get("/health/live")   # sets last_request_at
        body = (await client.get("/health/status")).json()
        assert body["app"]["status"] == "testing"
        assert body["uptime"]["process_seconds"] >= 0.0
        assert body["system"]["process"]["memory_rss_bytes"] > 0
        assert body["system"]["host"]["memory_total_bytes"] > 0
        assert body["dependencies"][0]["name"] == "postgres"
        assert body["requests"]["last_request_at"] is not None


async def test_metrics_endpoint_exposes_series(postgres_container):
    async with lifespan_test_client(_settings(postgres_container)) as client:
        await _poll_ready(client, 200)
        await client.get("/health/live")
        resp = await client.get("/metrics")
        assert resp.status_code == 200
        text = resp.text
        assert 'dependency_up{name="postgres"} 1.0' in text
        assert "dependency_check_latency_seconds" in text
        assert "process_memory_rss_bytes" in text
        assert "system_memory_total_bytes" in text
        assert "http_requests_total" in text      # prometheus-fastapi-instrumentator
        assert "app_info" in text


async def test_metrics_disabled_omits_endpoint(postgres_container):
    async with lifespan_test_client(_settings(postgres_container, metrics_enabled=False)) as client:
        assert (await client.get("/metrics")).status_code == 404


async def test_postgres_down_fails_readiness_with_generic_error():
    # A dedicated Postgres (not the shared session container) so it can be
    # killed mid-test. The probe must flip to down with a generic error token —
    # never the raw exception text (host/port/errno). Started and stopped
    # explicitly (not `with`): the test stops it mid-way, and the context
    # manager's exit would then fail removing a container that is already gone.
    dedicated = PostgresContainer(PG_IMAGE)
    dedicated.start()
    try:
        settings = _settings(dedicated, health_check_timeout_seconds=1.0)
        async with lifespan_test_client(settings) as client:
            assert (await _poll_ready(client, 200)).status_code == 200
            dedicated.stop()
            resp = await _poll_ready(client, 503, timeout=15.0)
            assert resp.status_code == 503
            body = resp.json()
            assert body["status"] == "not_ready"
            pg_check = {c["name"]: c for c in body["checks"]}["postgres"]
            assert pg_check["status"] == "down"
            # Either ConfigService catches the error ("unavailable") or the
            # probe's wait_for trips first ("timeout"); both are generic.
            assert pg_check["error"] in {"unavailable", "timeout"}

            metrics = (await client.get("/metrics")).text
            assert 'dependency_up{name="postgres"} 0.0' in metrics
    finally:
        try:
            dedicated.stop()
        except Exception:
            pass


async def test_postgres_health_check_exception_logs_and_returns_generic_error(postgres_container, caplog):
    import asyncpg
    from services.config import ConfigService

    pool = await asyncpg.create_pool(postgres_url(postgres_container).get_secret_value(), min_size=0, max_size=1)
    await pool.close()   # closed pool → acquire() raises InterfaceError
    svc = ConfigService(pool)
    with caplog.at_level(logging.ERROR, logger="services.config"):
        result = await svc.health_check()

    assert result.status == "down"
    assert result.error == "unavailable"
    assert "postgres health check failed" in caplog.text


async def test_config_read_logs_postgres_timing_with_correlation_id(postgres_container, cid_caplog):
    with cid_caplog.at_level(logging.DEBUG, logger="core.correlation"):
        async with lifespan_test_client(_settings(postgres_container)) as client:
            resp = await client.get("/config/", headers={"X-Request-ID": "cid-timing-1"})
            assert resp.status_code == 200
    timing = [r for r in cid_caplog.records if "postgres.config.get_all" in r.getMessage()]
    assert timing
    assert all(r.correlation_id == "cid-timing-1" for r in timing)
