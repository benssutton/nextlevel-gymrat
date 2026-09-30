import asyncio
import logging
import time
from typing import TYPE_CHECKING

import psutil

from core.system_metrics import collect_system_snapshot
from schemas.health import (
    AppInfo,
    CheckResult,
    DetailedStatusResponse,
    HealthStatusResponse,
    LivenessResponse,
    ProbeResult,
    ReadinessResponse,
    RequestInfo,
    UptimeInfo,
)
from services.config import ConfigService
from settings import Settings

if TYPE_CHECKING:
    from core.container import Container

log = logging.getLogger(__name__)


class HealthService:
    def __init__(self, settings: Settings, container: "Container") -> None:
        self.settings = settings
        self._container = container
        self._started_at = time.monotonic()
        self._process = psutil.Process()
        self._process.cpu_percent()        # prime process CPU delta
        psutil.cpu_percent(interval=None)  # prime host CPU delta

    # Kept for the MCP get_health_status tool (REST-mirroring, simple string).
    def status(self) -> HealthStatusResponse:
        return HealthStatusResponse(status=self.settings.status)

    def _uptime_seconds(self) -> float:
        return time.monotonic() - self._started_at

    def liveness(self) -> LivenessResponse:
        return LivenessResponse(status="alive", uptime_seconds=round(self._uptime_seconds(), 3))

    async def _probe(self, service_type: type, name: str) -> ProbeResult:
        try:
            service = self._container.get(service_type)
        except ValueError:
            return ProbeResult(name=name, status="down", latency_ms=0.0, error="initializing")
        try:
            return await asyncio.wait_for(
                service.health_check(), timeout=self.settings.health_check_timeout_seconds
            )
        except asyncio.TimeoutError:
            return ProbeResult(
                name=name, status="down",
                latency_ms=round(self.settings.health_check_timeout_seconds * 1000, 2),
                error="timeout",
            )
        except Exception as exc:
            log.error("%s health check raised: %s", name, exc)
            return ProbeResult(name=name, status="down", latency_ms=0.0, error="unavailable")

    async def _gather_dependencies(self) -> list[ProbeResult]:
        return list(await asyncio.gather(
            # Add a probe here for each new critical dependency.
            self._probe(ConfigService, "postgres"),
        ))

    async def readiness(self) -> ReadinessResponse:
        deps = await self._gather_dependencies()
        checks = [
            CheckResult(name=d.name, status=d.status, latency_ms=d.latency_ms, error=d.error)
            for d in deps
        ]
        all_up = all(d.status == "up" for d in deps)
        return ReadinessResponse(status="ready" if all_up else "not_ready", checks=checks)

    async def detailed_status(self) -> DetailedStatusResponse:
        deps = await self._gather_dependencies()
        snapshot = collect_system_snapshot(self._process)
        return DetailedStatusResponse(
            app=AppInfo(
                title=self.settings.app_title,
                version=self.settings.app_version,
                status=self.settings.status,
            ),
            uptime=UptimeInfo(
                process_seconds=round(self._uptime_seconds(), 3),
                system_boot_seconds=psutil.boot_time(),
            ),
            dependencies=deps,
            requests=RequestInfo(last_request_at=self._container.last_request_at),
            system=snapshot,
        )
