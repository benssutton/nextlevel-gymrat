"""Request correlation identity, propagated via a ContextVar.

A single ID is carried for the lifetime of an HTTP request (set by
CorrelationIdMiddleware). The logging filter stamps every record with the
current value so one grep of the ID surfaces the full causal trail.
asyncio.to_thread copies the context, so work offloaded to a thread keeps the
request's ID automatically. Background work outside a request can set its own
ID with set_correlation_id().
"""
import contextvars
import logging
import time
import uuid
from contextlib import asynccontextmanager

from core.boundary_timing import record_boundary
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

correlation_id_var: contextvars.ContextVar[str] = contextvars.ContextVar(
    "correlation_id", default="-"
)


def get_correlation_id() -> str:
    return correlation_id_var.get()


def set_correlation_id(value: str) -> contextvars.Token:
    """Set the correlation ID and return the reset token.

    Callers that need to restore the previous value should pair this with
    `correlation_id_var.reset(token)` when done — e.g. a background loop that
    resets after each unit of work so later logs are not mis-attributed to
    the previous unit's ID. Fire-and-forget callers that never need
    to restore the prior value may discard the returned token.
    """
    return correlation_id_var.set(value)


def new_id() -> str:
    return uuid.uuid4().hex


class CorrelationIdFilter(logging.Filter):
    """Injects the current correlation ID onto every LogRecord."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.correlation_id = correlation_id_var.get()
        return True


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, header_name: str = "X-Request-ID") -> None:
        super().__init__(app)
        self._header = header_name

    async def dispatch(self, request: Request, call_next):
        # Set BEFORE call_next so the value is visible to the downstream endpoint
        # and its loggers (the reliable direction for BaseHTTPMiddleware + contextvars).
        incoming = request.headers.get(self._header)
        cid = incoming or new_id()
        token = correlation_id_var.set(cid)
        try:
            response = await call_next(request)
        finally:
            correlation_id_var.reset(token)
        response.headers[self._header] = cid
        return response


_timing_log = logging.getLogger(__name__)


@asynccontextmanager
async def timed(label: str):
    """Log the wall-clock duration of an awaited boundary, tagged with the
    current correlation ID (added by CorrelationIdFilter), and record it as a
    Server-Timing sample for the current request (added by record_boundary).
    Structured as a context manager so an OpenTelemetry span could wrap the same
    boundary later without changing call sites."""
    start = time.perf_counter()
    try:
        yield
    finally:
        elapsed_ms = (time.perf_counter() - start) * 1000
        _timing_log.debug("%s %.2fms", label, elapsed_ms)
        record_boundary(label, elapsed_ms)
