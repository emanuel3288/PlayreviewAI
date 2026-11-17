import { createRemoteJWKSet, jwtVerify } from 'jose';
import { createHmac } from 'node:crypto';

const config = () => {
  const rawUrl = process.env.SUPABASE_URL;
  const url = rawUrl ? new URL(rawUrl).origin : null;
  const publishableKey = process.env.SUPABASE_PUBLISHABLE_KEY;
  const secretKey = process.env.SUPABASE_SECRET_KEY;
  if (!url || !publishableKey || !secretKey) throw new Error('La conexión con Supabase todavía no está configurada.');
  return { url, publishableKey, secretKey };
};

async function request(path, { method = 'GET', body, token, admin = false, headers = {} } = {}) {
  const { url, publishableKey, secretKey } = config();
  const key = admin ? secretKey : publishableKey;
  const response = await fetch(`${url}${path}`, {
    method,
    headers: {
      apikey: key,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) {
    let error;
    try { error = JSON.parse(text); } catch { error = {}; }
    throw new SupabaseApiError(response.status, error.code ?? error.error_code, error.msg ?? error.message ?? error.error_description ?? text);
  }
  return text ? JSON.parse(text) : null;
}

export async function adminRequest(path, options = {}) {
  return request(path, { ...options, admin: true });
}

export class SupabaseApiError extends Error {
  constructor(status, code, message) {
    super(message || 'Supabase no respondió correctamente.');
    this.status = status;
    this.code = code;
  }
}

const firebaseProjectId = () => {
  const projectId = process.env.FIREBASE_PROJECT_ID;
  if (!projectId) throw new Error('Firebase Authentication todavía no está configurado en el servidor.');
  return projectId;
};

const firebaseKeys = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
);

export async function currentUser(authorization) {
  const token = authorization?.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) throw new AuthError('Iniciá sesión para analizar reseñas.');
  const projectId = firebaseProjectId();
  try {
    const { payload } = await jwtVerify(token, firebaseKeys, {
      audience: projectId,
      issuer: `https://securetoken.google.com/${projectId}`,
    });
    if (!payload.sub || typeof payload.sub !== 'string') throw new Error('Token sin usuario.');
    if (payload.email_verified !== true) throw new AuthError('Confirmá tu correo electrónico antes de analizar reseñas.');
    const email = typeof payload.email === 'string' ? payload.email.trim().toLowerCase() : '';
    return { id: payload.sub, token, identityHash: email ? createIdentityHash(email) : null };
  } catch (error) {
    if (error instanceof AuthError) throw error;
    throw new AuthError('Tu sesión venció o no es válida. Iniciá sesión nuevamente.');
  }
}

function createIdentityHash(email) {
  const { secretKey } = config();
  return createHmac('sha256', secretKey).update(email).digest('hex');
}

export async function getUserPlan(userId) {
  await request('/rest/v1/profiles?on_conflict=id', {
    method: 'POST', admin: true,
    headers: { Prefer: 'resolution=ignore-duplicates,return=minimal' }, body: [{ id: userId }],
  });
  const profiles = await request(`/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}&select=plan`, { admin: true });
  return profiles?.[0]?.plan === 'premium' ? 'premium' : 'free';
}

export async function getUserCredits(userId, plan, identityHash = null) {
  const today = new Date().toISOString().slice(0, 10);
  const rows = await adminRequest(`/rest/v1/daily_analysis_usage?user_id=eq.${encodeURIComponent(userId)}&usage_date=eq.${today}&select=used_credits`);
  const limit = plan === 'premium' ? 30 : 3;
  const accountUsed = Number(rows?.[0]?.used_credits ?? 0);
  let used = accountUsed;
  if (plan !== 'premium' && identityHash) {
    const identityRows = await adminRequest(`/rest/v1/free_credit_identity_usage?identity_hash=eq.${encodeURIComponent(identityHash)}&usage_date=eq.${today}&select=used_credits`);
    used = Math.max(accountUsed, Number(identityRows?.[0]?.used_credits ?? 0));
  }
  return { remaining: Math.max(0, limit - used), limit };
}

export async function getAnalysisPortfolio(userId, maxRuns = 12) {
  const select = encodeURIComponent('app_id,period_days,review_count,average_score,sentiments,created_at,apps(title,developer)');
  const runs = await request(`/rest/v1/analysis_runs?user_id=eq.${encodeURIComponent(userId)}&select=${select}&order=created_at.desc&limit=300`, { admin: true });
  const apps = new Map();
  for (const run of runs ?? []) {
    const appId = run.app_id;
    if (!apps.has(appId)) apps.set(appId, {
      app_id: appId,
      title: run.apps?.title || appId,
      developer: run.apps?.developer || null,
      runs: [],
    });
    const history = apps.get(appId).runs;
    if (history.length < maxRuns) history.push({
      date: run.created_at,
      period_days: run.period_days,
      review_count: run.review_count,
      average_score: Number(run.average_score),
      negative_count: Number(run.sentiments?.negative || 0),
    });
  }
  return [...apps.values()];
}

