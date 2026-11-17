import { setCors } from '../lib/reviews.mjs';
import { AuthError, SupabaseApiError, currentUser, getAnalysisPortfolio, getUserPlan } from '../lib/supabase.mjs';

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'GET') return response.status(405).json({ detail: 'Usá GET.' });
  try {
    const user = await currentUser(request.headers.authorization);
    const plan = await getUserPlan(user.id);
    const apps = await getAnalysisPortfolio(user.id, plan === 'premium' ? 12 : 3);
    return response.status(200).json({ apps });
  } catch (error) {
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    if (error instanceof SupabaseApiError) console.error('Supabase portfolio failed', { status: error.status, code: error.code, message: error.message });
    return response.status(503).json({ detail: 'No se pudo consultar el historial de apps.' });
  }
}
