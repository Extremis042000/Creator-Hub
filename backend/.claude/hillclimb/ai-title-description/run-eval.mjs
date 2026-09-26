#!/usr/bin/env node
// Phase 35 eval runner + Phase 36 hillclimb harness for Title/Description
// AI generation (docs/decisions/09-ai-model-optimization.md).
//
// Calls the REAL production entry point: the exact system prompts from
// TitleGeneratorService.SYSTEM_PROMPT / DescriptionGeneratorService.SYSTEM_PROMPT
// (baseline copied verbatim; a variant's own system_prompt.txt overrides it)
// against the REAL OpenAiCompatibleGenerationProvider call shape (POST
// {chatUrl}, Bearer apiKey, {model, max_tokens, messages}) -- see
// backend/src/main/java/com/extremis/hub/ai/OpenAiCompatibleGenerationProvider.java.
//
// Grading here is deterministic only (ported from the exact runtime
// validation rules). The YouTube-grounded `quality` pass is a separate,
// manual step (apply-quality-pass.mjs) -- see QUALITY_PASS.md.
//
//   node run-eval.mjs --variant baseline --reps 2 --tool title
//   node run-eval.mjs --variant v1 --reps 2 --tool title

import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// --- real credentials, same ones already live on Render (Phase 26) --------
const AI_API_CHAT_URL = process.env.AI_API_CHAT_URL || 'https://alli.website/v1/chat/completions';
const AI_API_KEY = process.env.AI_API_KEY || 'sk-fm-da1e621ea39a92149c7363513a7cd6618b4dfccb4370ddf2';
const AI_MODEL = process.env.AI_MODEL || 'fm-v1-lite';
const AI_MAX_OUTPUT_TOKENS = 3000;

// --- exact system prompts, copied verbatim from the Java source ------------
const BASELINE_TITLE_SYSTEM_PROMPT = 'You write YouTube titles for gaming content creators. Reply with ONLY a JSON object, no markdown formatting, no commentary: {"titles": [5 to 8 long-form titles, each 100 characters or fewer], "shortFormTitles": [3 to 5 short titles, each 40 characters or fewer]}. Never use unverifiable absolute claims like "world record", "best ever", "#1 in the world", or "greatest of all time".';

const DESCRIPTION_SYSTEM_PROMPT = 'You write YouTube video descriptions for gaming content creators. Reply with ONLY a JSON object, no markdown formatting, no commentary: {"description": "a friendly, SEO-aware description, 5000 characters or fewer, that naturally weaves in the channel name and any social links given", "seoKeywordsSection": "a short comma-separated keyword list", "hashtags": ["1 to 15 hashtags, each starting with #, no spaces"]}. Never use unverifiable absolute claims like "world record", "best ever", "#1 in the world", or "greatest of all time".';

const BANNED_PHRASES = ['WORLD RECORD', '#1 IN THE WORLD', 'BEST EVER', 'GREATEST OF ALL TIME'];
const YOUTUBE_TITLE_MAX_LENGTH = 100;
const SHORT_FORM_TITLE_MAX_LENGTH = 40;
const MAX_DESCRIPTION_LENGTH = 5000;
const MAX_HASHTAGS = 15;

function containsBannedClaim(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  return BANNED_PHRASES.some((p) => upper.includes(p));
}

function stripCodeFences(text) {
  return text.replace(/^\s*```(?:json)?/, '').replace(/```\s*$/, '').trim();
}

function buildTitleUserPrompt(input) {
  const keywords = input.keywords ?? [];
  return `Game: ${input.game}\nTopic: ${input.topic}\nVideo type: ${input.videoType}\nTone: ${input.tone}\nKeywords: ${keywords.length ? keywords.join(', ') : '(none)'}`;
}

function validateTitleList(titles, maxLength) {
  if (!Array.isArray(titles)) return [];
  const valid = [];
  const seen = new Set();
  for (const t of titles) {
    if (typeof t !== 'string') continue;
    const trimmed = t.trim();
    if (!trimmed || trimmed.length > maxLength) continue;
    if (containsBannedClaim(trimmed)) continue;
    if (seen.has(trimmed)) continue;
    seen.add(trimmed);
    valid.push(trimmed);
  }
  return valid;
}

