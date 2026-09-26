# AI Model Optimization — Ideation & Phases

Status: **Phases 35-36 done and live-verified; Phases 37-39 proposed, not started.**
Founder asked to "train all my AI models to provide more optimized results."
This doc is honest about what that phrase can and can't mean given this
product's actual architecture, then plans the real, buildable version of it.

## 1. The honest starting point: we don't own the models

Every AI call this product makes today (Phases 26-29) goes through
`AiGenerationProvider` to one of two **free-tier gateway** backends:

| Slot | Gateway | Actual upstream model serving it |
|---|---|---|
| Primary | FreeModel (`alli.website`) | `inclusionai/ling-3.0-flash-sante:free` |
| Fallback | Free.ai | `Qwen/Qwen3-30B-A3B-Instruct` |

Neither is a model we hold weights for, host, or have any fine-tuning API
access to — they're free public inference endpoints on someone else's
infrastructure, picked because they cost $0. **"Training" in the literal
machine-learning sense (updating model weights) is not available for either.**
Claiming otherwise would be building on a premise that isn't true.

There is a real, different path to literal fine-tuning — a provider like
Together.ai supports LoRA fine-tuning an *open* model (e.g. Llama, Mistral,
Qwen) starting around $0.48 per 1M training tokens, with the fine-tuned model
then served from that same provider's paid inference — see §6. That's a real
option, just not a free one, and not where this doc recommends starting.

## 2. What "optimize the results" actually means here

Without owning the model, the levers that actually move output quality are:

1. **Prompt engineering** — the system/user prompt structure, the examples
   given, the constraints stated. This is the single highest-leverage,
   zero-cost lever available today, and it's currently minimal (Phase 27's
   prompts are a few sentences with no examples).
2. **Few-shot / in-context examples** — showing the model 2-3 real, ideal
   input→output pairs *inside the prompt itself*. This is the practical
   equivalent of "training" when the base model can't be touched — it steers
   behavior without any weight update, and costs nothing but prompt tokens.
3. **A real evaluation harness** — a fixed set of test inputs with a scoring
   method, run against every prompt change, so "optimized" is a measured
   claim, not a feeling. This project already has a bundled skill for exactly
   this (`claude-api`'s `build-eval` and `hillclimb` subcommands) — using it is
   the concrete mechanism for this phase, not something to build from scratch.
4. **A feedback loop from real usage** — signals already sitting in this
   product's own data (which generations get copied, shared, or refined vs.
   immediately regenerated) that can grow into a real preference dataset over
   time, entirely from first-party data this product already owns the rights
   to (no YouTube-ToS-style question here — see doc 10 §1 for why that
   question *does* apply elsewhere).
5. **Model choice itself.** `ClaudeGenerationProvider` already exists, built
   and tested, inert only because no `ANTHROPIC_API_KEY` has been supplied.
   A frontier model with the current prompts, unmodified, would likely already
   outperform the current free-tier gateway with heavily-engineered prompts —
   worth naming plainly rather than optimizing around a constraint that's a
   founder cost decision, not a technical one.

## 3. Master phases

