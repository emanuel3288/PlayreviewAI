const DAILY_FREE_ANALYSES = 3;
const INSTALLATION_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export async function consumeFreeAnalysis(installationId) {
  if (!INSTALLATION_ID.test(installationId ?? '')) {
    throw new QuotaConfigurationError('Instalación inválida. Actualizá la aplicación e intentá nuevamente.');
  }
  const baseUrl = process.env.UPSTASH_REDIS_REST_URL?.replace(/\/$/, '');
  const token = process.env.UPSTASH_REDIS_REST_TOKEN;
  if (!baseUrl || !token) {
    throw new QuotaConfigurationError('El límite gratuito todavía no está configurado en el servidor.');
  }

  const now = new Date();
  const argentinaNow = new Date(now.getTime() - 3 * 60 * 60 * 1000);
  const day = argentinaNow.toISOString().slice(0, 10);
  const tomorrowArgentina = new Date(`${day}T00:00:00.000Z`).getTime() + 24 * 60 * 60 * 1000;
  const secondsToReset = Math.max(1, Math.ceil((tomorrowArgentina - argentinaNow.getTime()) / 1000));
  const key = `free-analysis:${day}:${installationId}`;
  const headers = { Authorization: `Bearer ${token}` };

  const increment = await fetch(`${baseUrl}/incr/${encodeURIComponent(key)}`, { headers });
  if (!increment.ok) throw new QuotaConfigurationError('No se pudo verificar el límite gratuito.');
  const count = Number((await increment.json()).result);
  const expiry = await fetch(`${baseUrl}/expire/${encodeURIComponent(key)}/${secondsToReset}`, { headers });
  if (!expiry.ok) throw new QuotaConfigurationError('No se pudo verificar el límite gratuito.');
  return { allowed: count <= DAILY_FREE_ANALYSES, remaining: Math.max(0, DAILY_FREE_ANALYSES - count) };
}

export class QuotaConfigurationError extends Error {}
