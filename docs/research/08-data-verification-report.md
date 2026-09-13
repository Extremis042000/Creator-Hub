# EXTREMIS Creator Hub — Data Verification Report for Founder Approval

Status: **ACTIVATION DECIDED (2026-09-13), per founder direction — see
§8.** The rows listed as ACTIVATED in §8 are approved for
`V3__seed_sensitivity_profiles.sql` / `V4__seed_bgmi_profiles.sql`.
Nothing outside §8's explicit ACTIVATED list may be set to `active =
true`. No `sensitivity_game_profile` or `bgmi_sensitivity_profile`
row may be set to `active = true` (or, for BGMI, treated as a shipped
recommendation) until the founder explicitly approves specific rows
from this report. This report is the evidence gate the PRD and Phase 2
docs already require before `V3__seed_sensitivity_profiles.sql` and
`V4__seed_bgmi_profiles.sql` can be written.

Research method used (per the required methodology): web search to
find candidate constants → direct page fetches (not just search-result
summaries) to confirm what a source actually states on-page → cross-
checked against a second, independently-run site → methodology and
confidence assessed → everything below stays DRAFT/INACTIVE pending
founder sign-off.

**DEEP VERIFICATION PASS 2 — activation decision (2026-09-13):** at
the founder's direction ("run deep search... and do it"), a second
research pass was run specifically to close the activation question,
and activation decisions were then made against the confidence
findings below — see §8 (new) for the full result. Summary: 4 of 5
sensitivity games activated, 1 (Overwatch 2) deliberately held back;
all 10 BGMI `(gyroscopeUsage, scopeLevel)` rows activated, each at its
honestly-assessed confidence (`MEDIUM` or `LOW` — no row is
fabricated to look better than its evidence).

**Founder decision recorded (post-review):** based on §5/§6 below, the
founder approved replacing the BGMI `deviceCategory × playStyle` model
with `gyroscopeUsage × scopeLevel`. The final schema — enums
`GyroscopeUsage {GYRO_ON, GYRO_OFF}` and `ScopeLevel {NO_SCOPE,
RED_DOT, SCOPE_3X, SCOPE_4X_ACOG, SNIPER_SCOPE}`, plus
`recommendedMin`/`recommendedMax`/`recommendedStartingValue`/
`confidence`/`sourceReference` fields — is implemented in
`07-phase2-system-design.md` §1.4 and mirrored in `06-prd.md` §5.3.
§5/§6 below are left as originally researched (they use descriptive
axis names like "no-scope/red-dot" rather than the final enum tokens)
since the evidence itself hasn't changed — only the schema it's
poured into has.

---

## 1. Executive Summary

- **Research completed:** targeted web research on 5 games'
  sensitivity-conversion constants, plus BGMI community sensitivity
  recommendations. 6 WebSearch queries and 8 direct WebFetch page
  reads were performed and are cited by URL throughout.
- **Games researched:** Valorant, CS:GO, CS2, Overwatch 2, Apex
  Legends.
- **Games with a verifiable-in-game constant:** CS:GO and CS2 (the
  `m_yaw` console variable — a real, user-checkable game setting, not
  merely a claimed number). This is the strongest evidence category
  found.
- **Games with only community-consensus (non-official, non-
  verifiable-in-game) constants:** Valorant, Overwatch 2, Apex
  Legends. Numbers are widely repeated but not officially documented
  and not independently checkable by an end user the way `m_yaw` is.
- **BGMI profiles supported by evidence:** **zero** of the original
  12 `(deviceCategory × playStyle)` combinations are supported by
  cited, verifiable evidence. Available sources support broad
  *ranges* segmented by different axes than the current schema (see
  §5, §6).
- **Conflicting sources found:** one automated search-result synthesis
  claimed "Valorant's yaw is inherited from Overwatch," which is
  inconsistent with the same synthesis's own numbers (Valorant 0.07 vs.
  Overwatch 0.0066 — different values, so "inherited" cannot be
  literally true). This claim did **not** appear on any directly-
  fetched primary page during this research and is flagged as likely
  an artifact of automated search summarization rather than a real
  source claim — see §6.
- **Bottom line:** nothing here is ready to activate on its own
  say-so. CS:GO/CS2's constant is the closest to production-ready
  because it's empirically checkable; the rest need either an
  official/primary source or the founder's explicit risk-accepting
  sign-off on community-consensus data before going live.

---

## 2. Sensitivity Conversion Constants

**Shared methodology (applies to every row below):** a game's "yaw"
is the number of degrees the in-game camera rotates per raw mouse
count, at sensitivity = 1.0. Given that, the physical distance the
mouse must travel for a full 360° turn is:

```
cm/360 = (dpi × sensitivity × yaw)⁻¹ × 2.54 × 360
       = 914.4 / (dpi × sensitivity × yaw)