| Phase | Task | Depends on | Complexity | Completion criteria |
|---|---|---|---|---|
| 35 | Build an eval set for Title/Description generation | Phase 27 | Medium | ✅ **Done** — `backend/.claude/hillclimb/ai-title-description/`. 24 real cases run against the real production prompts and the real `alli.website` gateway. Grading ended up two-layered, not the model-graded rubric originally sketched here: a deterministic `valid`/`distinct_from_template` pass (both 100%, 24/24) plus a `quality` (0-3) pass grounded in live YouTube search results — the founder's explicit choice over getting a paid Claude API key, since there wasn't one configured. See `QUALITY_PASS.md` alongside the eval for the real findings (descriptions score 3/3 across the board; titles score 2/3 — correct but low option-diversity; one max-keywords edge case scores 1/3 — keyword-stuffed). |
| 36 | Prompt/few-shot iteration against the eval | Phase 35 | Medium | ✅ **Done.** Two rounds via `hillclimb`, both real runs against the production gateway: **v1** (varied opening hook per title + keyword cap) hit quality's ceiling (3.0/3) but regressed reliability (12.5% `finish_reason=length` failures from heavier hidden reasoning); **v2** (same prompt + `max_tokens` 3000→4500, a provider-parameter fix) eliminated the failures while holding quality at ceiling. Shipped to `TitleGeneratorService.java` only — `DescriptionGeneratorService` was out of scope since Phase 35 found it already at ceiling (3/3). All 130 backend tests green. See `backend/.claude/hillclimb/ai-title-description/report.html` and `narrative.md`. |
| 37 | First-party usage-signal capture | Phase 28 (`ai_generation_log`) | Medium | Extend `ai_generation_log` (or a small companion table) to record a lightweight outcome signal per generation -- was it copied, refined further (Phase 29's `refine()`), or immediately regenerated -- as a proxy for "was this good." No new user-facing UI; this is instrumentation for phase 38's admin visibility and any future eval-set growth. |
| 38 *(optional, later)* | Admin visibility into eval scores + usage signals | Phase 36, 37 | Low | A small addition to the existing "AI usage today" admin card (Phase 28) or a new panel showing the latest eval score per tool and the copy/refine/regenerate signal breakdown -- so prompt changes are visible as a trend, not just felt. |
| 39 *(optional, only if the founder wants to spend money on this)* | Real fine-tuning evaluation | Phase 35-37, founder budget decision | High | Only revisit this if the eval scores plateau despite prompt iteration *and* the founder is willing to pay for it. Would mean: picking an open model (e.g. via Together.ai), building a training set from the accumulated first-party preference data (Phase 37), and comparing the fine-tuned model's eval score against the best prompt-engineered baseline before ever switching production traffic to it. Not started, not costed beyond the ballpark in §6, because there's no real signal yet that prompting has been exhausted. |

## 4. Why this order

Phases 35-36 (eval + prompt iteration) come first because they're free,
fast, and this project has literally never measured whether a prompt change
helped — every improvement so far has been "this looks more natural," not "this
scores higher on a fixed test." Phase 37 (usage signals) is instrumentation
that costs nothing to add now and compounds in value the longer it runs, so
it's worth starting even before there's a concrete use for the data. Phase 38
is a small UI nicety, deferred until there's actually a trend worth showing.
Phase 39 (real fine-tuning) is explicitly gated on both a technical signal
(prompting has plateaued) and a business decision (spending real money) that
don't exist yet — building it speculatively would be the same mistake as
"tool-augmented generation" in `07-ai-generation-initiative.md` §6's Phase 30,
which is deliberately unscoped until a concrete need appears.

## 5. What this does NOT include

**Fine-tuning on scraped or third-party data of unclear provenance.** If Phase
39 is ever reached, the training data must be either this product's own
first-party usage signals (Phase 37) or a dataset with a clear, checkable
license — never a scraped corpus assembled without checking the source's own
terms. This is the same discipline applied throughout this project (Slickdeals/
camelcamelcamel in Phase 18/25, and see doc 10 §1 for the YouTube-specific
version of this question for the thumbnail tool).

## 6. Real numbers, if Phase 39 is ever reached (not a commitment, just so a
future cost conversation starts from facts)

Per current public pricing research (2026): LoRA fine-tuning an open 7B-class
model via a provider like Together.ai runs roughly **$0.48 per 1M training
tokens** for the training run itself, plus that provider's own per-token
serving cost afterward (no more free-tier gateway once serving a custom
fine-tune — the founder would be paying for inference too, not just training).
A small, focused training set (a few hundred to a few thousand
prompt→ideal-output pairs) is a realistically low, but non-zero, ongoing cost —
this is the one part of this whole initiative that isn't free, and shouldn't
be started without the founder explicitly deciding to spend on it.

## 7. What's needed from the founder

Nothing to start Phase 35-37 — all buildable now, $0 cost, using tools already
in this environment. If/when Phase 39 is ever proposed: an explicit go-ahead to
spend real money on fine-tuning + serving, since nothing past that point is
free-tier.
