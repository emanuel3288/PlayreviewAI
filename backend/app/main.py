from contextlib import asynccontextmanager
from datetime import datetime, timedelta
from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from .analysis import analyse_reviews
from .config import settings
from .database import Base, engine, get_db
from .models import PlayApp, Review, ScrapeJob
from .schemas import CreateJob, JobOut, SearchResult
from .services.play import find_apps
from .worker import scrape_public_reviews

@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(engine)
    yield
app = FastAPI(title="AppInsight AI Public Reviews API", version="0.1.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=settings().cors_origins.split(","), allow_methods=["GET", "POST"], allow_headers=["Content-Type", "Authorization"])

@app.get("/health")
def health(): return {"status": "ok"}

@app.get("/api/apps/search", response_model=list[SearchResult])
def search_apps(q: str = Query(min_length=2, max_length=80), country: str = Query("ar", pattern="^[a-z]{2}$"), lang: str = Query("es", pattern="^[a-z]{2}$")):
    try:
        raw = find_apps(q.strip(), country, lang)
        # Google may omit optional metadata in individual catalogue results.
        results = [SearchResult(app_id=x["appId"], title=x["title"], developer=x.get("developer"), icon=x.get("icon"), genre=x.get("genre"), score=x.get("score"), ratings=x.get("ratings"), summary=x.get("summary")) for x in raw if x.get("appId") and x.get("title")]
    except Exception as exc:
        raise HTTPException(502, "Google Play did not respond; retry later.") from exc
    return results

@app.post("/api/scraping/jobs", response_model=JobOut, status_code=202)
def create_job(payload: CreateJob, db: Session = Depends(get_db)):
    existing = db.scalar(select(ScrapeJob).where(ScrapeJob.app_id == payload.app_id, ScrapeJob.status.in_(["queued", "preparing", "downloading", "normalizing", "analysing"])))
    if existing: return existing
    app = db.get(PlayApp, payload.app_id)
    if not app:
        app = PlayApp(app_id=payload.app_id, title=payload.app_id)
        db.add(app)
    pairs = [{"country": c, "language": payload.languages[min(i, len(payload.languages)-1)], "period_days": payload.period_days} for i, c in enumerate(payload.regions)]
    job = ScrapeJob(app_id=payload.app_id, requested_regions=pairs)
    db.add(job); db.commit(); db.refresh(job)
    scrape_public_reviews.delay(job.id)
    return job

@app.get("/api/scraping/jobs/{job_id}", response_model=JobOut)
def get_job(job_id: str, db: Session = Depends(get_db)):
    job = db.get(ScrapeJob, job_id)
    if not job: raise HTTPException(404, "Job not found")
    return job

@app.post("/api/scraping/jobs/{job_id}/cancel", response_model=JobOut)
def cancel_job(job_id: str, db: Session = Depends(get_db)):
    job = db.get(ScrapeJob, job_id)
    if not job: raise HTTPException(404, "Job not found")
    job.cancel_requested = True; db.commit(); db.refresh(job)
    return job

def selected_period_cutoff(app_id: str, db: Session) -> tuple[datetime, int]:
    latest_job = db.scalar(select(ScrapeJob).where(ScrapeJob.app_id == app_id, ScrapeJob.status == "completed").order_by(ScrapeJob.updated_at.desc()))
    period_days = 30
    if latest_job and latest_job.requested_regions and isinstance(latest_job.requested_regions[0], dict):
        period_days = int(latest_job.requested_regions[0].get("period_days", period_days))
    return datetime.utcnow() - timedelta(days=period_days), period_days

@app.get("/api/apps/{app_id}/insights")
def insights(app_id: str, db: Session = Depends(get_db)):
    cutoff, period_days = selected_period_cutoff(app_id, db)
    rows = db.scalars(select(Review).where(Review.app_id == app_id, Review.at >= cutoff)).all()
    if not rows: raise HTTPException(404, "No real reviews collected for this app yet")
    return analyse_reviews(rows) | {"average_score": db.scalar(select(func.avg(Review.score)).where(Review.app_id == app_id, Review.at >= cutoff)), "source": f"public Google Play reviews from the last {period_days} days collected by this service"}

@app.get("/api/apps/{app_id}/reviews")
def list_reviews(app_id: str, limit: int = Query(50, ge=1, le=100), db: Session = Depends(get_db)):
    cutoff, _ = selected_period_cutoff(app_id, db)
    rows = db.scalars(select(Review).where(Review.app_id == app_id, Review.at >= cutoff).order_by(Review.at.desc()).limit(limit)).all()
    return [{"review_id": row.review_id, "score": row.score, "content": row.content, "at": row.at, "region": row.region, "language": row.language, "version": row.version} for row in rows]
