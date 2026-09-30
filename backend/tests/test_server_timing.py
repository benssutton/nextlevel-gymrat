import pytest

pytestmark = pytest.mark.observability


async def test_config_read_emits_server_timing(test_client):
    r = await test_client.get("/config/")
    assert r.status_code == 200
    server_timing = r.headers.get("Server-Timing", "")
    assert "postgres_config_get_all" in server_timing
    assert "total" in server_timing


async def test_config_write_emits_server_timing(test_client):
    r = await test_client.post("/config/", json={"key": "timing.probe", "value": "1"})
    assert r.status_code == 201
    assert "postgres_config_set" in r.headers.get("Server-Timing", "")


async def test_request_without_boundaries_has_only_total(test_client):
    # /health/live has no instrumented boundaries, so its Server-Timing must
    # carry only `total`. (The request-scoped isolation invariant itself is
    # proven rigorously in tests/test_boundary_timing.py and test_correlation.py.)
    r = await test_client.get("/health/live")
    assert r.status_code == 200
    server_timing = r.headers.get("Server-Timing", "")
    assert "total" in server_timing
    assert "postgres" not in server_timing