function gradeTitleOutput(cleaned) {
  const problems = [];
  let parsed;
  try { parsed = JSON.parse(cleaned); } catch (e) {
    return { valid: 0, problems: [`JSON parse failed: ${e.message}`], titles: [], shortFormTitles: [] };
  }
  const titles = validateTitleList(parsed.titles, YOUTUBE_TITLE_MAX_LENGTH);
  const shortFormTitles = validateTitleList(parsed.shortFormTitles, SHORT_FORM_TITLE_MAX_LENGTH);
  if (titles.length < 3) problems.push(`only ${titles.length} valid long-form titles (need >= 3)`);
  if (shortFormTitles.length === 0) problems.push('no valid short-form titles');
  const rawBanned = (parsed.titles ?? []).concat(parsed.shortFormTitles ?? []).filter((t) => typeof t === 'string' && containsBannedClaim(t));
  if (rawBanned.length) problems.push(`model produced ${rawBanned.length} banned-claim title(s), dropped by validation`);
  const valid = (titles.length >= 3 && shortFormTitles.length > 0) ? 1 : 0;
  return { valid, problems, titles, shortFormTitles };
}

function buildDescriptionUserPrompt(input) {
  const keywords = input.keywords ?? [];
  const socialLinks = input.socialLinks ?? [];
  let prompt = `Game: ${input.game}\nTopic: ${input.topic}\nChannel name: ${input.channelName}\nKeywords: ${keywords.length ? keywords.join(', ') : '(none)'}\n`;
  if (socialLinks.length) {
    prompt += 'Social links:\n';
    for (const link of socialLinks) prompt += `${link.platform}: ${link.url}\n`;
  }
  return prompt;
}

function validateHashtagList(hashtags) {
  if (!Array.isArray(hashtags)) return [];
  const valid = [];
  const seen = new Set();
  for (const tag of hashtags) {
    if (typeof tag !== 'string') continue;
    const trimmed = tag.trim();
    if (!trimmed.startsWith('#') || trimmed.includes(' ') || trimmed.length <= 1) continue;
    if (seen.has(trimmed)) continue;
    seen.add(trimmed);
    valid.push(trimmed);
  }
  return valid.length > MAX_HASHTAGS ? valid.slice(0, MAX_HASHTAGS) : valid;
}

function gradeDescriptionOutput(cleaned) {
  const problems = [];
  let parsed;
  try { parsed = JSON.parse(cleaned); } catch (e) {
    return { valid: 0, problems: [`JSON parse failed: ${e.message}`], description: null, hashtags: [] };
  }
  const description = typeof parsed.description === 'string' ? parsed.description.trim() : null;
  const hashtags = validateHashtagList(parsed.hashtags);
  let descOk = !!description && description.length > 0 && description.length <= MAX_DESCRIPTION_LENGTH;
  if (descOk && containsBannedClaim(description)) { descOk = false; problems.push('description contains a banned absolute claim'); }
  if (!description) problems.push('missing/empty description');
  else if (description.length > MAX_DESCRIPTION_LENGTH) problems.push(`description exceeds ${MAX_DESCRIPTION_LENGTH} chars`);
  if (hashtags.length === 0) problems.push('no valid hashtags');
  const valid = descOk && hashtags.length > 0 ? 1 : 0;
  return { valid, problems, description, hashtags };
}

const TONE_WORDS = { HYPE: 'INSANE', CASUAL: 'Chill', COMPETITIVE: 'Ranked', FUNNY: 'Hilarious' };
function templateTitles(input) {
  const toneWord = TONE_WORDS[input.tone];
  return [`${input.game} ${input.topic} (${toneWord})`, `${input.topic} in ${input.game} — ${toneWord}`];
}

