export const MARKETS = Object.freeze({
  ar: 'es', mx: 'es', cl: 'es', co: 'es', pe: 'es', br: 'pt', es: 'es',
  us: 'en', ca: 'en', gb: 'en', fr: 'fr', de: 'de', it: 'it', in: 'en',
  jp: 'ja', au: 'en',
});

export function marketFor(country = 'ar', language = 'es') {
  const normalizedCountry = String(country).toLowerCase();
  const normalizedLanguage = String(language).toLowerCase();
  return MARKETS[normalizedCountry] === normalizedLanguage
    ? { country: normalizedCountry, language: normalizedLanguage }
    : null;
}
