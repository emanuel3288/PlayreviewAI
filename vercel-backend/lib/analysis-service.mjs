import { collectCachedMarketReviews } from './review-cache.mjs';
import { createInsights, createRatingTrend } from './reviews.mjs';
import { consumeAnalysis, getUserPlan, saveAnalysis } from './supabase.mjs';
import { marketFor } from './markets.mjs';

const REVIEW_LIMITS = [50, 100, 500];

export function creditsForAnalysis(maxReviews, languageCount) {
  if (maxReviews === 50 && languageCount === 1) return 1;
  if (maxReviews === 100 && languageCount === 1) return 2;
  return 5;
}

export class AnalysisServiceError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

export function validateAnalysisInput(body) {
  const { app_id: appId, app_title: appTitle, app_developer: appDeveloper, app_icon: appIcon, period_days: periodDays, max_reviews: maxReviews, country, language, review_languages: requestedLanguages } = body ?? {};
  const market = marketFor(country, language);
  const reviewLanguages = requestedLanguages === undefined ? [market?.language] : requestedLanguages;
  const validLanguages = Array.isArray(reviewLanguages) && reviewLanguages.length >= 1 && reviewLanguages.length <= 3
    && reviewLanguages.every((item) => typeof item === 'string' && ['es', 'en', 'pt'].includes(item)) && new Set(reviewLanguages).size === reviewLanguages.length;
  if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(appId ?? '') || ![7, 14, 30].includes(periodDays) || !REVIEW_LIMITS.includes(maxReviews) || !market || !validLanguages) {
    throw new AnalysisServiceError(400, 'Aplicación, período o cantidad inválidos.');
  }
  return { appId, appTitle, appDeveloper, appIcon, periodDays, maxReviews, market, reviewLanguages };
}

export async function executeAnalysisJob(job) {
  const { userId, identityHash, input } = job.payload;
  const { appId, appTitle, appDeveloper, appIcon, periodDays, maxReviews, market, reviewLanguages } = input;
  const currentPlan = await getUserPlan(userId);
  if (currentPlan !== 'premium' && market.country !== 'ar') throw new AnalysisServiceError(403, 'El plan gratuito permite analizar solo la tienda de Google Play de Argentina.');
  const credits = creditsForAnalysis(maxReviews, reviewLanguages.length);
  if (currentPlan !== 'premium' && credits > 1) throw new AnalysisServiceError(403, 'Esta opción requiere el plan Premium.');
  const quota = await consumeAnalysis(userId, credits, identityHash);
  if (!quota.allowed) {
    throw new AnalysisServiceError(429, `No te quedan créditos hoy. Te quedan ${quota.remaining ?? 0} créditos disponibles.`);
  }
  let trendReviews;
  try {
    trendReviews = await collectCachedMarketReviews(appId, 30, maxReviews, market.country, reviewLanguages);
  } catch (error) {
    console.error('Google Play review collection failed', { appId, message: error.message });
    throw new AnalysisServiceError(502, 'Google Play no respondió al consultar las reseñas. Intentá nuevamente más tarde.');
  }
  const reviews = periodDays === 30 ? trendReviews : trendReviews.filter((review) => new Date(review.date).getTime() >= Date.now() - periodDays * 24 * 60 * 60 * 1000).slice(0, maxReviews);
  const insights = createInsights(reviews);
  const trends = { week: createRatingTrend(trendReviews, 7), two_week: createRatingTrend(trendReviews, 14), month: createRatingTrend(trendReviews, 30) };
  await saveAnalysis({ userId, app: { id: appId, title: String(appTitle || appId).slice(0, 200), developer: appDeveloper, icon: appIcon }, periodDays, reviews, insights, trends, market });
  const now = new Date().toISOString();
  return {
    job: { id: job.id, app_id: appId, status: 'completed', requested_regions: reviewLanguages.map((selectedLanguage) => ({ country: market.country, language: selectedLanguage, period_days: periodDays })), completed_regions: reviewLanguages.map((selectedLanguage) => `${selectedLanguage}-${market.country}`), reviews_found: reviews.length, reviews_new: reviews.length, warning: reviews.length === maxReviews ? `Se analizaron las ${maxReviews} reseñas más recientes del período.` : null, analyses_remaining: quota.remaining, plan: quota.plan, error: null, created_at: now, updated_at: now },
    insights, trends,
    reviews: reviews.map((review) => ({ score: review.score ?? 0, content: review.text ?? 'Sin comentario', region: 'und', language: review.language, date: review.date ? new Date(review.date).toISOString() : null })),
  };
}
