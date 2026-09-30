from datetime import datetime

from pydantic import BaseModel


class HealthStatusResponse(BaseModel):
    status: str


class ProbeResult(BaseModel):
    """Result of a single dependency health probe."""
    name: str
    status: str            # "up" | "down"
    latency_ms: float
    error: str | None = None


class CheckResult(BaseModel):
    """A single entry in the flat /health/ready checks array.

    Serialised with response_model_exclude_none, so `error` appears only on
    failed checks.
    """
    name: str
    status: str
    latency_ms: float | None = None
    error: str | None = None


class LivenessResponse(BaseModel):
    status: str = "alive"
    uptime_seconds: float


class ReadinessResponse(BaseModel):
    status: str            # "ready" | "not_ready"
    checks: list[CheckResult]


class ProcessStats(BaseModel):
    cpu_percent: float
    memory_rss_bytes: int
    num_threads: int
    open_files: int


class HostStats(BaseModel):
    cpu_percent: float
    memory_total_bytes: int
    memory_available_bytes: int
    memory_percent: float


class SystemSnapshot(BaseModel):
    process: ProcessStats
    host: HostStats


class AppInfo(BaseModel):
    title: str
    version: str
    status: str


class UptimeInfo(BaseModel):
    process_seconds: float
    system_boot_seconds: float


class RequestInfo(BaseModel):
    last_request_at: datetime | None = None


class DetailedStatusResponse(BaseModel):
    app: AppInfo
    uptime: UptimeInfo
    dependencies: list[ProbeResult]
    requests: RequestInfo
    system: SystemSnapshot
