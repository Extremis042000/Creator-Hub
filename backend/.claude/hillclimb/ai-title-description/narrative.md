| round | change (one line) | quality | valid | notes |
|---|---|---|---|---|
| 0 (baseline) | — | 1.958 / 3 | 24/24 (100%) | 23/24 samples scored 2, one (max-keywords edge case) scored 1 |
| 1 | vary title hooks + cap keyword usage at 3-4 | 3.0 / 3 (21 successful) | 21/24 (87.5%) | 3 attempts hit `finish_reason=length` -- hidden reasoning ate the token budget |
| 2 (winner) | same prompt + `max_tokens` 3000→4500 | **3.0 / 3** | **24/24 (100%)** | Failure mode eliminated, quality held at ceiling, both guardrails clean |

**Winner: v2.** Round 1's prompt change (ask for a distinct opening hook
per title -- statement/question/how-to/bracketed-qualifier/number -- and
cap supplied-keyword usage at 3-4 per title) produced a real, substantial
quality jump (1.958 -> 3.0, the rubric's ceiling) on every case it
succeeded on, confirmed by spot-checking 6 real outputs against the same
YouTube search grounding used for the Phase 35 baseline. But it also
introduced a real reliability regression: 3 of 24 attempts (12.5%,
disproportionately the already-verbose Elden Ring case and the
max-keywords edge case) hit the model's token ceiling before emitting any
visible text, because the added instructions increased how much the
free-tier model reasons internally before answering.

Round 2 kept the exact same prompt and only raised `AI_MAX_OUTPUT_TOKENS`
from 3000 to 4500 (the founder's pre-approved "provider parameters" scope)
-- a provider-parameter fix targeting the specific failure mechanism
rather than walking back the prompt change. It fully eliminated the
failures (24/24 succeeded) while holding quality at the same ceiling,
including on both reps of the previously-unstable max-keywords case.

**Why trust this:** every round ran the real production prompts against
the real `alli.website` gateway, at 2 reps per case; the quality dimension
is grounded in the same live YouTube search comparisons as the Phase 35
baseline, not a self-reported number; and the fix for v1's regression was
tied to a specific, named mechanism (hidden-reasoning token consumption),
not a blind retry.

**What else was tried:** nothing else -- the goal (raise title quality)
hit its rubric's ceiling in round 2 with zero guardrail regressions, so
there was no further headroom to chase on this metric. If a future round
wants more signal, the next lever with headroom would be a finer-grained
quality rubric (the current 0-3 scale can't distinguish "good" from
"excellent" once everything clears 3) or moving the judge to a real
Claude model if the founder ever provisions an API key.

**Status: stopped, not paused.** Shipped to `TitleGeneratorService.java`
(both the prompt and the `max_tokens` change); all 130 backend tests
green.
