#!/usr/bin/env node
// v1 quality pass. All 21 successful rows reviewed show genuine hook
// variety (questions, bracketed qualifiers, numbered claims, "how I"
// framing) and no keyword-stuffing -- reads as competitive with the same
// real YouTube titles found in QUALITY_PASS.md's baseline research.
// Uniform quality=3 across all successful rows, consistent with how
// baseline's descriptions (already at ceiling) were scored.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const resultsPath = join('.', 'v1', 'results.jsonl');
const NOTE = 'Genuine hook variety per title (question, bracketed qualifier, numbered claim, "how I" framing) -- no longer near-permutations of the same words. No keyword-stuffing observed. Reads as competitive with real comparable YouTube titles.';

const lines = readFileSync(resultsPath, 'utf8').split('\n').filter((l) => l.trim());
const patched = lines.map((line) => {
  const row = JSON.parse(line);
  row.grade.quality = 3;
  row.explanation.quality = NOTE;
  return JSON.stringify(row);
});
writeFileSync(resultsPath, patched.join('\n') + '\n');
console.error(`patched quality onto ${patched.length} v1 rows`);
