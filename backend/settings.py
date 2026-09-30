from functools import lru_cache

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_title: str = "NextLevel GymRat API"
    app_version: str = "1.0.0"
    app_description: str = "Backend service for the NextLevel GymRat iOS app"

    status: str = "running"

    server_host: str = "0.0.0.0"
    server_port: int = 443
    ssl_keyfile: str = "./certs/key.pem"
    ssl_certfile: str = "./certs/cert.pem"

    mcp_name: str = "gymrat-backend"
    mcp_instructions: str = "Tools for this template application."

    postgres_url: SecretStr = SecretStr("postgresql://appuser:password@localhost:5432/appdb")
    postgres_pool_min_size: int = 2
    postgres_pool_max_size: int = 10

    # Connection retry/backoff (Postgres startup)
    connect_max_attempts: int = Field(default=5, ge=1)
    connect_base_delay: float = Field(default=1.0, ge=0)
    connect_max_delay: float = Field(default=30.0, ge=0)

    # Observability
    metrics_enabled: bool = True
    health_check_timeout_seconds: float = 2.0                 # per-dependency ping timeout

    # Inbound size limits
    max_request_body_bytes: int = 16 * 1024 * 1024     # 16 MiB; over-limit HTTP body -> 413

    # Correlation ID header name (inbound adoption + response echo).
    correlation_id_header: str = "X-Request-ID"

    # CORS — permissive by default for local UI dev; tighten per deployment.
    cors_allow_origins: list[str] = ["*"]
    cors_allow_methods: list[str] = ["*"]
    cors_allow_headers: list[str] = ["*"]
    cors_allow_credentials: bool = False     # must stay False while origins == ["*"]

    @model_validator(mode="after")
    def _cors_credentials_check(self) -> "Settings":
        if self.cors_allow_credentials and "*" in self.cors_allow_origins:
            raise ValueError(
                "cors_allow_credentials=True is incompatible with cors_allow_origins=['*']. "
                "Specify explicit origins when enabling credentials."
            )
        return self


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
