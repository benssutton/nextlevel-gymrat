from typing import Annotated

from fastapi import Depends, Request

from core.container import Container
from services.config import ConfigService
from services.health import HealthService
from services.metrics import MetricsService


def get_container(request: Request) -> Container:
    # Resolved per-request from the owning app, so each app (including
    # isolated test apps in the same process) sees only its own services.
    return request.app.state.container


ContainerDep = Annotated[Container, Depends(get_container)]


def get_health_service(container: ContainerDep) -> HealthService:
    return container.get(HealthService)


def get_config_service(container: ContainerDep) -> ConfigService:
    return container.get(ConfigService)


def get_metrics_service(container: ContainerDep) -> MetricsService:
    return container.get(MetricsService)


HealthServiceDep = Annotated[HealthService, Depends(get_health_service)]
ConfigServiceDep = Annotated[ConfigService, Depends(get_config_service)]
MetricsServiceDep = Annotated[MetricsService, Depends(get_metrics_service)]
