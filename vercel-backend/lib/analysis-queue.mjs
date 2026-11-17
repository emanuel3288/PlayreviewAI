import { claimNextAnalysisJob, failAnalysisJob, finishAnalysisJob } from './supabase.mjs';
import { executeAnalysisJob } from './analysis-service.mjs';

export async function processNextAnalysisJob() {
  const job = await claimNextAnalysisJob();
  if (!job) return null;
  try {
    const result = await executeAnalysisJob(job);
    await finishAnalysisJob(job.id, result);
  } catch (error) {
    await failAnalysisJob(job.id, error.message || 'No se pudo completar el análisis.');
  }
  return job.id;
}
