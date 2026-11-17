from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")
    database_url: str = "sqlite:///./appinsight-dev.db"
    redis_url: str = "redis://localhost:6379/0"
    cors_origins: str = "http://localhost:5173"
    # 0 removes the application's cap; Google Play still decides accessible coverage.
    max_reviews_per_region: int = 0
    scraping_delay_seconds: float = 1.2

@lru_cache
def settings() -> Settings:
    return Settings()
