import { setCors } from '../lib/reviews.mjs';
import { AuthError, SupabaseApiError, currentUser, deleteAccountData } from '../lib/supabase.mjs';

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'DELETE') return response.status(405).json({ detail: 'Usá DELETE.' });
  try {
    const user = await currentUser(request.headers.authorization);
    await deleteAccountData(user.id);
    return response.status(204).end();
  } catch (error) {
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    if (error instanceof SupabaseApiError) console.error('Supabase account deletion failed', { status: error.status, code: error.code, message: error.message });
    return response.status(503).json({ detail: 'No se pudieron eliminar tus datos. Intentá nuevamente.' });
  }
}
