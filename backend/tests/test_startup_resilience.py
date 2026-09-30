import time

import pytest
from pydantic import SecretStr

from settings import Settings
from tests.app_client import lifespan_test_client

pytestmark = pytest.mark.resilience


async def test_postgres_unreachable_aborts_startup_after_bounded_retries():
    # Postgres points at a closed port. With a tiny, Settings-driven backoff
    # budget the real lifespan must retry then abort — not hang, not skip the failure.
    settings = Settings(
        postgres_url=SecretStr("postgresql://user:password@127.0.0.1:1/appdb"),   # nothing is listening here
        connect_max_attempts=3,
        connect_base_delay=0.001,
        connect_max_delay=0.005,
    )
    start = time.monotonic()
    with pytest.raises(OSError):   # asyncpg surfaces the refused connection as an OSError
        async with lifespan_test_client(settings):
            pass
    assert time.monotonic() - start < 15.0  # retries ≤15ms total; 15s allows for ASGI startup overhead
