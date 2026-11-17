import { setCors } from '../lib/reviews.mjs';
import { AuthError, SupabaseApiError, currentUser, enqueueAnalysisJob, getAnalysisJob, getUserPlan } from '../lib/supabase.mjs';
import { AnalysisServiceError, validateAnalysisInput } from '../lib/analysis-service.mjs';
import { processNextAnalysisJob } from '../lib/analysis-queue.mjs';

const respondWithJob = async (response, userId, jobId) => {
  await processNextAnalysisJob();
  const job = await getAnalysisJob(userId, jobId);
  if (!job) return response.status(404).json({ detail: 'No encontramos este análisis.' });
  if (job.status === 'completed') return response.status(200).json({ status: job.status, result: job.result });
  if (job.status === 'failed') return response.status(502).json({ detail: job.error || 'No se pudo completar el análisis.' });
  return response.status(202).json({ id: job.id, status: job.status, created_at: job.created_at });
};

export default async function handler(request, response) {
  setCors(response);
  if (request.method !== 'POST') return response.status(405).json({ detail: 'Usá POST.' });
  try {
    const input = validateAnalysisInput(request.body);
    const user = await currentUser(request.headers.authorization);
    // Ensure the Firebase user has a profile before the queue's foreign key is used.
    await getUserPlan(user.id);
    const job = await enqueueAnalysisJob(user.id, { userId: user.id, identityHash: user.identityHash, input });
    return respondWithJob(response, user.id, job.id);
  } catch (error) {
    if (error instanceof AnalysisServiceError) return response.status(error.status).json({ detail: error.message });
    if (error instanceof AuthError) return response.status(401).json({ detail: error.message });
    if (error instanceof SupabaseApiError) console.error('Supabase analysis queue failed', { status: error.status, code: error.code, message: error.message });
    console.error('Analysis queue configuration failed', { message: error.message });
    return response.status(503).json({ detail: 'El servidor de análisis aún no está configurado para la cola de análisis.' });
  }
}
