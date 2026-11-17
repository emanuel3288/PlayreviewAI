import test from 'node:test';
import assert from 'node:assert/strict';
import { createInsights, keepDistinctMarketReviews } from '../lib/reviews.mjs';

test('shared reviews do not become country-specific charts or weak points', () => {
  const markets = keepDistinctMarketReviews([
    { country: 'ar', reviews: [
      { id: 'shared', score: 1, content: 'no funciona el pago' },
      { id: 'ar-only', score: 5, content: 'excelente' },
    ] },
    { country: 'co', reviews: [
      { id: 'shared', score: 1, content: 'no funciona el pago' },
      { id: 'co-only', score: 2, content: 'no puedo iniciar sesión' },
    ] },
  ]);

  assert.deepEqual(markets.map((market) => market.reviews.map((review) => review.id)), [['ar-only'], ['co-only']]);
  assert.deepEqual(markets.map((market) => market.shared_review_count), [1, 1]);
  assert.deepEqual(markets.map((market) => createInsights(market.reviews.map((review) => ({ score: review.score, text: review.content }))).average_score), [5, 2]);
});

test('a fully shared market has no distinct review sample', () => {
  const markets = keepDistinctMarketReviews([
    { country: 'ar', reviews: [{ id: 'same' }] },
    { country: 'co', reviews: [{ id: 'same' }] },
  ]);
  assert.deepEqual(markets.map((market) => market.reviews.length), [0, 0]);
  assert.deepEqual(markets.map((market) => market.shared_review_count), [1, 1]);
});
