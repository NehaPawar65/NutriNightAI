"""Runtime settings read from environment variables (and ../.env for local development)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parents[1]


def _load_dotenv() -> None:
    """Minimal .env loader for local runs; real environment variables always win."""
    for candidate in (SERVICE_ROOT / ".env", SERVICE_ROOT.parent / ".env"):
        if not candidate.exists():
            continue
        for line in candidate.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            os.environ.setdefault(key.strip(), value.strip())
        break


@dataclass(frozen=True)
class Settings:
    api_key: str = ""
    model_dir: Path = SERVICE_ROOT / "app" / "models"
    log_level: str = "INFO"
    max_records: int = 250_000
    cors_origins: list[str] = field(default_factory=list)


def load_settings() -> Settings:
    if os.environ.get("NUTRINIGHT_SKIP_DOTENV") != "1":
        _load_dotenv()
    return Settings(
        api_key=os.environ.get("AI_SERVICE_API_KEY", ""),
        model_dir=Path(os.environ.get("MODEL_DIR", SERVICE_ROOT / "app" / "models")),
        log_level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        max_records=int(os.environ.get("MAX_RECORDS", "250000")),
    )
