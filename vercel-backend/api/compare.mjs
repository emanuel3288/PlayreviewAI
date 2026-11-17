import gplay from 'google-play-scraper';
import { createInsights, createRatingTrend, setCors } from '../lib/reviews.mjs';
import { collectCachedMarketReviews } from '../lib/review-cache.mjs';
import { AuthError, consumeAnalysis, currentUser, getUserPlan } from '../lib/supabase.mjs';
import { MARKETS, marketFor } from '../lib/markets.mjs';

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'POST') return response.status(405).json({ detail: 'Usá POST.' });

  const { app_id: appId, countries } = request.body ?? {};
  const markets = Array.isArray(countries)
    ? countries.map((country) => marketFor(country, MARKETS[String(country).toLowerCase()]))
    : [];
  if (!/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/.test(appId ?? '')
      || markets.length < 2 || markets.length > 3
      || new Set(markets.map((market) => market?.country)).size !== markets.length
      || markets.some((market) => !market)) {
    return response.status(400).json({ detail: 'Elegí una aplicación y entre dos y tres tiendas válidas.' });
  }

  try {
    const user = await currentUser(request.headers.authorization);
    if (await getUserPlan(user.id) !== 'premium') {
      return response.status(403).json({ detail: 'La comparación de calificaciones por tienda requiere Premium.' });
    }

    // Una comparación de hasta tres tiendas consume cinco créditos Premium.
    const quota = await consumeAnalysis(user.id, 5, user.identityHash);
    if (!quota.allowed) return response.status(429).json({ detail: `No te quedan créditos hoy. Te quedan ${quota.remaining ?? 0} créditos disponibles.` });

    const marketsResult = await Promise.all(markets.map(async (market) => {
      const [listing, sample] = await Promise.allSettled([
        gplay.app({ appId, country: market.country, lang: market.language }),
        collectCachedMarketReviews(appId, 30, 50, market.country, [market.language]),
      ]);
      const app = listing.status === 'fulfilled' ? listing.value : null;
      const reviews = sample.status === 'fulfilled' ? sample.value : [];
      if (listing.status === 'rejected') console.warn('Google Play store rating lookup failed', { appId, country: market.country, message: listing.reason?.message });
      if (sample.status === 'rejected') console.warn('Google Play store review lookup failed', { appId, country: market.country, message: sample.reason?.message });
      const available = typeof app?.score === 'number' && Number.isFinite(app.score);
      const histogram = app?.histogram && typeof app.histogram === 'object'
        ? Object.fromEntries([1, 2, 3, 4, 5].map((star) => [star, Number(app.histogram[star] ?? 0)]))
        : null;
      return {
        country: market.country,
        language: market.language,
        available,
        score: available ? app.score : null,
        score_text: available ? app.scoreText ?? app.score.toFixed(1) : null,
        ratings: Number(app?.ratings ?? 0),
        histogram,
        store_url: app?.url ?? null,
        error: available ? null : 'No se obtuvo la calificación pública de esta tienda.',
        review_error: sample.status === 'rejected' ? 'No se pudieron consultar las reseñas de esta tienda.' : null,
        sample_period_days: 30,
        sample_limit: 50,
        reviews: reviews.map((review) => ({
          id: String(review.id ?? `${review.date}-${review.score}-${review.text}`),
          score: review.score ?? 0,
          content: review.text ?? 'Sin comentario',
          region: 'und', language: review.language,
          date: review.date ? new Date(review.date).toISOString() : null,
        })),
      };
    }));

    // Google Play puede devolver una misma reseña en más de una tienda. Cada país
    // conserva la muestra que devolvió su consulta para que nunca quede vacío.
    const comparedMarkets = marketsResult.map((market) => ({
      ...market,
      shared_review_count: 0,
      insights: createInsights(market.reviews.map((review) => ({ score: review.score, text: review.content, date: review.date }))),
      trends: { week: createRatingTrend(market.reviews, 7), two_week: createRatingTrend(market.reviews, 14), month: createRatingTrend(market.reviews, 30) },
      reviews: market.reviews.map(({ id, ...review }) => review),
    }));

    if (comparedMarkets.every((market) => !market.available && !market.reviews.length)) {
      return response.status(502).json({ detail: 'Google Play no devolvió calificaciones ni reseñas para las tiendas elegidas.' });
    }
    return response.status(200).json({ markets: comparedMarkets, analyses_remaining: quota.remaining });
  } catch (error) {
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    console.error('Store comparison failed', { message: error.message });
    return response.status(503).json({ detail: 'No se pudo preparar la comparación de calificaciones.' });
  }
}
