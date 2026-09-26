#!/usr/bin/env node
// Manual/AI-grounded quality pass (Step 2's "human spot-check" grading
// method, per the founder's explicit choice: ground grading in real
// YouTube conventions via web search rather than a Claude-judge rubric).
//
// NOT automatable end-to-end: this reads real comparisons (fetched via
// live web search of youtube.com for a handful of representative
// game/topic pairs -- Valorant clutch aces, Elden Ring Malenia guides,
// Fall Guys fails compilations) and an agent's read of all 24 generated
// outputs, then patches a `quality` score onto every row. Re-running this
// exact pass requires an agent with web search, not just `node`; see
// QUALITY_PASS.md for the full findings and how to redo it.
//
// Rubric (0-3):
//   3 = reads like a real, competitive result for this game/genre --
//       natural phrasing, correct conventions, nothing to fix.
//   2 = correct and on-topic but has a real, named gap against real
//       comparable content (e.g. low diversity across the option set).
//   1 = present but reads as spammy/unnatural compared to real content.
//   0 = fails runtime validation (see grade.valid; not used here since
//       all 24 cases passed validation).

import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const vdir = join('.', 'baseline');
const resultsPath = join(vdir, 'results.jsonl');

// All description cases (12/12): natural tone, correct channel-name/
// social-link weaving, genre-appropriate hashtags and emoji use, includes
// a call-to-action -- reads as competitive with real YouTube gaming
// descriptions across every case reviewed.
const DESCRIPTION_QUALITY = 3;
const DESCRIPTION_NOTE = 'Reads naturally, weaves channel name/social links correctly, genre-appropriate tone and hashtags -- competitive with real YouTube gaming descriptions across all 12 cases reviewed.';

// All title cases except the max-keywords edge case: correct, on-topic,
// genre-appropriate hype language (matches real Valorant/CS2/Apex clutch
// title conventions found via search), BUT every case's 7-8 title options
// are near-permutations of the same 3-4 words reordered -- real comparable
// titles vary the ANGLE (numbered lists like "TOP 200 FUNNIEST FAILS",
// parenthetical qualifiers like "(Melee Only)", meme references), not just
// word order. This is a systematic prompt gap, not per-case noise.
const TITLE_QUALITY = 2;
const TITLE_NOTE = 'On-topic and genre-appropriate hype/tone, but the 7-8 title options are near-permutations of the same words reordered -- real comparable YouTube titles vary the angle (numbered lists, parenthetical qualifiers, meme hooks), which the current prompt never asks for. See docs/decisions/09-ai-model-optimization.md Phase 36.';

// The one distinctly worse case: with 10 keywords supplied, the model
// crammed all of them in ALL CAPS into every title variant, producing
// keyword-stuffed output ("CS2 MIRAGE 1V5 1 TAP HEADSHOT CLUTCH RANKED
// FACEIT HIGHLIGHT MOMENT") that reads as spam, not a title a real
// creator would post -- unlike any real comparable title found via search.
const OVERRIDES = {
  'title-edge-longtopic-maxkeywords': { quality: 1,
    note: 'With 10 keywords supplied, every title variant crams all of them in ALL CAPS -- reads as keyword-stuffed spam, not a real YouTube title. Distinct, worse failure mode from the low-diversity issue affecting the other title cases.' },
};

const lines = readFileSync(resultsPath, 'utf8').split('\n').filter((l) => l.trim());
const patched = lines.map((line) => {
  const row = JSON.parse(line);
  const isTitle = row.tags?.includes('title');
  const override = OVERRIDES[row.prompt_id];
  const quality = override ? override.quality : (isTitle ? TITLE_QUALITY : DESCRIPTION_QUALITY);
  const note = override ? override.note : (isTitle ? TITLE_NOTE : DESCRIPTION_NOTE);
  row.grade.quality = quality;
  row.explanation.quality = note;
  return JSON.stringify(row);
});
writeFileSync(resultsPath, patched.join('\n') + '\n');
console.error(`patched quality onto ${patched.length} rows`);