async function callGateway(systemPrompt, userPrompt, maxTokens) {
  const body = { model: AI_MODEL, max_tokens: maxTokens,
    messages: [{ role: 'system', content: systemPrompt }, { role: 'user', content: userPrompt }] };
  const res = await fetch(AI_API_CHAT_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${AI_API_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) { const e = new Error(`AI gateway returned HTTP ${res.status}`); e.status = res.status; throw e; }
  const json = await res.json();
  const choice = json.choices?.[0];
  if (!choice?.message) throw new Error('AI gateway returned no choices.');
  let text = (choice.message.content ?? '').replace(/<think>[\s\S]*?<\/think>/g, '').trim();
  if (!text) throw new Error(`AI gateway returned no text (finish_reason=${choice.finish_reason}).`);
  return { text, servedModel: json.model, inputTokens: json.usage?.prompt_tokens ?? 0, outputTokens: json.usage?.completion_tokens ?? 0 };
}

async function runCase(c, systemPrompts, maxTokens) {
  const systemPrompt = c.tool === 'title' ? systemPrompts.title : systemPrompts.description;
  const userPrompt = c.tool === 'title' ? buildTitleUserPrompt(c.input) : buildDescriptionUserPrompt(c.input);
  const result = await callGateway(systemPrompt, userPrompt, maxTokens);
  const cleaned = stripCodeFences(result.text);
  return {
    output: cleaned,
    transcript: [
      { role: 'system', content: systemPrompt },
      { role: 'user', content: userPrompt },
      { role: 'assistant', content: result.text },
    ],
    model: result.servedModel, usage: { input_tokens: result.inputTokens, output_tokens: result.outputTokens },
    stop_reason: 'end_turn',
  };
}

function gradeCase(c, run) {
  const g = c.tool === 'title' ? gradeTitleOutput(run.output) : gradeDescriptionOutput(run.output);
  const template = c.tool === 'title' ? templateTitles(c.input) : [];
  const distinct = c.tool === 'title'
    ? (g.titles.length > 0 && !g.titles.some((t) => template.includes(t)) ? 1 : 0)
    : 1;
  return {
    grade: { valid: g.valid, distinct_from_template: distinct },
    explanation: { valid: g.problems.length ? g.problems.join('; ') : 'passed all runtime validation checks' },
    parsed: g,
  };
}

function parseArgs(argv) {
  const a = { variant: 'baseline', reps: 1, tool: 'all', maxTokens: AI_MAX_OUTPUT_TOKENS };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--variant') a.variant = argv[++i];
    else if (k === '--reps') a.reps = +argv[++i];
    else if (k === '--tool') a.tool = argv[++i];
    else if (k === '--max-tokens') a.maxTokens = +argv[++i];
  }
  return a;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const flow = '.';
  const vdir = join(flow, args.variant);
  mkdirSync(join(vdir, 'traces'), { recursive: true });
  let cases = JSON.parse(readFileSync(join(flow, 'cases.json'), 'utf8'));
  if (args.tool !== 'all') cases = cases.filter((c) => c.tool === args.tool);

  // System prompt: baseline uses the hardcoded verbatim copies; any other
  // variant reads its own system_prompt.txt (the thing being iterated on).
  const systemPrompts = { description: DESCRIPTION_SYSTEM_PROMPT, title: BASELINE_TITLE_SYSTEM_PROMPT };
  const variantPromptPath = join(vdir, 'system_prompt.txt');
  if (args.variant !== 'baseline') {
    if (!existsSync(variantPromptPath)) {
      console.error(`[FATAL] ${variantPromptPath} not found -- write the candidate title system prompt there before running a non-baseline variant.`);
      process.exit(2);
    }
    systemPrompts.title = readFileSync(variantPromptPath, 'utf8').trim();
  }

  const resultsPath = join(vdir, 'results.jsonl');
  const errorsPath = join(vdir, 'errors.jsonl');
  const done = new Set();
  if (existsSync(resultsPath))
    for (const ln of readFileSync(resultsPath, 'utf8').split('\n')) {
      if (!ln.trim()) continue;
      try { const r = JSON.parse(ln); done.add(`${r.prompt_id}\u0000${r.rep}`); } catch {}
    }

  let ok = 0, fail = 0;
  for (const c of cases) {
    for (let rep = 0; rep < args.reps; rep++) {
      const key = `${c.id}\u0000${rep}`;
      if (done.has(key)) { console.error(`[skip] ${c.id} rep${rep} already done`); continue; }
      const t0 = Date.now();
      try {
        const run = await runCase(c, systemPrompts, args.maxTokens);
        const { grade, explanation } = gradeCase(c, run);
        const latency_s = (Date.now() - t0) / 1000;
        const row = {
          prompt_id: c.id, rep, prompt: JSON.stringify(c.input), tags: c.tags,
          model: run.model, usage: run.usage, stop_reason: run.stop_reason,
          latency_s, grade, explanation,
        };
        appendFileSync(resultsPath, JSON.stringify(row) + '\n');
        writeFileSync(join(vdir, 'traces', `${c.id}_rep${rep}.json`), JSON.stringify(run.transcript, null, 2));
        ok++;
        console.error(`[ok] ${c.id} rep${rep} valid=${grade.valid} distinct=${grade.distinct_from_template} served_by=${run.model} (${latency_s.toFixed(1)}s)`);
      } catch (e) {
        fail++;
        appendFileSync(errorsPath, JSON.stringify({ prompt_id: c.id, rep, failure_class: e.status ? 'http_error' : 'error', error: String(e.message || e), latency_s: (Date.now() - t0) / 1000 }) + '\n');
        console.error(`[FAIL] ${c.id} rep${rep}: ${e.message || e}`);
      }
    }
  }
  console.error(`[${args.variant}] done: ${ok} ok, ${fail} failed -> ${resultsPath}`);
}

main();
