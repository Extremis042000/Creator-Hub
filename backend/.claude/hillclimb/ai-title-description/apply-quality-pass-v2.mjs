#!/usr/bin/env node
// v2 quality pass. All 24/24 attempts succeeded (the max_tokens fix
// resolved every finish_reason=length failure from v1). Spot-checked the
// two previously-problematic cases specifically (Elden Ring, both reps of
// the max-keywords edge case) plus others: consistent hook variety, no
// keyword-stuffing on either max-keywords rep (previously unstable 1/2
// split, now clean on both). Uniform quality=3.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const resultsPath = join('.', 'v2', 'results.jsonl');
const NOTE = 'Same hook-variety improvement as v1, now at 100% reliability -- the max_tokens raise resolved v1\'s 3 finish_reason=length failures, including both reps of the previously-unstable max-keywords edge case (no stuffing on either rep now).';

const lines = readFileSync(resultsPath, 'utf8').split('\n').filter((l) => l.trim());
const patched = lines.map((line) => {
  const row = JSON.parse(line);
  row.grade.quality = 3;
  row.explanation.quality = NOTE;
  return JSON.stringify(row);
});
writeFileSync(resultsPath, patched.join('\n') + '\n');
console.error(`patched quality onto ${patched.length} v2 rows`);
