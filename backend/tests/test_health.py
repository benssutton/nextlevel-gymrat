
async def test_liveness_returns_alive(test_client):
    response = await test_client.get("/health/live")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "alive"
    assert body["uptime_seconds"] >= 0.0


async def test_status_reports_app_and_system(test_client):
    """status is overridden to 'testing' in the test fixtures."""
    response = await test_client.get("/health/status")
    assert response.status_code == 200
    body = response.json()
    assert body["app"]["status"] == "testing"
    assert body["uptime"]["process_seconds"] >= 0.0
    assert body["system"]["process"]["memory_rss_bytes"] > 0
    assert [d["name"] for d in body["dependencies"]] == ["postgres"]
    assert body["dependencies"][0]["status"] == "up"


async def test_readiness_reports_postgres_up(test_client):
    response = await test_client.get("/health/ready")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ready"
    assert [c["name"] for c in body["checks"]] == ["postgres"]
    assert body["checks"][0]["status"] == "up"
    assert "error" not in body["checks"][0]     # exclude_none: no error key when healthy


async def test_root_returns_non_empty_json(test_client):
    response = await test_client.get("/")
    assert response.status_code == 200
    assert response.json()
