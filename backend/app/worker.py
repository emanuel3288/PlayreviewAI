import time
from datetime import datetime, timedelta
from celery import Celery
from sqlalchemy.dialects.postgresql import insert as postgres_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from .config import settings
from .database import Base, SessionLocal, engine
from .models import PlayApp, Review, ScrapeJob
from .services.play import get_page

celery_app = Celery("appinsight", broker=settings().redis_url, backend=settings().redis_url)

def update(db, job, **fields):
    for name, value in fields.items(): setattr(job, name, value)
    db.commit(); db.refresh(job)

def store_page(db, job, page, country: str, language: str) -> int:
    """Persist a page in one transaction instead of committing every review."""
    rows = [{
        "app_id": job.app_id,
        "review_id": item["reviewId"],
        "score": item["score"],
        "content": item.get("content"),
        "at": item.get("at"),
        "version": item.get("reviewCreatedVersion"),
        "thumbs_up": item.get("thumbsUpCount", 0),
        "region": country,
        "language": language,
    } for item in page]
    insert = sqlite_insert if db.bind.dialect.name == "sqlite" else postgres_insert
    result = db.execute(insert(Review).values(rows).on_conflict_do_nothing(index_elements=["app_id", "review_id"]))
    inserted = max(result.rowcount or 0, 0)
    job.reviews_found += len(rows)
    job.reviews_new += inserted
    db.commit()
    return len(rows)

@celery_app.task(name="scrape_public_reviews", bind=True, acks_late=True)
def scrape_public_reviews(self, job_id: str):
    Base.metadata.create_all(engine)
    db = SessionLocal()
    try:
        job = db.get(ScrapeJob, job_id)
        if not job or job.cancel_requested: return
        update(db, job, status="preparing")
        regions = job.requested_regions
        languages = job.requested_regions[0:0]  # populated safely below from stored request format
        # Regions are pairs {country, language}; retain backwards safety for jobs created before this worker.
        pairs = [r if isinstance(r, dict) else {"country": r, "language": "es"} for r in regions]
        for pair in pairs:
            job = db.get(ScrapeJob, job_id)
            if job.cancel_requested:
                update(db, job, status="cancelled"); return
            country, lang = pair["country"], pair["language"]
            period_days = int(pair.get("period_days", 30))
            cutoff = datetime.utcnow() - timedelta(days=period_days)
            update(db, job, status="downloading")
            token = None; collected = 0
            # A value of zero is deliberately unbounded. Stop only when Play ends pagination,
            # cancellation is requested, or an external error occurs.
            while settings().max_reviews_per_region <= 0 or collected < settings().max_reviews_per_region:
                job = db.get(ScrapeJob, job_id)
                if job.cancel_requested:
                    update(db, job, status="cancelled"); return
                page, token = get_page(job.app_id, country, lang, token)
                if not page: break
                recent_page = [item for item in page if item.get("at") and item["at"] >= cutoff]
                if recent_page:
                    page = recent_page
                else:
                    break
                remaining = settings().max_reviews_per_region - collected
                batch = page if settings().max_reviews_per_region <= 0 else page[:remaining]
                collected += store_page(db, job, batch, country, lang)
                # Results arrive newest first, so there is nothing else in this region once the cutoff is reached.
                if len(recent_page) < 200: break
                if not token: break
                time.sleep(settings().scraping_delay_seconds)
            job = db.get(ScrapeJob, job_id)
            completed = list(job.completed_regions)
            completed.append(f"{lang}-{country}")
            update(db, job, completed_regions=completed)
        update(db, db.get(ScrapeJob, job_id), status="completed")
    except Exception as exc:
        job = db.get(ScrapeJob, job_id)
        if job: update(db, job, status="failed", error=str(exc)[:1000])
        raise
    finally: db.close()