export async function deleteAccountData(userId) {
  // profiles is the parent row for the user's tracked apps, analyses, usage,
  // and queued jobs. The schema's ON DELETE CASCADE constraints remove those
  // records atomically while shared app/review catalogue data remains intact.
  await adminRequest(`/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}`, {
    method: 'DELETE',
    headers: { Prefer: 'return=minimal' },
  });
}

export async function consumeAnalysis(userId, credits, identityHash = null) {
  const result = await request('/rest/v1/rpc/consume_analysis_credits', {
      method: 'POST', admin: true,
      body: { p_user_id: userId, p_credits: credits, p_identity_hash: identityHash },
    });
  return Array.isArray(result) ? result[0] : result;
}

export async function saveAnalysis({ userId, app, periodDays, reviews, insights, trends, market }) {
  await request('/rest/v1/apps?on_conflict=id', {
    method: 'POST', admin: true,
    headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
    body: [{ id: app.id, title: app.title, developer: app.developer ?? null, icon_url: app.icon ?? null }],
  });
  await request('/rest/v1/tracked_apps?on_conflict=user_id,app_id', {
    method: 'POST', admin: true,
    headers: { Prefer: 'resolution=ignore-duplicates,return=minimal' }, body: [{ user_id: userId, app_id: app.id }],
  });
  if (reviews.length) {
    const rows = reviews.map((review, index) => ({
      app_id: app.id,
      source_review_id: String(review.id ?? `${review.date ?? 'unknown'}-${review.score ?? 0}-${index}-${(review.text ?? '').slice(0, 80)}`),
      rating: review.score ?? 1,
      content: (review.text ?? '').slice(0, 2000),
      review_date: review.date ? new Date(review.date).toISOString() : null,
      region: 'und', language: review.language ?? 'und', last_seen_at: new Date().toISOString(),
    }));
    await request('/rest/v1/reviews?on_conflict=app_id,source_review_id', {
      method: 'POST', admin: true,
      headers: { Prefer: 'resolution=merge-duplicates,return=minimal' }, body: rows,
    });
  }
  await request('/rest/v1/analysis_runs', {
    method: 'POST', admin: true,
    headers: { Prefer: 'return=minimal' },
    body: [{ user_id: userId, app_id: app.id, period_days: periodDays, review_count: reviews.length,
      average_score: insights.average_score, sentiments: insights.sentiments, top_categories: insights.top_categories,
      week_trend: trends.week, month_trend: trends.month }],
  });
  await pruneAnalysisHistory(userId, app.id);
}

async function pruneAnalysisHistory(userId, appId) {
  const keep = await adminRequest(`/rest/v1/analysis_runs?user_id=eq.${encodeURIComponent(userId)}&app_id=eq.${encodeURIComponent(appId)}&select=id&order=created_at.desc&limit=12`);
  const ids = (keep ?? []).map((row) => row.id).filter(Boolean);
  if (!ids.length) return;
  const encodedIds = ids.map((id) => encodeURIComponent(id)).join(',');
  await adminRequest(`/rest/v1/analysis_runs?user_id=eq.${encodeURIComponent(userId)}&app_id=eq.${encodeURIComponent(appId)}&id=not.in.(${encodedIds})`, {
    method: 'DELETE', headers: { Prefer: 'return=minimal' },
  });
}

export async function enqueueAnalysisJob(userId, payload) {
  const jobs = await adminRequest('/rest/v1/analysis_jobs?select=id,status,created_at', {
    method: 'POST', headers: { Prefer: 'return=representation' }, body: [{ user_id: userId, payload }],
  });
  return jobs?.[0];
}

export async function claimNextAnalysisJob() {
  const jobs = await adminRequest('/rest/v1/rpc/claim_next_analysis_job', { method: 'POST', body: {} });
  return Array.isArray(jobs) ? jobs[0] : jobs;
}

export async function getAnalysisJob(userId, jobId) {
  const jobs = await adminRequest(`/rest/v1/analysis_jobs?id=eq.${encodeURIComponent(jobId)}&user_id=eq.${encodeURIComponent(userId)}&select=id,status,result,error,created_at,started_at,completed_at`, { admin: true });
  return jobs?.[0] ?? null;
}

export async function finishAnalysisJob(jobId, result) {
  await adminRequest(`/rest/v1/analysis_jobs?id=eq.${encodeURIComponent(jobId)}`, {
    method: 'PATCH', headers: { Prefer: 'return=minimal' }, body: { status: 'completed', result, error: null, completed_at: new Date().toISOString() },
  });
}

export async function failAnalysisJob(jobId, error) {
  await adminRequest(`/rest/v1/analysis_jobs?id=eq.${encodeURIComponent(jobId)}`, {
    method: 'PATCH', headers: { Prefer: 'return=minimal' }, body: { status: 'failed', error: String(error).slice(0, 500), completed_at: new Date().toISOString() },
  });
}

export class AuthError extends Error {}
