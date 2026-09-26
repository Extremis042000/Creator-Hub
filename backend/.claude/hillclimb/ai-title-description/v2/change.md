# v2: same prompt as v1, raise max_tokens (provider-parameter lever)

## Evidence

v1 (identical system prompt, this directory reuses it verbatim) raised
quality from 1.958 to 3.0 on 21/24 successful attempts, but regressed
reliability: 3 attempts (`title-tutorial-competitive-eldenring` rep0+rep1,
`title-edge-longtopic-maxkeywords` rep0) failed with
`finish_reason=length` -- the gateway's hidden reasoning consumed the
entire 3000-token budget before emitting any visible content (see
`OpenAiCompatibleGenerationProvider.java`'s own documented failure mode:
"null/empty content when hidden reasoning eats the budget"). The more
detailed instruction set (hook variety + keyword capping) appears to
prompt more internal reasoning on some calls, tipping already-verbose
cases (Elden Ring's baseline output was already 1001 output tokens) over
the ceiling.

## The change

Provider parameter only, not prompt wording (system_prompt.txt in this
directory is byte-identical to v1's) -- raise `max_tokens` from 3000 to
4500 for the title call, giving the model room to finish hidden reasoning
AND the full JSON response. In scope per the founder's explicit "system
prompt + provider parameters" approval.

**Expected effect:** eliminate or reduce the 3 `finish_reason=length`
failures while holding quality at v1's 3.0 level (the parameter change
doesn't touch what the model is asked to produce, only how much room it
has to produce it).

## Off-limits respected

Same as v1 -- description prompt, JSON shape, banned-claims sentence,
validation code all untouched.
