import pytest
from pydantic import SecretStr
from testcontainers.postgres import PostgresContainer

from settings import Settings
from tests.app_client import lifespan_test_client

PG_IMAGE = "postgres:18"


# ── Postgres fixtures ──────────────────────────────────────────────────────

@pytest.fixture(scope="session")
def postgres_container():
    with PostgresContainer(PG_IMAGE) as pg:
        yield pg


def postgres_url(pg: PostgresContainer) -> SecretStr:
    port = int(pg.get_exposed_port(5432))
    return SecretStr(f"postgresql://{pg.username}:{pg.password}@localhost:{port}/{pg.dbname}")


# ── Async Test Client ──────────────────────────────────────────────────────
#
# Each client fixture builds its OWN isolated app via main.create_app — its
# own DI container, MCPServer instance, and lifespan — so multiple apps with
# different Settings can coexist in one pytest process without sharing state.

@pytest.fixture(scope="session")
async def test_client(postgres_container):
    settings = Settings(status="testing", postgres_url=postgres_url(postgres_container))
    async with lifespan_test_client(settings) as client:
        yield client


@pytest.fixture
def cid_caplog(caplog):
    """caplog with the production CorrelationIdFilter attached, so captured
    records expose `record.correlation_id`. Real logging, no mocking."""
    from core.correlation import CorrelationIdFilter
    caplog.handler.addFilter(CorrelationIdFilter())
    return caplog
