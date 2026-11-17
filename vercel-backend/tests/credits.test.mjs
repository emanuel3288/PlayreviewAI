import test from 'node:test';
import assert from 'node:assert/strict';
import { creditsForAnalysis } from '../lib/analysis-service.mjs';

test('charges credits according to analysis depth', () => {
  assert.equal(creditsForAnalysis(50, 1), 1);
  assert.equal(creditsForAnalysis(100, 1), 2);
  assert.equal(creditsForAnalysis(500, 1), 5);
  assert.equal(creditsForAnalysis(50, 3), 5);
});
