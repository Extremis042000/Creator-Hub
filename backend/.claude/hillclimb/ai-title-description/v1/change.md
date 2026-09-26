# v1: vary title structure + cap keyword usage per title

Skipping a separate analyzer pass -- Phase 35's baseline already named the
exact fix (this is the "next change is already clear" case per
eval-hillclimb.md Step 4).

## Evidence

**Low diversity across every case's 5-8 options** -- reordering the same
3-4 words rather than varying the angle. Verbatim from
`baseline/traces/title-tutorial-competitive-eldenring_rep0.json`:

> "Malenia No Damage Guide: Competitive Elden Ring Boss Strategy",
> "How to Beat Malenia With Zero Damage Taken in Elden Ring",
> "Malenia Boss Guide: No Damage Strategy for Competitive Players",
> "Elden Ring Malenia No Damage Run: Full Boss Fight Tutorial",
> "Malenia No Damage Tutorial: Competitive Elden Ring Boss Clear",
> "Zero Damage Malenia Guide: Elden Ring Competitive Boss Strategy",
> "Malenia Boss Fight No Damage: Competitive Elden Ring Walkthrough",
> "Elden Ring Malenia No Damage Strategy: Competitive Tutorial"

All 8 are "Malenia No Damage [Guide/Tutorial/Strategy]: [Competitive]
Elden Ring Boss [Strategy/Tutorial/Walkthrough]" permutations. Confirmed
this isn't a one-off: `baseline/traces/title-tutorial-competitive-eldenring_rep1.json`
shows the identical pattern on a second, independent call.

**Keyword-stuffing under many keywords is present but UNSTABLE, not
consistent** -- from `baseline/traces/title-edge-longtopic-maxkeywords_rep0.json`
(10 keywords supplied):

> "CS2 MIRAGE 1V5 1 TAP HEADSHOT CLUTCH RANKED FACEIT HIGHLIGHT MOMENT"

reads as spam -- every one of that rep's 8 titles crams most/all 10
keywords in ALL CAPS. But `.../title-edge-longtopic-maxkeywords_rep1.json`
on the exact same input did not stuff at all:

> "INSANE 1v5 Headshot Clutch on Mirage - Faceit Ranked Ace Highlight CS2"

The prompt currently gives no bound on how many of the supplied keywords
belong in one title, so behavior swings between the two reps.

## The change

Appended two sentences to `TitleGeneratorService.SYSTEM_PROMPT` (see
`system_prompt.txt` in this directory for the full candidate text):

1. Ask for a different **opening hook** per title (statement / question /
   how-to / bracketed qualifier / number), naming concrete hook types
   rather than a vague "be creative" -- targets the diversity finding.
2. Cap keyword usage explicitly at "3-4 most relevant per title" when
   more than 4 are supplied -- targets the stuffing-instability finding.

**Expected effect:** raise `quality` on cases currently at 2 (the
diversity issue) toward 3, and stabilize `title-edge-longtopic-maxkeywords`
at 2+ on both reps instead of swinging 1/2. **Expect no regression** on
`valid` or `distinct_from_template` -- neither instruction changes the
output JSON shape, length limits, or banned-claims handling.

## Off-limits respected

Description prompt untouched. Response JSON shape (`titles`/
`shortFormTitles` keys) unchanged. Banned-claims sentence unchanged.
Validation code (`TitleGeneratorService.java`) unchanged -- this is a
system-prompt-only change plus (per the founder's scope answer) a
provider-parameter experiment tried separately if this alone doesn't
move quality enough.
