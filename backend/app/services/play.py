"""Only public catalogue/review calls. No CAPTCHA bypass or credential handling."""
from google_play_scraper import search, reviews, Sort
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=12), reraise=True)
def find_apps(query: str, country: str = "ar", lang: str = "es") -> list[dict]:
    return search(query, lang=lang, country=country, n_hits=12)

@retry(stop=stop_after_attempt(3), wait=wait_exponential(min=1, max=20), reraise=True)
def get_page(app_id: str, country: str, lang: str, token=None):
    return reviews(app_id, lang=lang, country=country, sort=Sort.NEWEST, count=200, continuation_token=token)
