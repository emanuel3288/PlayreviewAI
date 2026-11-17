import { adminRequest } from './supabase.mjs';
import { collectMarketReviews } from './reviews.mjs';

const CACHE_TTL_MS = 10 * 60 * 1000;

const cacheKey = (appId, country, languages) =>
  `${appId}:${country}:${[...languages].sort().join(',')}`;

const withinPeriod = (reviews, periodDays, maxReviews) => {
  const cutoff = Date.now() - periodDays * 24 * 60 * 60 * 1000;
  return (reviews ?? [])
    .filter((review) => review.date && new Date(review.date).getTime() >= cutoff)
    .sort((first, second) => new Date(second.date) - new Date(first.date))
    .slice(0, maxReviews);
};

export async function collectCachedMarketReviews(appId, periodDays, maxReviews, country, languages) {
  const key = cacheKey(appId, country, languages);
  const now = new Date().toISOString();
  await adminRequest(`/rest/v1/google_play_cache?expires_at=lt.${encodeURIComponent(now)}`, { method: 'DELETE', headers: { Prefer: 'return=minimal' } });
  const cached = await adminRequest(`/rest/v1/google_play_cache?cache_key=eq.${encodeURIComponent(key)}&expires_at=gt.${encodeURIComponent(now)}&select=max_reviews,reviews`, { admin: true });
  const entry = cached?.[0];
  if (entry && Number(entry.max_reviews) >= maxReviews) {
    return withinPeriod(entry.reviews, periodDays, maxReviews);
  }

  // A single 30-day sample supplies the selected period and its trend, avoiding
  // duplicate Google Play requests for the same app, country, and languages.
  const reviews = await collectMarketReviews(appId, 30, maxReviews, country, languages);
  await adminRequest('/rest/v1/google_play_cache?on_conflict=cache_key', {
    method: 'POST', headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: [{ cache_key: key, max_reviews: maxReviews, reviews, fetched_at: now, expires_at: new Date(Date.now() + CACHE_TTL_MS).toISOString() }],
  });
  return withinPeriod(reviews, periodDays, maxReviews);
}
