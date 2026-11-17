import gplay from 'google-play-scraper';
import { setCors } from '../../lib/reviews.mjs';
import { marketFor } from '../../lib/markets.mjs';

export default async function handler(request, response) {
  setCors(response);
  const query = String(request.query.q ?? '').trim();
  if (query.length < 2) return response.status(400).json({ detail: 'Ingresá al menos 2 caracteres.' });
  const market = marketFor(request.query.country, request.query.lang);
  if (!market) return response.status(400).json({ detail: 'La tienda o el idioma elegidos no están disponibles.' });

  try {
    const results = await gplay.search({ term: query, country: market.country, lang: market.language, num: 12 });
    return response.status(200).json(results.filter((app) => app.appId).map((app) => ({
      app_id: app.appId,
      title: app.title,
      developer: app.developer ?? null,
      icon: app.icon ?? null,
      score: app.score ?? null,
    })));
  } catch {
    return response.status(502).json({ detail: 'Google Play no respondió. Intentá de nuevo.' });
  }
}
