import { setCors } from '../lib/reviews.mjs';
import { AuthError, SupabaseApiError, currentUser, getUserCredits, getUserPlan } from '../lib/supabase.mjs';

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'GET') return response.status(405).json({ detail: 'Usá GET.' });
  try {
    const user = await currentUser(request.headers.authorization);
    const plan = await getUserPlan(user.id);
    const credits = await getUserCredits(user.id, plan, user.identityHash);
    return response.status(200).json({ plan, daily_limit: credits.limit, credits_remaining: credits.remaining, credits_limit: credits.limit, max_reviews: plan === 'premium' ? 500 : 50 });
  } catch (error) {
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    if (error instanceof SupabaseApiError) console.error('Supabase profile failed', { status: error.status, code: error.code, message: error.message });
    return response.status(503).json({ detail: 'No se pudo consultar el plan.' });
  }
}
