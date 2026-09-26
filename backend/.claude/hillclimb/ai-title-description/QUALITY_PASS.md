# Quality pass — how it was done, and what it found

Per the founder's explicit choice for Phase 35 grading (no Claude API key
configured, so no automated Claude-judge; not a pure programmatic rubric
either): ground the qualitative score in real, live YouTube search results
rather than an abstract rubric or a second model's opinion.

## What this pass actually did

1. `run-eval.mjs` calls the real production system prompts against the real
   `alli.website` gateway (same credentials already live on Render) for all
   24 synthesized cases — this part is a plain, re-runnable Node script.
2. An agent (Claude Code, with `WebSearch`) read all 24 real trace outputs
   and ran 3 live searches against `youtube.com` for representative
   game/topic pairs already in the case set (Valorant clutch aces, Elden
   Ring Malenia no-hit guides, Fall Guys fails compilations) to see what
   real, currently-ranking video titles/descriptions for the same content
   actually look like.
3. `apply-quality-pass.mjs` then patches a `quality` (0-3) score onto every
   row, using a consistent rubric grounded in step 2's real comparisons
   rather than per-case web lookups (24 individual searches wasn't
   necessary once the pattern was clear and consistent across cases).

**This is not automatable end-to-end.** Re-running the quality dimension
on a future prompt change needs an agent session with web search, not just
`node apply-quality-pass.mjs` — that script currently just re-applies the
same September 2026 findings below. If a future prompt change is meant to
fix the diversity/keyword-stuffing issues found here, the honest way to
re-score it is a fresh agent pass, not re-running this file verbatim.
`valid` and `distinct_from_template`, by contrast, are fully automated and
re-run for free on every `run-eval.mjs` invocation.

## Real findings (2026-09-26)

**Descriptions: strong, quality=3 on 12/12.** Natural tone, correctly
weaves in the channel name and every social link supplied, genre-
appropriate hashtags and emoji use (compare `#StardewValleyTips
#CozyGaming` for a farming-sim channel vs `#ApexTips #Predator` for a
competitive shooter one), always includes a call-to-action. Reads as
competitive with real YouTube gaming descriptions in every case reviewed.

**Titles: correct but low-diversity, quality=2 on 11/12.** Real comparable
titles researched via search:
- Valorant: "A 4-Second Phantom Ace & an Impossible 1v5 Clutch", "INSANE
  ACE CLUTCH | VALORANT MONTAGE #HIGHLIGHTS Ep.298"
- Elden Ring: "Malenia Boss Fight (No Damage & No Parry) [Elden Ring]",
  "No Hit Level 1 Malenia (Melee Only)"
- Fall Guys: "TOP 200 FUNNIEST FAILS IN FALL GUYS", "Fall Guys Coffin
  Dance! Funny Fails Compilation!"

Real titles vary the *angle* between options — a numbered-list hook, a
parenthetical qualifier ("Melee Only", "No Parry"), a meme reference. Our
prompt asks for "5 to 8 long-form titles" but gives no instruction to vary
the angle, so the model reorders the same 3-4 words per case instead —
e.g. the Elden Ring case's 8 titles are all "Malenia No Damage [Guide/
Tutorial/Strategy]: [Competitive] Elden Ring Boss [Strategy/Tutorial/
Walkthrough]" permutations. Concrete, actionable prompt gap for Phase 36:
ask explicitly for varied hooks/angles across the option set, and consider
a numbered-list variant as one of the 5-8.

**One distinctly worse case, quality=1:**
`title-edge-longtopic-maxkeywords` (10 keywords supplied) crammed every
keyword into ALL CAPS in every title variant — e.g. "CS2 MIRAGE 1V5 1 TAP
HEADSHOT CLUTCH RANKED FACEIT HIGHLIGHT MOMENT" — which reads as
keyword-stuffed spam, unlike any real title found. A prompt fix should
cap how many supplied keywords the model is expected to actually use per
title, not just cap the keyword list's own length (already 10 max at the
request-validation layer).

## Sources checked

- https://www.youtube.com/watch?v=YsI7DwCYVBQ (Valorant)
- https://www.youtube.com/watch?v=7XWa842STWg (Valorant)
- https://www.youtube.com/watch?v=JkA6Ai4ZO50 (Elden Ring)
- https://www.youtube.com/watch?v=j1mPqg4CKUQ (Elden Ring)
- https://www.youtube.com/watch?v=43k0adhEWG8 (Fall Guys)
- https://www.youtube.com/watch?v=ZxLI7mkTkXo (Fall Guys)

Browser/search only — no YouTube Data API calls, nothing cached or stored
beyond this doc's own summary of what was found. Consistent with
`docs/decisions/10-ai-thumbnail-generator.md` §1's reasoning: this is
ephemeral, one-time use for grading context, not a persisted derived
dataset built from API Data.