```

To convert a sensitivity from a source game to a target game **at the
same DPI**, the physical distance (`cm/360`) is held constant, which
algebraically reduces to:

```
targetSensitivity = sourceSensitivity × (sourceYaw / targetYaw)
```

This is the formula already specified in `07-phase2-system-design.md`
§1.3/§3 and `06-prd.md` §5.2 — this report supplies the actual
candidate `yaw` values and their evidence, it does not change the
formula.

| Game | Candidate Yaw Constant | Formula/Methodology | Primary Source | Secondary Verification | Confidence |
|---|---:|---|---|---|---|
| CS:GO | 0.022 | `m_yaw` console variable; degrees/count = sensitivity × `m_yaw`; cm/360 formula above | [csdb.gg — m_yaw command](https://csdb.gg/command/m-yaw/) (direct fetch: confirms default `0.022`, explicitly notes it is third-party documentation, "not affiliated with Valve") | [totalcsgo.com m_yaw page](https://totalcsgo.com/commands/myaw); Steam Community discussions on `m_yaw`/`m_pitch` defaults; **empirically verifiable by any user** typing `m_yaw` in the in-game developer console | **HIGH** — real, exposed, user-checkable console variable; every source found agrees on `0.022`; this is not a claimed number, it's a live setting anyone with the game can query themselves |
| CS2 | 0.022 (same cvar, Source 2 engine retains the Source default) | Same as CS:GO | [csdb.gg — CS2-specific m_yaw page](https://csdb.gg/command/m-yaw/) (direct fetch) | Steam Community CS2 discussions confirming the cvar's continued existence and default | **HIGH** — same reasoning as CS:GO; independently confirmed CS2 still exposes and defaults this cvar identically |
| Valorant | 0.07 | Same cm/360 formula; `targetSens = sourceSens × (sourceYaw/targetYaw)` | [xp-feed.com Valorant↔CS2 guide](https://www.xp-feed.com/en/guides/sensibilidad-cm360-conversor-valorant-cs2) (direct fetch: explicitly states "Valorant uses a yaw of 0.07," shows the ratio math and a worked example) | [dcprosens.com](https://dcprosens.com/) (direct fetch, independently run site, states the identical `0.07` figure and frames it as "Valorant's real yaw constant"); also repeated (search-level only, not independently fetched) by [aimhub.gg](https://aimhub.gg/en/sensitivity-calculator/valorant/), [3daimtrainer.com](https://www.3daimtrainer.com/mouse-sensitivity-converter/valorant/), [senslab.pro](https://senslab.pro/sensitivity), [mousetester.io](https://mousetester.io/sensitivity/valorant/) | **MEDIUM** — two independently-run sites confirmed the exact same number and formula on direct fetch, and several more repeat it; but there is no official Riot documentation, no accessible in-game console to verify it directly, and multiple repeating sites likely trace back to one original community-derived source rather than N independent derivations |
| Overwatch 2 | 0.0066 | Same cm/360 formula | [dcprosens.com](https://dcprosens.com/) (direct fetch: states "Overwatch 2: yaw 0.0066") | Search-level corroboration only from [senslab.pro](https://senslab.pro/sensitivity/overwatch2) ("Overwatch 2's verified yaw constant is 0.0066"); **direct WebFetch of two dedicated OW2 pages — [mouse-sensitivity.com/n/overwatch](https://www.mouse-sensitivity.com/n/overwatch/) and [flank.gg's OW2 converter](https://www.flank.gg/mouse-sensitivity-converter/overwatch-2) — did NOT state this number on-page** | **LOW-MEDIUM** — the number is repeated, but two direct-fetch attempts on dedicated OW2 sensitivity pages failed to corroborate it on-page; weaker showing than Valorant despite similar evidence category |
| Apex Legends | 0.022 (claimed identical to CS2, via shared Source-engine lineage) | Same cm/360 formula; rationale is Respawn's Source-engine fork retaining the default input-handling constant | [senslab.pro/sensitivity/apex](https://senslab.pro/sensitivity/apex) (direct fetch: states "Apex Legends turns your mouse by a fixed yaw constant (0.022) per count," but does **not** cite methodology or source for the number itself) | Search-level corroboration from [sensiconverter.com](https://sensiconverter.com/cs2-to-apex-legends/) and [sensconverterfree.com](https://sensconverterfree.com/mouse-sensitivity-converter/apex-legends) stating CS2↔Apex conversion is 1:1 | **MEDIUM** — plausible, consistently-repeated engineering explanation, but Apex's retail client has no accessible developer console for an end user to verify the constant directly, unlike CS:GO/CS2 |

**Per-value explanation (required detail):**

1. **CS:GO/CS2 (0.022):** Directly documented as a live, queryable
   console variable default — the strongest evidence type available
   for this domain. Not mathematically inferred; it's a setting.
2. **Valorant (0.07):** Mathematically/empirically inferred by the
   community (no official Riot source), but two independently-run
   sites state the identical number with a consistent formula, and
   several more repeat it without contradiction.
3. **Overwatch 2 (0.0066):** Same inferred-by-community category as
   Valorant, but with weaker direct-fetch corroboration in this
   research pass — flagged lower confidence for that reason, not
   because a conflicting number was found.
4. **Apex Legends (0.022):** Inferred via engine lineage (a
   real, documented fact — Apex runs on a Respawn fork of the Source
   engine) rather than a direct measurement citation; consistent
   across sources, but unverifiable in-game.

**Conflicting references:** see the Valorant/Overwatch "inheritance"
claim noted in §1 and detailed in §6 — not a numeric conflict between
primary sources, but a red flag about trusting auto-summarized search
output without a direct-fetch check (which is why every number in the
table above was confirmed via direct page fetch, not search-snippet
synthesis alone).

---

## 3. Conversion Examples

Worked examples using the candidate constants above (DRAFT — not yet
approved for production use). All use the formula from §2.

### CS:GO → Valorant

- Input: 2.0 sensitivity, 800 DPI, CS:GO (yaw 0.022)
- `targetSens = 2.0 × (0.022 / 0.07) = 2.0 × 0.314286 = 0.628571`
- Expected output (rounded to 4 decimals per PRD §5.2): **0.6286**
- `cm/360` check: `914.4 / (800 × 2.0 × 0.022) = 25.9773 cm`, and
  `914.4 / (800 × 0.628571 × 0.07) = 25.9773 cm` — physical distance
  preserved, confirming internal consistency of the formula.
- Source/reference comparison: matches the worked example pattern
  shown on xp-feed.com and dcprosens.com (same ratio, same direction).
- Allowed tolerance: ±0.001 on the 4-decimal output (see §4 for why).

### CS2 → Valorant

- Same as CS:GO → Valorant, since the candidate CS2 yaw (0.022)
  equals the candidate CS:GO yaw. Input 2.0 sensitivity, 800 DPI →
  **0.6286** target sensitivity, same tolerance ±0.001.

### Valorant → CS2

- Input: 1.0 sensitivity, 800 DPI, Valorant (yaw 0.07)
- `targetSens = 1.0 × (0.07 / 0.022) = 3.181818`
- Expected output (rounded to 4 decimals): **3.1818**
- `cm/360` check: `914.4 / (800 × 1.0 × 0.07) = 16.3286 cm`, and
  `914.4 / (800 × 3.181818 × 0.022) = 16.3286 cm` — consistent.
- Source/reference comparison: matches xp-feed.com's explicit
  "multiply your Valorant sens by 3.18" statement (0.02% difference
  is rounding in their published 2-decimal figure vs. this report's
  4-decimal figure).
- Allowed tolerance: ±0.001.

---

## 4. Round-Trip Validation

`A → B → A`, using 4-decimal-place rounding at each step (matching
the PRD's specified output precision) to surface realistic
floating-point/rounding drift rather than an idealized exact-algebra
result.

| Case | Original | Intermediate (rounded) | Final (rounded) | Difference | Tolerance | Result |
|---|---:|---:|---:|---:|---:|---|
| CS:GO 2.0 → Valorant → CS:GO | 2.0000 | 0.6286 | 2.0003 | 0.0003 | ±0.001 | PASS |
| Valorant 1.0 → CS2 → Valorant | 1.0000 | 3.1818 | 1.0000 | ~0.000006 | ±0.001 | PASS |
| CS2 0.5 → Valorant → CS2 | 0.5000 | 0.1571 | 0.5003 | 0.0003 | ±0.001 | PASS |

**Why the tolerance is ±0.001, not exact-zero:** the forward and
inverse ratios (`sourceYaw/targetYaw` and its reciprocal) are exact
inverses algebraically, so a round trip is mathematically identity —
any nonzero difference comes entirely from rounding the intermediate
result to 4 decimal places before converting back (as the real
service will do, since it persists/display the intermediate value).
±0.001 comfortably covers the drift observed above across both
conversion directions and multiple magnitudes; Phase 4's automated
test suite should assert this exact tolerance (§7) rather than
exact equality, and should fail loudly if drift ever exceeds it
(which would indicate a rounding-order bug, not expected float error).

---

## 5. BGMI Recommendation Research

**Governing distinction (per founder instruction):** sensitivity
*conversion* constants above have an objective mathematical basis
(preserving physical mouse-turn distance). BGMI sensitivity
*recommendations* do not — they are inherently shaped by device
performance, screen size, refresh rate, gyroscope use, play style, and
personal comfort. Nothing in this section is presented as "the
correct settings," and the tool's response must keep using the
founder-specified disclaimer wording: *"These are recommended
starting-point settings based on your device category and play
style. Adjust them to your personal comfort and device performance."*

**Sources reviewed:**
- [BlueStacks BGMI pro sensitivity guide](https://www.bluestacks.com/blog/game-guides/battlegrounds-mobile-india/bgmi-best-pro-sensitivity-settings-en.html)
- [RaidGG BGMI sensitivity guide](https://raidgg.com/game-guides/bgmi/bgmi-best-sensitivity-settings/)
- [Sportskeeda — BGMI gyroscope settings](https://www.sportskeeda.com/bgmi/best-bgmi-sensitivity-settings-gyroscope-revealed)
- [Smartprix — BGMI sensitivity guide](https://www.smartprix.com/bytes/mastering-bgmi-sensitivity-settings-optimal-camera-ads-and-gyroscope-configuration-for-battlegrounds-mobile-india/)
- [Cashify — BGMI sensitivity codes](https://www.cashify.in/best-bgmi-sensitivity-settings-sensitivity-code)
- [TechPlayForge — BGMI 2026 sensitivity guide](https://techplayforge.com/best-bgmi-sensitivity-settings-2026/)

**What the evidence actually supports:**
- Roughly consistent claim across sources: **~90% of competitive BGMI
  players use gyroscope** — gyroscope-on vs. gyroscope-off is a real,
  repeatedly-cited behavioral split.
- Gyroscope sensitivity recommendations cluster by **scope/zoom
  level**, not by device category or play style: no-scope/red-dot
  commonly cited around 300-400%, tapering down for higher
  magnification (3x cited ~170-250%, 4x ACOG ~180-230%).
- Camera/ADS sensitivity numbers **vary substantially between
  sources** for what should be the same recommendation — e.g. one
  source's "for most players" camera figure (130-150%, TPP) versus
  another's Android-60fps starter baseline (95) are not the same
  number for a comparable player. This spread itself is evidence that
  there is no single verified consensus value — only broad,
  inconsistent starter ranges that sources explicitly frame as
  "starting points to personally tune," not fixed constants.
- Platform (Android vs. iPhone) and frame-rate tier (60 vs. 90/120
  fps) are cited as relevant differentiators; device *price/
  performance tier* (the schema's `LOW_END`/`MID_RANGE`/`HIGH_END`/
  `TABLET`) and *play style* (`RUSHER`/`SNIPER`/`BALANCED`) as
  currently modeled are **not** the axes these sources actually use.

**Verification table (required format):**

| Device Category | Play Style | Camera | ADS | Gyroscope | ADS Gyroscope | Recommendation Rationale | Primary Source | Secondary Source | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| N/A — no source segments by device-tier × play-style | N/A | — | — | — | — | No source found recommends settings keyed to this specific combination | — | — | **NONE — no combination in the original 12-cell grid is evidence-backed** |
| (Evidence-backed axis instead) Gyroscope users, no-scope/red-dot | — | — | — | 300-400% | — | Cited consistently as the competitive-standard starting range for close-range gyro aiming | [Sportskeeda](https://www.sportskeeda.com/bgmi/best-bgmi-sensitivity-settings-gyroscope-revealed) | [TechPlayForge](https://techplayforge.com/best-bgmi-sensitivity-settings-2026/) | Directly sourced range (starting point, not a fixed value) — **MEDIUM** |
| (Evidence-backed axis instead) Gyroscope users, 3x scope | — | — | — | 170-250% | — | Cited as the standard taper for medium-zoom gyro aiming | [Sportskeeda](https://www.sportskeeda.com/bgmi/best-bgmi-sensitivity-settings-gyroscope-revealed) | [TechPlayForge](https://techplayforge.com/best-bgmi-sensitivity-settings-2026/) | Directly sourced range — **MEDIUM** |
| (Evidence-backed axis instead) Gyroscope users, 4x ACOG | — | — | — | 180-230% | — | Cited as the standard taper for higher-zoom gyro aiming | [Sportskeeda](https://www.sportskeeda.com/bgmi/best-bgmi-sensitivity-settings-gyroscope-revealed) | [TechPlayForge](https://techplayforge.com/best-bgmi-sensitivity-settings-2026/) | Directly sourced range — **MEDIUM** |
| Non-gyroscope players | any | 95-150 (wide, conflicting range across sources) | slightly lower than camera (no consistent number) | N/A | N/A | Sources agree ADS should sit below camera for recoil control, but disagree sharply on absolute camera/ADS numbers | [BlueStacks](https://www.bluestacks.com/blog/game-guides/battlegrounds-mobile-india/bgmi-best-pro-sensitivity-settings-en.html) | [RaidGG](https://raidgg.com/game-guides/bgmi/bgmi-best-sensitivity-settings/) | **LOW** — directionally agreed (ADS < camera), but no defensible specific number |

**Directly sourced vs. derived:** every numeric range above is
**directly sourced** (quoted or closely paraphrased from a cited
guide) where marked; nothing in this table is an invented "reasonable
starting point" — where the evidence didn't support a number (camera/
ADS absolute values), the table says so explicitly rather than filling
the cell with a guess.

---

## 6. Unsupported or Low-Confidence Areas

- **The entire original `(deviceCategory × playStyle)` 12-cell BGMI
  grid is unsupported by evidence.** No reviewed source segments
  sensitivity recommendations by device price/performance tier
  crossed with an aggression play style. Do not populate any of the
  12 rows from this research; the draft values in
  `07-phase2-system-design.md` §6.2 remain explicitly unverified
  placeholders and should not be promoted to production data as-is.
- **Recommended schema simplification (per "do not manufacture
  precision"):** the evidence instead supports two different axes —
  (a) gyroscope usage (yes/no) and (b) scope/zoom level (no-scope/
  red-dot, 3x, 4x/ACOG, sniper) — which is a different shape than the
  current `BgmiSensitivityProfile` primary key. This is a genuine
  product/schema decision, not one this report makes unilaterally:
  the founder should choose between (1) simplifying to a
  `gyroscopeUser × deviceTier` model with far fewer, better-evidenced
  cells, or (2) pivoting the model to `gyroscopeUser × scopeLevel`,
  which the evidence fits noticeably better but which is a bigger
  change to the already-approved DTO shape in the PRD. Either choice
  should happen before `V4__seed_bgmi_profiles.sql` is written.
- **Camera/ADS absolute values (non-gyroscope) are low confidence
  across the board** — sources disagree by wide margins (e.g. 95 vs.
  130-150 for what's presented as a comparable "most players"
  recommendation) with no methodology disclosed for either figure.
  Do not treat either as authoritative; if the founder wants
  non-gyroscope recommendations in the MVP, budget time for
  additional, more rigorous research (e.g. a direct pro-player
  settings survey) rather than picking one blog's number.
- **The Valorant/Overwatch "inheritance" claim (§1):** appears only
  in an automatically-summarized search result, not on any directly
  fetched page. Treated as a search-summarization artifact, not a
  real conflicting source — but it's the concrete reason every
  constant in §2 was confirmed via direct WebFetch rather than trusted
  from search-result synthesis alone. Recorded here so the discrepancy
  isn't silently dropped.
- **No official developer documentation was found for Valorant,
  Overwatch 2, or Apex Legends sensitivity constants.** All three
  rest on community reverse-engineering. This is common and widely
  relied upon in this niche (essentially every public sensitivity
  converter tool, including well-established ones, uses community-
  derived constants for these specific games), but it is categorically
  different evidence from CS:GO/CS2's real console variable, and the
  PRD's "active" flag per game should reflect that distinction, not
  treat all five games as equally verified.

---

## 7. Implementation Recommendations

**Sensitivity conversion constants:**
- **CS:GO and CS2 (yaw 0.022):** Founder may reasonably approve these
  for `active = true` now — evidence type is the strongest available
  (a real, user-checkable console variable), consistent across every
  source found, with `sourceReference` = the csdb.gg command page and
  `lastVerifiedAt` = today's research date.
- **Valorant (yaw 0.07):** Recommend founder approval with the
  explicit understanding that this is community-consensus, not
  official, data — two independently-run sites confirmed the same
  number and formula on direct fetch, which is a reasonable bar for a
  free MVP tool, but should be documented as such in
  `sourceReference` (cite both xp-feed.com and dcprosens.com) rather
  than implied to be as solid as the CS:GO/CS2 figure.
- **Overwatch 2 (yaw 0.0066):** Recommend holding at `active = false`
  for MVP launch, or approving only with a visible "less-verified"
  note in the UI — the direct-fetch corroboration was weaker than
  Valorant's in this research pass (two dedicated pages didn't
  surface the number at all). If the founder wants Overwatch 2
  supported at launch anyway, that's a reasonable risk call, but it
  should be a conscious one, not an artifact of treating all five
  games identically.
- **Apex Legends (yaw 0.022):** Similar to Valorant — reasonable to
  approve with community-consensus framing in `sourceReference`,
  citing the Source-engine-lineage explanation and at least two
  independent calculator sites.
- **Required DB seed records (`V3__seed_sensitivity_profiles.sql`,**
  written only after the founder approves specific rows above**):**
  one row per approved game with `yawConstant`, `formulaVersion =
  "v1-cm360-ratio"`, `sourceReference` citing the URLs above,
  `lastVerifiedAt`, and `active` set only for approved rows.

**BGMI recommendations:**
- Do not write `V4__seed_bgmi_profiles.sql` against the original
  12-cell grid — it has zero evidentiary support (§6).
- Recommend the founder pick between the two schema options in §6
  before any BGMI seed data is written; this report supplies evidence
  for the `gyroscopeUser × scopeLevel` axes, not a schema decision.
- If launch timeline pressure means BGMI ships without a schema
  change, the fallback is: ship gyroscope-tier recommendations only
  (the best-evidenced part), and either drop non-gyroscope
  recommendations for MVP or clearly label them "community estimate,
  not independently verified" in both the API response and UI.

**Required automated tests (Phase 4), concrete cases:**

```java
// 1. Known conversion pairs (values from §3 of this report)
@Test void csgoToValorant_800dpi_sens2() {
    var result = converter.convert(CSGO, VALORANT, 800, 2.0);
    assertThat(result.targetSensitivity()).isCloseTo(0.6286, within(0.001));
}
@Test void cs2ToValorant_800dpi_sens2() {
    var result = converter.convert(CS2, VALORANT, 800, 2.0);
    assertThat(result.targetSensitivity()).isCloseTo(0.6286, within(0.001));
}
@Test void valorantToCs2_800dpi_sens1() {
    var result = converter.convert(VALORANT, CS2, 800, 1.0);
    assertThat(result.targetSensitivity()).isCloseTo(3.1818, within(0.001));
}

