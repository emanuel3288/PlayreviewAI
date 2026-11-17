import { setCors } from '../lib/reviews.mjs';
import { AuthError, SupabaseApiError, currentUser, getAnalysisJob } from '../lib/supabase.mjs';
import { processNextAnalysisJob } from '../lib/analysis-queue.mjs';

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'GET') return response.status(405).json({ detail: 'Usá GET.' });
  try {
    const user = await currentUser(request.headers.authorization);
    const jobId = String(request.query.id ?? '');
    if (!jobId) return response.status(400).json({ detail: 'Falta el identificador del análisis.' });
    await processNextAnalysisJob();
    const job = await getAnalysisJob(user.id, jobId);
    if (!job) return response.status(404).json({ detail: 'No encontramos este análisis.' });
    if (job.status === 'completed') return response.status(200).json({ status: job.status, result: job.result });
    if (job.status === 'failed') return response.status(502).json({ detail: job.error || 'No se pudo completar el análisis.' });
    return response.status(202).json({ id: job.id, status: job.status, created_at: job.created_at });
  } catch (error) {
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    if (error instanceof SupabaseApiError) console.error('Supabase analysis job failed', { status: error.status, code: error.code, message: error.message });
    return response.status(503).json({ detail: 'No se pudo consultar la cola de análisis.' });
  }
}
