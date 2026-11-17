from datetime import datetime
from typing import Literal
from pydantic import BaseModel, Field, field_validator

DEFAULT_COVERAGE = [("ar", "es")]

class SearchResult(BaseModel):
    app_id: str; title: str; developer: str | None = None; icon: str | None = None
    genre: str | None = None; score: float | None = None; ratings: int | None = None; summary: str | None = None
class CreateJob(BaseModel):
    app_id: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
    period_days: Literal[10, 30] = 30
    regions: list[str] = Field(default_factory=lambda: [country for country, _ in DEFAULT_COVERAGE])
    languages: list[str] = Field(default_factory=lambda: [language for _, language in DEFAULT_COVERAGE])
    @field_validator("regions", "languages")
    @classmethod
    def valid_codes(cls, values: list[str]):
        if not values or len(values) > 8 or any(not code.isalpha() or len(code) != 2 for code in values): raise ValueError("Use up to 8 country/language ISO-2 codes")
        return [v.lower() for v in values]
class JobOut(BaseModel):
    id: str; app_id: str; status: str; requested_regions: list; completed_regions: list
    reviews_found: int; reviews_new: int; warning: str | None; error: str | None; created_at: datetime; updated_at: datetime
    model_config = {"from_attributes": True}