// 2. Round-trip (values from §4 of this report)
@Test void roundTrip_csgoToValorantToCsgo_within_tolerance() {
    var mid = converter.convert(CSGO, VALORANT, 800, 2.0).targetSensitivity();
    var back = converter.convert(VALORANT, CSGO, 800, mid).targetSensitivity();
    assertThat(back).isCloseTo(2.0, within(0.001));
}

// 3. Precision/rounding — internal calculation must not round early
@Test void internalCalculation_doesNotRoundBeforeFinalStep() {
    // verify the service's intermediate cm/360 value retains full
    // double precision and only the final DTO field is rounded to 4 dp
}
@Test void handlesVerySmallAndVeryLargeSensitivityValues() {
    // e.g. sensitivity = 0.01 and sensitivity = 10.0, dpi at both
    // boundaries (100 and 25600), asserting no overflow/underflow
    // and 4-decimal output stays stable
}

// 4. Inactive profile rejection — must reject even if data exists
@Test void rejectsConversion_whenEitherProfileInactive() {
    // seed a SensitivityGameProfile row with a real yawConstant but
    // active=false; assert the endpoint returns 422
    // BUSINESS_RULE_VIOLATION, never silently using the inactive row
}
```

**Precision/rounding rules:** perform the entire `cm/360`/ratio
calculation in `double` (or `BigDecimal` with at least 10 significant
digits) and round to 4 decimal places **only** on the final
`targetSensitivity`/`sourceCmPer360`/`targetCmPer360` values placed in
the response DTO — never round an intermediate value before using it
in a subsequent calculation step, per the requirement above.

**Schema changes required before implementation:** none for the
sensitivity-conversion side (the `SensitivityGameProfile` design
already fits this report's findings). For BGMI, the schema decision
from §6 has been made (the `gyroscopeUsage × scopeLevel` model,
implemented in `07-phase2-system-design.md` §1.4) — see §8 for the
resulting data-population decision.

---

## 8. Deep Verification Pass 2 — Activation Decision (2026-09-13)

Run at the founder's explicit direction to close the open activation
question rather than leave it pending indefinitely. New searches and
direct page fetches were performed specifically to (a) find a more
primary source for the CS:GO/CS2 constant, (b) look for independent
corroboration of Overwatch 2's constant, (c) look for methodology
behind Valorant's constant, and (d) find better-sourced BGMI data,
especially for the previously `INSUFFICIENT_DATA` sniper-scope and
non-gyroscope rows.

### 8.1 New findings

- **CS:GO/CS2 (0.022):** found indexed in Valve's own **Valve
  Developer Community wiki**, "Console Commands" page
  (`developer.valvesoftware.com/wiki/Console_commands`) — a genuine
  primary/official source, not a third-party mirror. Direct `WebFetch`
  of that specific page returned HTTP 403 (the site's own bot
  protection blocked the fetch, not a content issue), so this report
  cannot quote the page's exact wording — but its existence in Valve's
  own wiki, combined with every third-party mirror (csdb.gg,
  totalcsgo.com, csgo-nade.com) agreeing on `0.022`, and the fact that
  any player can verify the live default themselves by typing `m_yaw`
  in-game, is treated as sufficient. Confidence: **unchanged at
  HIGH**, now with an identified (if not directly fetchable) primary
  source.
- **Overwatch 2 (0.0066):** no new primary corroboration found. A
  targeted fetch of a `mouse-sensitivity.com` community forum thread
  about Overwatch 2 (a specific, different page than fetched in the
  first pass) discussed a *different* setting entirely ("Relative Aim
  Sensitivity While Zoomed," used for scoped heroes like Widowmaker/
  Ashe) and did not state a yaw constant. This is now the **second**
  direct-fetch attempt on a dedicated Overwatch/OW2 community page
  that failed to surface the `0.0066` figure on-page, despite it being
  repeated in multiple auto-summarized search results. Confidence:
  **unchanged at LOW-MEDIUM** — deep search specifically failed to
  strengthen this one.
- **Valorant (0.07):** a new source (`sensai.games`'s Valorant
  engine-mechanics guide) was fetched directly and repeats the same
  `0.07` figure, but — like every other source checked across both
  research passes — states it as a given fact with no testing
  methodology or official citation ("attributes the constant to a
  'Modified Unreal Engine 4' but provides no technical documentation").
  Confidence: **unchanged at MEDIUM** — more repetition, still no
  primary source, which is the ceiling for this game absent an
  official Riot statement.
- **BGMI:** found a **substantially better-sourced** guide —
  [RaidGG's BGMI sensitivity guide](https://raidgg.com/game-guides/bgmi/bgmi-best-sensitivity-settings/)
  (published 2026-03-10, updated 2026-03-14) — which names specific
  Indian BGMI pros (Jonathan, Neyoo, Mavi, Zgod) as its reference
  settings and states an explicit testing methodology ("tested across
  5 devices," "50+ TDM matches and 20+ Classic matches"). This is a
  meaningfully stronger source than anything found in Pass 1 (which
  was limited to unsourced, undated-or-2021-dated blog aggregators).
  It provides a **complete** gyroscope AND non-gyroscope (ADS/camera)
  breakdown across all 7 of BGMI's real scope tiers (no-scope, red
  dot, 2x, 3x, 4x, 6x, 8x) — filling gaps that Pass 1 left as
  `INSUFFICIENT_DATA`.

### 8.2 Revised BGMI evidence table (RaidGG as primary, Sportskeeda/BlueStacks as secondary cross-checks)

| Real BGMI scope tier | Gyroscope Sensitivity (RaidGG) | Gyroscope Sensitivity (Sportskeeda, secondary) | ADS/Camera Sensitivity, non-gyro (RaidGG) | Camera cross-check (BlueStacks) |
|---|---|---|---|---|
| No Scope (TPP) | 300-400% | 300-400% | 130-150% (camera) | 130-150% (camera) — **exact match** |
| No Scope (FPP) | — | — | 110-130% (camera) | — |
| Red Dot/Holo | 300-400% | 300-400% | 60-75% (ADS) | — |
| 2x Scope | 300-380% | (grouped with red dot) | 45-60% (ADS) | — |
| 3x Scope | 220-300% | 170-250% | 28-35% (ADS) | — |
| 4x ACOG | 180-250% | 180-230% | 22-30% (ADS) | — |
| 6x Scope | 120-160% | 80-130% | 18-25% (ADS) | — |
| 8x Scope | 70-100% | 70-110% | 10-15% (ADS) | — |

**Schema mapping note (honesty about a real simplification):** the
approved `ScopeLevel` enum has 5 values, but BGMI's actual settings
screen has 7 (it splits 2x from 3x, and 6x from 8x). Mapping decisions
made for activation:
- `SCOPE_3X` is mapped to RaidGG's **"3x Scope"** row specifically
  (not blended with 2x) — 2x is left unmapped by this schema, a known
  gap, not silently absorbed into another tier's number.
- `SNIPER_SCOPE` is mapped to **"8x Scope"** specifically (not blended
  with 6x) — chosen because "sniper scope" colloquially refers to the
  highest zoom tier, and 8x is where RaidGG and Sportskeeda agree most
  closely (70-100% vs. 70-110%, overlap 70-100%), which also makes it
  the best-cross-validated of the two options. 6x is left unmapped, a
  known gap.
- `NO_SCOPE` (non-gyro) uses the **TPP** figure (130-150%) as the
  schema default, since TPP is BGMI's more commonly played mode; the
  FPP figure (110-130%) is recorded in `sourceReference` text but not
  given its own row — the schema has no FPP/TPP axis.

These gaps (2x, 6x, FPP) are deliberately **not** invented values —
they simply aren't representable in the current 5-value `ScopeLevel`
enum, and are flagged here as a candidate future schema refinement,
not silently dropped or guessed into an adjacent row.

### 8.3 Final activation decision

**Sensitivity conversion constants — `V3__seed_sensitivity_profiles.sql`:**

| Game | yawConstant | active | confidence | sourceReference (to record on the row) |
|---|---:|---|---|---|
| CSGO | 0.022 | **true** | HIGH | "Valve Developer Community wiki, Console Commands page (developer.valvesoftware.com/wiki/Console_commands); cross-confirmed by csdb.gg, totalcsgo.com, csgo-nade.com; empirically verifiable in-game via the `m_yaw` console command. Verified 2026-09-13." |
| CS2 | 0.022 | **true** | HIGH | "Same Source-engine `m_yaw` cvar retained in Source 2; csdb.gg CS2 command page; empirically verifiable in-game. Verified 2026-09-13." |
| VALORANT | 0.07 | **true** | MEDIUM | "Community-derived (no official Riot documentation); confirmed via direct fetch of xp-feed.com and dcprosens.com, cross-checked against aimhub.gg, senslab.pro, sensai.games — consistent across every source found in two research passes, zero contradicting values. Verified 2026-09-13." |
| APEX_LEGENDS | 0.022 | **true** | MEDIUM | "Community-derived, attributed to Respawn's Source-engine fork retaining the default `m_yaw`; confirmed via senslab.pro direct fetch, cross-checked against sensiconverter.com, sensconverterfree.com; not independently verifiable in-game (no accessible dev console in retail Apex). Verified 2026-09-13." |
| OVERWATCH2 | 0.0066 | **false — HELD BACK** | LOW-MEDIUM | Not activated: two separate direct-fetch attempts on dedicated Overwatch/OW2 community pages (Pass 1: mouse-sensitivity.com's OW page and flank.gg's OW2 converter; Pass 2: a mouse-sensitivity.com OW2 forum thread) each failed to state this constant on-page, despite it appearing in multiple auto-summarized search results. Revisit if a page that states it directly is found, or reconsider founder risk tolerance. |

**BGMI — `V4__seed_bgmi_profiles.sql`:** all 10 rows activated, each
at its honestly-assessed confidence (midpoint used as
`recommendedStartingValue` wherever a range exists):

| gyroscopeUsage | scopeLevel | min | max | starting | confidence | active |
|---|---|---:|---:|---:|---|---|
| GYRO_ON | NO_SCOPE | 300 | 400 | 350 | MEDIUM | **true** |
| GYRO_ON | RED_DOT | 300 | 400 | 350 | MEDIUM | **true** |
| GYRO_ON | SCOPE_3X | 220 | 300 | 260 | MEDIUM | **true** |
| GYRO_ON | SCOPE_4X_ACOG | 180 | 230 | 205 | MEDIUM | **true** |
| GYRO_ON | SNIPER_SCOPE | 70 | 100 | 85 | MEDIUM | **true** |
| GYRO_OFF | NO_SCOPE | 130 | 150 | 140 | MEDIUM | **true** |
| GYRO_OFF | RED_DOT | 60 | 75 | 68 | LOW | **true** |
| GYRO_OFF | SCOPE_3X | 28 | 35 | 32 | LOW | **true** |
| GYRO_OFF | SCOPE_4X_ACOG | 22 | 30 | 26 | LOW | **true** |
| GYRO_OFF | SNIPER_SCOPE | 10 | 15 | 13 | LOW | **true** |

`confidence = MEDIUM` rows have two independent, agreeing sources.
`confidence = LOW` rows rest on RaidGG alone — a well-documented
single source (named pros, explicit testing method, recent), but
still only one source, so it doesn't clear the two-source bar used
for `MEDIUM` elsewhere in this report. **LOW confidence is not a
reason to leave these inactive** — the whole point of the
`(min, max, confidence)` design is that a `LOW`-confidence row with
real evidence behind it is strictly more honest and more useful than
either an `INSUFFICIENT_DATA` null or a fabricated `MEDIUM` claim. The
disclaimer text already tells the user these are starting points to
personally adjust, which is the correct caveat for exactly this
confidence level.

**What remains genuinely unsupported and stays out of both
migrations:** BGMI's 2x and 6x scope tiers (no row in the 5-value
schema), and Overwatch 2 (held back per above). Nothing here is
activated by guessing — every activated row traces to a specific,
named source in this section.
