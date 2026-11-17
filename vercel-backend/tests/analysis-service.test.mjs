import test from 'node:test';
import assert from 'node:assert/strict';
import { AnalysisServiceError, validateAnalysisInput } from '../lib/analysis-service.mjs';

test('validates the accepted analysis limits and markets before queueing', () => {
  const input = validateAnalysisInput({
    app_id: 'com.example.app', app_title: 'Example', period_days: 30,
    max_reviews: 500, country: 'ar', language: 'es', review_languages: ['es', 'en'],
  });
  assert.equal(input.market.country, 'ar');
  assert.deepEqual(input.reviewLanguages, ['es', 'en']);
});

test('rejects a free-form queue payload with unsupported limits', () => {
  assert.throws(
    () => validateAnalysisInput({ app_id: 'not-an-app-id', period_days: 5, max_reviews: 70, country: 'ar', language: 'es' }),
    (error) => error instanceof AnalysisServiceError && error.status === 400,
  );
});
