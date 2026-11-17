import gplay from 'google-play-scraper';

const KEYWORDS = {
  'inicio de sesión': ['login', 'iniciar sesión', 'contraseña', 'cuenta', 'sign in', 'log in', 'password', 'account', 'senha', 'entrar', 'conta'],
  rendimiento: ['lenta', 'lent', 'congela', 'lag', 'slow', 'sluggish', 'freezes', 'travando', 'devagar'],
  'cierres inesperados': ['cierra', 'crash', 'se sale', 'reinicia', 'closes', 'stops', 'keeps stopping', 'fecha', 'parou'],
  notificaciones: ['notificación', 'notificaciones', 'notification', 'notifications', 'notificação', 'notificações'],
  conectividad: ['conexión', 'internet', 'conecta', 'connection', 'connectivity', 'offline', 'conexão'],
  pagos: ['pago', 'cobro', 'tarjeta', 'payment', 'charge', 'card', 'pagamento', 'cobrança', 'cartão'],
};

const LANGUAGE_WORDS = {
  es: new Set(['el', 'la', 'los', 'las', 'un', 'una', 'que', 'para', 'con', 'pero', 'muy', 'bien', 'mala', 'buena', 'funciona', 'aplicación', 'reseña', 'cuando', 'porque', 'tiene', 'puedo', 'gracias', 'error', 'no']),
  en: new Set(['the', 'this', 'that', 'with', 'but', 'very', 'good', 'bad', 'works', 'working', 'app', 'when', 'because', 'have', 'cannot', 'can', 'please', 'review', 'great', 'not']),
  pt: new Set(['o', 'a', 'os', 'as', 'uma', 'para', 'com', 'mas', 'muito', 'bem', 'boa', 'bom', 'funciona', 'aplicativo', 'avaliação', 'quando', 'porque', 'tem', 'posso', 'obrigado', 'não']),
};

export function detectReviewLanguage(text) {
  const tokens = String(text ?? '').toLowerCase().match(/[\p{L}]+/gu) ?? [];
  const score = Object.fromEntries(Object.entries(LANGUAGE_WORDS).map(([lang, words]) => [lang, tokens.filter((token) => words.has(token)).length]));
  if (tokens.some((token) => /[ãõç]/u.test(token))) score.pt += 2;
  if (tokens.some((token) => /[ñ¿¡]/u.test(token))) score.es += 2;
  const sorted = Object.entries(score).sort((a, b) => b[1] - a[1]);
  return sorted[0][1] >= 2 && sorted[0][1] > sorted[1][1] ? sorted[0][0] : 'und';
}

export async function collectMarketReviews(appId, periodDays, maxReviews, country, languages) {
  const batches = await Promise.all(languages.map((language) => collectRecentReviews(appId, periodDays, maxReviews, { country, language })));
  const unique = new Map();
  for (const batch of batches) for (const review of batch) {
    const key = String(review.id ?? `${review.date}-${review.score}-${review.text}`);
    const language = detectReviewLanguage(review.text);
    const candidate = { ...review, language };
    if (!unique.has(key) || (unique.get(key).language === 'und' && language !== 'und')) unique.set(key, candidate);
  }
  return [...unique.values()]
    .sort((a, b) => new Date(b.date ?? 0) - new Date(a.date ?? 0))
    .slice(0, maxReviews);
}

export function keepDistinctMarketReviews(markets) {
  const countriesByReview = new Map();
  for (const market of markets) for (const review of market.reviews) {
    const countries = countriesByReview.get(review.id) ?? new Set();
    countries.add(market.country);
    countriesByReview.set(review.id, countries);
  }
  return markets.map((market) => {
    const reviews = market.reviews.filter((review) => countriesByReview.get(review.id)?.size === 1);
    return { ...market, shared_review_count: market.reviews.length - reviews.length, reviews };
  });
}

export async function collectRecentReviews(appId, periodDays, maxReviews, market = { country: 'ar', language: 'es' }) {
  const cutoff = new Date(Date.now() - periodDays * 24 * 60 * 60 * 1000);
  const collected = [];
  let nextPaginationToken;

  while (collected.length < maxReviews) {
    const page = await gplay.reviews({
      appId,
      country: market.country,
      lang: market.language,
      sort: gplay.sort.NEWEST,
      num: 200,
      paginate: true,
      nextPaginationToken,
    });
    const reviews = page.data ?? [];
    const recent = reviews.filter((review) => review.date && new Date(review.date) >= cutoff);
    collected.push(...recent);
    if (recent.length < reviews.length || !page.nextPaginationToken || reviews.length === 0) break;
    nextPaginationToken = page.nextPaginationToken;
  }

  return collected.slice(0, maxReviews);
}

export function createInsights(reviews) {
  const sentiments = { positive: 0, negative: 0, neutral: 0 };
  const categories = new Map();
  let scoreTotal = 0;

  for (const review of reviews) {
    scoreTotal += review.score ?? 0;
    if (review.score <= 2) sentiments.negative += 1;
    else if (review.score >= 4) sentiments.positive += 1;
    else sentiments.neutral += 1;

    const text = (review.text ?? '').toLowerCase();
    for (const [category, words] of Object.entries(KEYWORDS)) {
      if (words.some((word) => text.includes(word))) {
        categories.set(category, (categories.get(category) ?? 0) + 1);
      }
    }
  }

  return {
    sample_size: reviews.length,
    average_score: reviews.length ? scoreTotal / reviews.length : 0,
    sentiments,
    top_categories: [...categories.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8),
  };
}

export function createRatingTrend(reviews, days) {
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  const byDay = new Map();
  for (const review of reviews) {
    const date = review.date ? new Date(review.date) : null;
    if (!date || Number.isNaN(date.getTime()) || date.getTime() < cutoff) continue;
    const day = date.toISOString().slice(0, 10);
    const values = byDay.get(day) ?? [];
    values.push(review.score ?? 0);
    byDay.set(day, values);
  }
  const points = [...byDay.entries()].sort(([first], [second]) => first.localeCompare(second)).map(([day, values]) => ({
    day,
    average_score: Number((values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(2)),
    count: values.length,
  }));
  const average = points.length ? points.reduce((sum, point) => sum + point.average_score * point.count, 0) / points.reduce((sum, point) => sum + point.count, 0) : 0;
  // Show the observed change between the first and last dates in the sample.
  // This is easier to interpret than extrapolating a regression over sparse days.
  const first = points[0] ?? null;
  const latest = points.at(-1) ?? null;
  const change = first && latest ? latest.average_score - first.average_score : 0;
  return {
    days,
    average_score: Number(average.toFixed(2)),
    change: Number(change.toFixed(2)),
    direction: change > 0.04 ? 'positive' : change < -0.04 ? 'negative' : 'stable',
    first_score: first?.average_score ?? null,
    latest_score: latest?.average_score ?? null,
    first_day: first?.day ?? null,
    latest_day: latest?.day ?? null,
    points,
  };
}

export function setCors(response) {
  response.setHeader('Access-Control-Allow-Origin', '*');
  response.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
}
