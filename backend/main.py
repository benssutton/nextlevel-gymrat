import logging
from contextlib import asynccontextmanager, AsyncExitStack
from datetime import datetime, timezone
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from mcp.server.mcpserver import MCPServer

from core.container import Container
from core.correlation import CorrelationIdMiddleware
from core.logging_config import configure_logging
from core.boundary_timing import ServerTimingMiddleware
from core.request_limits import MaxBodySizeMiddleware
from settings import get_settings, Settings
from persistence.transaction_store.postgres.postgres_client import PostgresClient
from routers import health, config, metrics
from mcp_routers import tools
from services.config import ConfigService
from services.metrics import MetricsService

log = logging.getLogger(__name__)

logging.getLogger("asyncio").addFilter(
    lambda r: not (r.exc_info and isinstance(r.exc_info[1], ConnectionResetError))
)

def create_lifespan(settings: Settings, mcp: MCPServer):
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        container: Container = app.state.container
        async with AsyncExitStack() as stack:
            pg_pool = await stack.enter_async_context(PostgresClient(settings))
            schema_sql = (Path(__file__).parent / "scripts" / "postgres-init.sql").read_text()
            async with pg_pool.acquire() as conn:
                await conn.execute(schema_sql)
            container.register_singleton(ConfigService, ConfigService(pg_pool))

            await stack.enter_async_context(mcp.session_manager.run())
            yield
    return lifespan


def create_app(settings: Settings) -> FastAPI:
    """Build a fully isolated application instance.

    Everything stateful — the DI container, the MCPServer (whose
    session manager can only run once per instance), and the lifespan — is
    created fresh per call, so multiple apps can coexist in one process
    (e.g. test apps with different settings running in the same pytest
    session).
    """
    configure_logging()
    container = Container(settings)

    mcp = MCPServer(
        name=settings.mcp_name,
        instructions=settings.mcp_instructions,
        version=settings.app_version,
    )
    tools.register(mcp, container)

    app = FastAPI(
        title=settings.app_title,
        version=settings.app_version,
        description=settings.app_description,
        openapi_tags=[health.TAG_METADATA, config.TAG_METADATA],
        lifespan=create_lifespan(settings, mcp),
    )
    app.state.container = container

    @app.middleware("http")
    async def _track_last_request(request, call_next):
        request.app.state.container.last_request_at = datetime.now(timezone.utc)
        return await call_next(request)

    # ServerTimingMiddleware is the first add_middleware call (innermost of this
    # stack; the _track_last_request decorator above sits just inside it) — Starlette
    # LIFO ordering — so `total` measures handler + downstream I/O time and all
    # boundaries are captured.
    app.add_middleware(ServerTimingMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_allow_origins,
        allow_methods=settings.cors_allow_methods,
        allow_headers=settings.cors_allow_headers,
        allow_credentials=settings.cors_allow_credentials,
    )
    app.add_middleware(CorrelationIdMiddleware, header_name=settings.correlation_id_header)
    # MaxBodySizeMiddleware must be LAST (outermost) — Starlette LIFO ordering.
    app.add_middleware(MaxBodySizeMiddleware, max_bytes=settings.max_request_body_bytes)

    app.include_router(health.router, prefix="/health")
    app.include_router(config.router, prefix="/config")

    if settings.metrics_enabled:
        metrics_service = MetricsService(settings)
        metrics_service.instrument(app)
        container.register_singleton(MetricsService, metrics_service)
        app.include_router(metrics.router)

    # The sub-app serves at its own root; the mount supplies the /mcp prefix.
    app.mount("/mcp", mcp.streamable_http_app(streamable_http_path="/"))

    @app.get("/", tags=["API Root Page"])
    async def get_root():
        return {
            "title": settings.app_title,
            "version": settings.app_version,
            "description": settings.app_description,
            "docs": "/docs",
            "MCP": "/mcp"
        }

    return app


# Module-level app for `uvicorn main:app`.
# All Settings fields have defaults so this is safe to execute at import time.
# If you later add a field with no default, switch to the factory pattern:
#   uvicorn main:create_app --factory
app = create_app(get_settings())


if __name__ == "__main__":
    import uvicorn
    log.info("Starting the application from main.py")
    settings = get_settings()   # same cached instance as `app` above
    uvicorn.run(
        app,
        host=settings.server_host,
        port=settings.server_port,
        ssl_keyfile=settings.ssl_keyfile,
        ssl_certfile=settings.ssl_certfile,
    )
