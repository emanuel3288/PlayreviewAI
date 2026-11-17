import uuid
from datetime import datetime
from sqlalchemy import DateTime, Float, ForeignKey, Integer, JSON, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column
from .database import Base

def uid() -> str: return str(uuid.uuid4())
class PlayApp(Base):
    __tablename__ = "play_apps"
    app_id: Mapped[str] = mapped_column(String(255), primary_key=True)
    title: Mapped[str] = mapped_column(String(500))
    developer: Mapped[str | None] = mapped_column(String(500))
    icon: Mapped[str | None] = mapped_column(Text)
    genre: Mapped[str | None] = mapped_column(String(160))
    score: Mapped[float | None] = mapped_column(Float)
    ratings: Mapped[int | None] = mapped_column(Integer)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
class ScrapeJob(Base):
    __tablename__ = "scrape_jobs"
    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=uid)
    app_id: Mapped[str] = mapped_column(ForeignKey("play_apps.app_id"), index=True)
    status: Mapped[str] = mapped_column(String(32), default="queued", index=True)
    requested_regions: Mapped[list] = mapped_column(JSON, default=list)
    completed_regions: Mapped[list] = mapped_column(JSON, default=list)
    reviews_found: Mapped[int] = mapped_column(Integer, default=0)
    reviews_new: Mapped[int] = mapped_column(Integer, default=0)
    warning: Mapped[str | None] = mapped_column(Text)
    error: Mapped[str | None] = mapped_column(Text)
    cancel_requested: Mapped[bool] = mapped_column(default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
class Review(Base):
    __tablename__ = "reviews"
    __table_args__ = (UniqueConstraint("app_id", "review_id", name="uq_app_review"),)
    id: Mapped[int] = mapped_column(primary_key=True)
    app_id: Mapped[str] = mapped_column(ForeignKey("play_apps.app_id"), index=True)
    review_id: Mapped[str] = mapped_column(String(255))
    score: Mapped[int] = mapped_column(Integer)
    content: Mapped[str | None] = mapped_column(Text)
    at: Mapped[datetime | None] = mapped_column(DateTime)
    version: Mapped[str | None] = mapped_column(String(100))
    thumbs_up: Mapped[int] = mapped_column(Integer, default=0)
    region: Mapped[str] = mapped_column(String(8))
    language: Mapped[str] = mapped_column(String(8))
    extracted_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
