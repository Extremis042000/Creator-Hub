# EXTREMIS Creator Hub — Product Requirements Document (Phase 1)

Status: Phase 0 blueprint approved by founder, with one architecture
change (Java Spring Boot backend instead of an all-Next.js backend).
**Reviewed and APPROVED WITH 6 REQUIRED REVISIONS by the founder; all
six are incorporated below** (anonymous `save=true` rate limiting +
30-day expiry, `GeneratedResult` entity contract, corrected
eDPI-vs-cm/360° sensitivity model with a constants source/version
strategy, BGMI's 12-profile data table + exact settings terminology,
the `/api/v1/results/{shareToken}` contract + secure token generation,
and a new Non-Functional Requirements / CORS / Rate Limiting section).
Approved for Phase 2: System Design. **Further revised after Phase 2
review + the Data Verification Report:** §5.3 (BGMI Sensitivity
Helper) is rewritten from a `(deviceCategory, playStyle)` model to a
`(gyroscopeUsage, scopeLevel)` model with evidence-graded ranges,
since the original grid had zero evidentiary support — see
`08-data-verification-report.md` and `07-phase2-system-design.md`
§1.4/§6.2.

## 1. Personas

1. **The Competitive Grinder** — Valorant/BGMI player optimizing
   settings (sensitivity, DPI) and tracking performance (KD ratio).
   Wants a fast, accurate, no-signup-required tool.
2. **The Aspiring Creator** — makes gaming YouTube content, needs
   title/description ideas quickly, cares about SEO/discoverability
   of their own videos.
3. **The Gear Shopper** — arrives via a tool or article, open to
   purchasing recommended gear via affiliate links (later phase).

## 2. User Journeys

**Journey A — Anonymous tool use (all 5 MVP tools):**
```
Land on tool page (organic search or EXTREMIS Plays link)
  → read intro/how-to-use
  → fill inputs
  → submit → POST to Spring Boot endpoint
  → see result instantly
  → optionally copy / share (share link persists result via shareToken)
  → optionally click to a related tool
```

**Journey B — Signed-in user (post-MVP, scaffolded now):**
```
Anonymous use → decides to sign in (Google OAuth)
  → JWT issued → history of past tool results saved to account
  → later: premium tools unlocked if entitled
```

**Journey C — Creator buys a digital product (Phase 5+):**
```
Discovers product via tool page cross-link or store page
  → checkout → payment provider → webhook verified by Spring Boot
  → entitlement granted → secure download link
  → confirmation + download email
```

## 3. Feature List — MVP vs. Future

**MVP (this PRD covers in full detail):**
- 5 deterministic tools (below)
- Tools directory + homepage
- SEO layer (sitemap, meta, structured data)
- Anonymous usage with optional result sharing via `shareToken`
- Google Analytics event tracking

**Explicitly deferred (future roadmap, not built until MVP validates):**
- Authentication (Google OAuth + JWT) — architecture defined, not
  required for any MVP tool to function
- Admin panel
- Digital products, affiliate links, display ads, premium tier
  (per approved monetization sequence — gated on real traffic first)
- Hashtag generator, prize-split calculator, and other roadmap tools

## 4. Global API Conventions

- Base path: `/api/v1`
- All request/response bodies: JSON
- All tool endpoints accept an optional `save: boolean` field (default
  `false`); when `true`, the result is persisted as a `GeneratedResult`
  and the response includes a `shareToken`. When `false` (default),
  nothing touches the database — pure stateless calculation.
- **`save=false` writes nothing at all (explicit, per review — closes
  ambiguity #27):** no `GeneratedResult` row, and no `ToolUsage` row
  either — `save=false` means zero database writes, full stop, which
  is what makes the reliability guarantee in §7 ("a `save=false`
  calculation must never fail because of a database problem") actually
  true. `save=true` writes **both**: one `GeneratedResult` row (the
  shareable result, per §4.1) and one `ToolUsage` row (a lightweight
  usage-analytics record, per the Phase 0 schema) — the two rows serve
  different purposes (sharing vs. usage analytics) and are not
  redundant, but both only happen together, on `save=true`, never
  independently.
- Standard error response DTO (`ApiErrorResponse`), returned by the
  global `@ControllerAdvice` handler for any 4xx/5xx:

```json
{
  "timestamp": "2026-09-12T10:15:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "One or more fields are invalid.",
  "path": "/api/v1/tools/kd-calculator",
  "fieldErrors": [
    { "field": "kills", "message": "must be >= 0" }
  ]
}
```

- All numeric business errors (e.g. unsupported game pair) use
  `error: "BUSINESS_RULE_VIOLATION"` with a 422 status, distinct from
  raw field-validation 400s.

### 4.1 `GeneratedResult` entity contract (REQUIRED CHANGE 2)

The PRD depends on this entity from §4 onward, so its minimum shape is
fixed here rather than left to Phase 2:

```
GeneratedResult
-----------------------------
id            UUID (PK)
toolType      ENUM (KD_CALCULATOR, VALORANT_SENSITIVITY_CONVERTER,
                     BGMI_SENSITIVITY_HELPER, TITLE_GENERATOR,
                     DESCRIPTION_GENERATOR)
inputJson     JSONB   -- the original request DTO, minus `save`
outputJson    JSONB   -- the response DTO, minus `shareToken`
shareToken    VARCHAR UNIQUE  -- cryptographically secure, see §4.2
userId        UUID NULL       -- nullable; authentication is deferred,
                                  but the column exists now so anonymous
                                  and future authenticated results share
                                  one table without a migration later
createdAt     TIMESTAMP
expiresAt     TIMESTAMP       -- see retention policy below
```

**Retention policy for anonymous saved results (REQUIRED CHANGE 1):**

```
save=true (anonymous)
      ↓
GeneratedResult created, shareToken issued
      ↓
Publicly readable at GET /api/v1/results/{shareToken}
      ↓
expiresAt = createdAt + 30 days
      ↓
A scheduled job (Spring `@Scheduled`) hard-deletes expired rows daily
```

`GET /api/v1/results/{shareToken}` returns 404 (not the shared result)
once `expiresAt` has passed, even if the row hasn't been physically
purged yet — expiry is checked at read time, deletion is a cleanup
detail, not the source of truth for whether a link still works.

**Anti-abuse for `save=true` (REQUIRED CHANGE 1):** since every tool
endpoint is unauthenticated, `save=true` gets a stricter, separate rate
limit from plain calculations — see §9 (Rate Limiting).

### 4.2 Shared result endpoint (REQUIRED CHANGE 5)

Shared results are a platform-level resource, not a per-tool one, so
they live outside `/tools`:

```
GET /api/v1/results/{shareToken}
```

Response:

```json
{
  "toolType": "KD_CALCULATOR",
  "result": { "kdRatio": 2.0, "performanceCategory": "STRONG", "isUndefined": false },
  "createdAt": "2026-09-12T10:15:00Z",
  "expiresAt": "2026-10-12T10:15:00Z"
}
```

`result` is the tool's own response DTO shape (minus `shareToken`);
the frontend dispatches on `toolType` to pick the right result
renderer. Unknown/expired/never-existed `shareToken` → 404 with the
standard `ApiErrorResponse` (the API does not distinguish "expired"
from "never existed" in the response, to avoid leaking which tokens
were ever valid).

**ShareToken generation (REQUIRED CHANGE 6):** generated server-side
using a cryptographically secure random source (e.g.
`SecureRandom` + Base62/Base64-URL encoding, minimum 22 characters —
~128 bits of entropy), never a sequential/incrementing ID. Enforced by
a database `UNIQUE` constraint on `GeneratedResult.shareToken` in
addition to the application-level generation, so a collision is
rejected at the DB layer rather than silently overwriting another
result.

---

## 5. Tool Specifications

### 5.1 Gaming KD Ratio Calculator

**User story:** As a competitive player, I want to enter my kills and
deaths so I can instantly see my KD ratio and how it compares to
general performance bands, without creating an account.

**Route:** `/tools/kd-calculator`
**Endpoint:** `POST /api/v1/tools/kd-calculator`

**Inputs:**
| Field | Type | Required |
|---|---|---|
| kills | integer | yes |
| deaths | integer | yes |
| save | boolean | no (default false) |

**Validation rules:**
- `kills >= 0`
- `deaths >= 0`
- Both cannot be null/missing (400 `VALIDATION_ERROR` otherwise)

**Request DTO (`KdCalculatorRequest`):**
```json
{ "kills": 0, "deaths": 0, "save": false }
```

**Response DTO (`KdCalculatorResponse`):**
```json
{
  "kdRatio": 1.85,
  "displayValue": "1.85",
  "performanceCategory": "STRONG",
  "isUndefined": false,
  "shareToken": null
}
```

Undefined case (revised per review — `kdRatio` must never hold a
numeric value that looks like a real ratio when it isn't one):
```json
{
  "kdRatio": null,
  "displayValue": "Perfect / No Deaths",
  "kills": 5,
  "deaths": 0,
  "performanceCategory": "UNDEFEATED",
  "isUndefined": true,
  "shareToken": null
}
```

**Business logic:**
- If `deaths == 0` and `kills > 0`: `kdRatio = null`,
  `isUndefined = true`, `performanceCategory = "UNDEFEATED"`,
  `displayValue = "Perfect / No Deaths"`. `kills`/`deaths` are echoed
  back so the frontend can render "5-0" without recomputing. `kdRatio`
  is never set to `kills` or any other numeric stand-in — `null` is
  the only representation of "mathematically undefined," so API
  consumers can't mistake it for a real ratio.
- If `deaths == 0` and `kills == 0`: `kdRatio = null`, category
  `NO_DATA`, `displayValue = "No Data"`.
- Else `kdRatio = round(kills / deaths, 2)`, `displayValue` = the same
  value formatted to 2 decimals as a string, `isUndefined = false`.
- Category thresholds (deterministic, documented as general bands,
  not a performance guarantee): `< 1.0` → `DEVELOPING`, `1.0–1.49` →
  `SOLID`, `1.5–2.49` → `STRONG`, `>= 2.5` → `ELITE`, undefined → `UNDEFEATED`.
- If `save == true`, persist a `GeneratedResult` row and return a
  `shareToken`.

**Error handling:**
- Negative `kills`/`deaths` → 400 `VALIDATION_ERROR`, field-level
  message.
- Non-numeric/missing fields → 400 `VALIDATION_ERROR` (handled by
  Jakarta Validation + `@ControllerAdvice` before reaching the service
  layer).

**Acceptance criteria:**
- Given kills=20, deaths=10 → kdRatio=2.00, category=STRONG,
  isUndefined=false.
- Given kills=5, deaths=0 → kdRatio=null, isUndefined=true,
  performanceCategory=UNDEFEATED, kills=5, deaths=0 echoed.
- Given kills=0, deaths=0 → kdRatio=null, category=NO_DATA.
- Given kills=-1 → 400 with a field error on `kills`.
- `save=true` → response includes a non-null, unique `shareToken`
  generated per §4.2; `GET /api/v1/results/{shareToken}` returns the
  same result wrapped per that endpoint's contract.

---

### 5.2 Valorant Sensitivity Converter

**User story:** As a Valorant player switching from another FPS
(or another mouse DPI), I want to convert my existing sensitivity so
my aim feels the same in Valorant.

**Route:** `/tools/valorant-sensitivity-converter`
**Endpoint:** `POST /api/v1/tools/valorant-sensitivity-converter`

**Inputs:**
| Field | Type | Required |
|---|---|---|
| sourceGame | enum (`VALORANT`, `CSGO`, `CS2`, `OVERWATCH2`, `APEX_LEGENDS`) | yes |
| targetGame | enum (same set) | yes |
| dpi | integer | yes |
| sourceSensitivity | double | yes |
| save | boolean | no |

**Validation rules:**
- `dpi` between 100 and 25600
- `sourceSensitivity > 0`
- `sourceGame` and `targetGame` must each be one of the supported enum
  values; if `sourceGame == targetGame`, allowed but returns the input
  unchanged (no-op conversion) with a note in the response.
- If the specific `(sourceGame, targetGame)` pair has no defined
  conversion factor yet, this is a **business rule violation**, not a
  validation error.

**Same-game precedence rule (REQUIRED CHANGE 5 resolution — explicit,
not left for the implementer to guess):** the same-game no-op check
runs **before** the active-profile check, unconditionally. If
`sourceGame == targetGame`, the service returns the input sensitivity
unchanged with `conversionNote` set, **regardless of whether that
game's `SensitivityGameProfile.active` is true or false** — a same-
game request never needs the profile's `yawConstant` at all (the ratio
`yaw/yaw` is always 1 algebraically, so it's not computed, just
short-circuited), so there's nothing for "inactive" to block. The
active-profile check only applies when `sourceGame != targetGame`.
In pseudocode:
```
if sourceGame == targetGame:
    return unchanged (no-op), regardless of active flag
else if either profile inactive or pair unsupported:
    422 BUSINESS_RULE_VIOLATION
else:
    perform the conversion
```

**Request DTO (`SensitivityConversionRequest`):**
```json
{
  "sourceGame": "CSGO",
  "targetGame": "VALORANT",
  "dpi": 800,
  "sourceSensitivity": 2.0,
  "save": false
}
```

**Response DTO (`SensitivityConversionResponse`):**

Corrected per review — the example below is recomputed from this
section's own stated formula and the candidate constants in
`08-data-verification-report.md` §2 (CS:GO yaw 0.022, Valorant yaw
0.07), for the exact request shown above (CS:GO, 800 DPI, sensitivity
2.0 → Valorant). The previous version of this example (`0.6234` /
`32.45`) did not match the documented formula and must not be used as
a test fixture — it was a documentation error, not an alternate valid
answer.

```json
{
  "targetSensitivity": 0.6286,
  "effectiveDpi": 1600.0,
  "sourceCmPer360": 25.9773,
  "targetCmPer360": 25.9773,
  "formulaVersion": "v1-cm360-ratio",
  "conversionNote": null,
  "shareToken": null
}
```

Worked out: `effectiveDpi = dpi × sourceSensitivity = 800 × 2.0 =
1600.0`. `targetSensitivity = 2.0 × (0.022 / 0.07) = 0.628571… ≈
0.6286` (4 dp). `cm/360 = 914.4 / (dpi × sensitivity × yaw) = 914.4 /
(800 × 2.0 × 0.022) = 25.9773`, and the target-side figure must equal
the source-side figure by construction (that's the whole point of the
conversion) — `914.4 / (800 × 0.628571 × 0.07) = 25.9773`, confirmed
equal. This matches `08-data-verification-report.md` §3's "CS:GO →
Valorant" worked example exactly — that report and this PRD must not
show two different answers for the same input, which is exactly the
bug being fixed here.

**`formulaVersion` (REQUIRED CHANGE 6 resolution — decided as Option
A):** every `SensitivityConversionResponse` includes the
`formulaVersion` string that was active at calculation time (currently
`"v1-cm360-ratio"`, matching `SensitivityGameProfile.formulaVersion`
for both games involved — if the two games' rows ever disagree on
`formulaVersion`, that pairing is treated as unsupported, 422). When
`save=true`, this field is persisted as part of `outputJson` on the
`GeneratedResult` row — `GeneratedResult` itself gets no separate
`formulaVersion` column; the response DTO (stored verbatim in
`outputJson`) is the single place this is recorded. A later change to
the live constant does not alter historical `outputJson` rows, so an
old shared link keeps showing the number it was calculated with.

`sourceCmPer360`/`targetCmPer360` are always computed and returned
(even though the MVP UI may only surface `targetSensitivity`) so the
conversion model stays independently verifiable and testable from the
API alone.

**Business logic (revised per review — eDPI and cm/360° are distinct
concepts and must not be conflated):**

`effectiveDpi` (eDPI) = `dpi × sourceSensitivity`. This is a
convenience figure for the user, not the basis of the conversion.

The actual conversion is performed using each game's validated
yaw/sensitivity constant: the backend converts the source
`(dpi, sourceSensitivity)` into a common **physical rotation metric**
— centimeters (or inches) of mouse movement per 360° turn,
`cmPer360 = (360 / (dpi × sensitivity × yawConstant)) × 2.54` — then
solves for the `targetSensitivity` at the same `dpi` in the target
game that reproduces the *same* `cmPer360`. Preserving `cmPer360`
(the physical distance the hand moves for a full turn), not preserving
the raw sensitivity number, is what makes the conversion correct
across games with different yaw constants.

**Sensitivity constants require a source/version strategy (REQUIRED
CHANGE 3)** — per-game constants live in a `SensitivityGameProfile`
table, not hardcoded per-branch logic:

```
SensitivityGameProfile
-------------------------
game              ENUM (PK)
yawConstant       DOUBLE   -- degrees of rotation per count, per this game's engine
formulaVersion    VARCHAR  -- bumped whenever the constant or formula changes
sourceReference   VARCHAR  -- where the constant was verified (official docs, community-verified benchmark, etc.)
lastVerifiedAt    TIMESTAMP
active            BOOLEAN  -- inactive games are rejected as unsupported, not guessed
```

The MVP only accepts a `(sourceGame, targetGame)` pair when **both**
profiles have `active = true` and a `yawConstant` with a recorded
`sourceReference` — the service must never fall back to an unverified
or guessed constant. Adding a new game is a data insert into this
table plus verification, not a code change.

**Error handling:**
- `dpi` outside 100–25600 → 400 `VALIDATION_ERROR`.
- `sourceSensitivity <= 0` → 400 `VALIDATION_ERROR`.
- Unsupported `(sourceGame, targetGame)` pair, or either profile
  `active = false`, → 422 `BUSINESS_RULE_VIOLATION`, message:
  "Conversion between these games is not supported yet."

**Acceptance criteria:**
- Given a known reference pair (e.g. CS:GO → Valorant at 800 DPI,
  2.0 sensitivity) with verified `SensitivityGameProfile` rows for
  both games, `sourceCmPer360 == targetCmPer360` (within rounding
  tolerance) and `targetSensitivity` matches the documented formula.
- `sourceGame == targetGame` → `targetSensitivity == sourceSensitivity`,
  `conversionNote` explains it's a no-op, **even if that game's
  profile is `active = false`** (same-game precedence rule above).
- Unsupported pair, or a pair where one profile is inactive, when
  `sourceGame != targetGame` → 422 with the exact business message
  above.
- Every successful response includes a non-null `formulaVersion`
  matching both games' `SensitivityGameProfile.formulaVersion`.
- `dpi = 99` → 400; `dpi = 25601` → 400.
- Launch blocker: only games with an independently verified
  `SensitivityGameProfile` ship in the MVP's supported list — the
  exact initial list (e.g. Valorant, CS2, CS:GO, Overwatch 2, Apex
  Legends) is confirmed with sourced constants before Phase 4 coding,
  not guessed during implementation.

---

### 5.3 BGMI Sensitivity Helper (REVISED — schema pivoted per Data Verification Report)

**Revision note:** the original `(deviceCategory, playStyle)` model
below is replaced. `08-data-verification-report.md` §5-6 found zero
evidence supporting that grid; the founder approved pivoting to
`(gyroscopeUsage, scopeLevel)` with ranges instead of single point
values, matching what the evidence actually supports (see
`07-phase2-system-design.md` §1.4/§6.2 for the entity/data detail this
section mirrors).

**User story:** As a BGMI player, I want a sensitivity starting range
for my scope/zoom level based on whether I use gyroscope, understanding
it's a starting point — not a guarantee of better performance, and not
presented with more precision than the evidence supports.

**Route:** `/tools/bgmi-sensitivity-helper`
**Endpoint:** `POST /api/v1/tools/bgmi-sensitivity-helper`

**Inputs:**
| Field | Type | Required |
|---|---|---|
| usesGyroscope | boolean | yes |
| save | boolean | no |

The tool takes only one input now — which slider set to recommend
(gyroscope vs. ADS/camera) — and returns recommendations for **every**
`scopeLevel` in one response, rather than requiring the user to submit
a specific scope. This matches how the real tool is used: a player
wants their full settings sheet, not one scope at a time.

**Validation rules:**
- `usesGyroscope` must be present and boolean (missing/non-boolean →
  400 `VALIDATION_ERROR`).

**Request DTO (`BgmiSensitivityRequest`):**
```json
{ "usesGyroscope": true, "save": false }
```

**Response DTO (`BgmiSensitivityResponse`):**
```json
{
  "recommendations": [
    {
      "scopeLevel": "NO_SCOPE",
      "settingName": "Gyroscope Sensitivity",
      "recommendedMin": 300,
      "recommendedMax": 400,
      "recommendedStartingValue": 350,
      "confidence": "MEDIUM"
    },
    {
      "scopeLevel": "RED_DOT",
      "settingName": "Gyroscope Sensitivity",
      "recommendedMin": 300,
      "recommendedMax": 400,
      "recommendedStartingValue": 350,
      "confidence": "MEDIUM"
    },
    {
      "scopeLevel": "SCOPE_3X",
      "settingName": "Gyroscope Sensitivity",
      "recommendedMin": 170,
      "recommendedMax": 250,
      "recommendedStartingValue": 210,
      "confidence": "MEDIUM"
    },
    {
      "scopeLevel": "SCOPE_4X_ACOG",
      "settingName": "Gyroscope Sensitivity",
      "recommendedMin": 180,
      "recommendedMax": 230,
      "recommendedStartingValue": 205,
      "confidence": "MEDIUM"
    },
    {
      "scopeLevel": "SNIPER_SCOPE",
      "settingName": "Gyroscope Sensitivity",
      "recommendedMin": 70,
      "recommendedMax": 100,
      "recommendedStartingValue": 85,
      "confidence": "MEDIUM"
    }
  ],
  "disclaimer": "These are recommended starting-point settings based on your gyroscope usage and scope level. Adjust them to your personal comfort and device performance.",
  "shareToken": null
}
```

**Updated per Deep Verification Pass 2 (2026-09-13) — all rows now
activated** (`08-data-verification-report.md` §8): every row above
returns real `MEDIUM`-confidence ranges (`SNIPER_SCOPE` maps
specifically to BGMI's 8x tier, per the report's mapping notes — its
6x tier has no row in this 5-value schema, a documented gap, not a
guess). When `usesGyroscope = false`, `settingName` becomes `"ADS
Sensitivity"` for every `scopeLevel` except `NO_SCOPE`, which becomes
`"Camera Sensitivity"` — these rows are now also activated, at `LOW`
confidence for every tier except `NO_SCOPE` (`MEDIUM`, since RaidGG's
and BlueStacks' camera figures matched exactly). `LOW` confidence
means real, single-sourced evidence with a range and a starting value
— not `INSUFFICIENT_DATA`; the disclaimer's "adjust to personal
comfort" framing is what makes shipping a `LOW`-confidence starting
value honest rather than overclaimed. The response DTO's `confidence`
field is what tells the frontend which tier of trust to visually
convey (e.g. a "less-verified" note on `LOW` rows) — the API always
returns real data now, no row is null.

**Settings terminology** — each `scopeLevel` maps to an exact BGMI
in-game scope/zoom tier, and `settingName` names the exact slider that
tier's row is about:

| scopeLevel | Maps to BGMI in-game scope | settingName when `usesGyroscope=true` | settingName when `usesGyroscope=false` |
|---|---|---|---|
| `NO_SCOPE` | Free-look, no scope equipped | "Gyroscope Sensitivity" | "Camera Sensitivity" |
| `RED_DOT` | Red Dot / holo sight | "Gyroscope Sensitivity" | "ADS Sensitivity" |
| `SCOPE_3X` | 2x/3x scope | "Gyroscope Sensitivity" | "ADS Sensitivity" |
| `SCOPE_4X_ACOG` | 4x ACOG scope | "Gyroscope Sensitivity" | "ADS Sensitivity" |
| `SNIPER_SCOPE` | 8x scope specifically (6x has no row in this schema — see verification report §8.2) | "Gyroscope Sensitivity" | "ADS Sensitivity" |

**Business logic:**
- A data-driven lookup over `BgmiSensitivityProfile`, keyed by
  `(gyroscopeUsage, scopeLevel)` — deterministic, no AI call. See
  `07-phase2-system-design.md` §1.4 for the full entity.
- The service returns **all 5 `scopeLevel` rows** for the requested
  `gyroscopeUsage` in one call, in a fixed order (`NO_SCOPE`,
  `RED_DOT`, `SCOPE_3X`, `SCOPE_4X_ACOG`, `SNIPER_SCOPE`).
- A row with `active = false` (not yet founder-approved — none of
  the current 10 rows are in this state as of Deep Verification Pass
  2, but the mechanism stays in place for any future row added before
  its evidence clears the bar) or `confidence = INSUFFICIENT_DATA` is
  still returned — with null range fields — rather than omitted; the
  frontend renders those rows as "not enough data yet for this
  setting" instead of hiding them, so the tool never silently drops a
  scope tier.
- **DB-to-API transformation rule (explicit, per review — closes the
  ambiguity between "database says MEDIUM+inactive" and "what does the
  API return"):** the mapping is a pure function of `active`, not of
  the stored `confidence` alone —
  ```
  if row.active == true:
      return confidence = row.confidence, ranges = row's actual values
  else:  // not yet founder-approved, regardless of stored confidence
      return confidence = "INSUFFICIENT_DATA", ranges = null, null, null
  ```
  A row can carry `confidence = MEDIUM` in the database (reflecting
  the research finding) while `active = false` (not yet approved for
  production) — the API never exposes that internal "researched but
  not approved" state as anything other than `INSUFFICIENT_DATA`; a
  caller cannot distinguish "no evidence exists" from "evidence exists
  but isn't approved yet" from the API alone, which is intentional —
  approval state is an internal admin concern, not something to leak
  to end users.
- The `disclaimer` string is always included in the response (not
  just the UI) so any API consumer sees the non-guarantee framing.
  **Wording note (fixed per review — BLOCKER 2):** the founder's
  original disclaimer text referenced "device category and play
  style," which described the pre-pivot model; since the schema moved
  to `(gyroscopeUsage, scopeLevel)`, the wording is updated to match
  ("gyroscope usage and scope level") so the disclaimer doesn't
  describe an input model the tool no longer has. The non-guarantee
  *substance* the founder specified is preserved verbatim; only the
  two nouns naming the input axes changed. Flag to the founder if the
  exact original phrasing is wanted for some other reason — but
  shipping it unchanged would make the API describe itself
  incorrectly, which review correctly rejected.

**Error handling:**
- Missing/non-boolean `usesGyroscope` → 400 `VALIDATION_ERROR` naming
  the field.

**Acceptance criteria:**
- `usesGyroscope: true` → response contains exactly 5 recommendation
  rows (one per `scopeLevel`), in the fixed order above.
- Any row backed by an `active = true`, non-`INSUFFICIENT_DATA` row in
  `BgmiSensitivityProfile` returns non-null `recommendedMin`/`Max`/
  `StartingValue`; any other row returns nulls and
  `confidence: "INSUFFICIENT_DATA"` (or `LOW`), never a fabricated
  number.
- Response always includes the disclaimer text verbatim, exactly as
  specified by the founder.
- Missing `usesGyroscope` → 400 naming the field.

---

### 5.4 Gaming YouTube Title Generator

**User story:** As a creator, I want several non-misleading title
options for my video so I can pick one quickly instead of staring at
a blank field.

**Route:** `/tools/gaming-title-generator`
**Endpoint:** `POST /api/v1/tools/gaming-title-generator`

**Inputs:**
| Field | Type | Required |
|---|---|---|
| game | string | yes |
| topic | string | yes |
| videoType | enum (`HIGHLIGHT`, `TUTORIAL`, `MONTAGE`, `VLOG`, `LIVESTREAM_RECAP`) | yes |
| tone | enum (`HYPE`, `CASUAL`, `COMPETITIVE`, `FUNNY`) | yes |
| keywords | string[] | no |
| save | boolean | no |

**Validation rules:**
- `game` and `topic`: non-blank, max length 100.
- `keywords`: max 10 items, each max length 40, sanitized (strip HTML/
  control characters) before use.

**Request DTO (`TitleGeneratorRequest`):**
```json
{
  "game": "Valorant",
  "topic": "1v5 clutch on Ascent",
  "videoType": "HIGHLIGHT",
  "tone": "HYPE",
  "keywords": ["ace", "clutch"],
  "save": false
}
```

**Response DTO (`TitleGeneratorResponse`):**
```json
{
  "titles": [
    "This 1v5 Clutch on Ascent Should Not Have Worked (Valorant)",
    "INSANE Valorant Ace — 1v5 Clutch on Ascent"
  ],
  "shortFormTitles": ["1v5 ACE?! 🤯 Valorant Clutch"],
  "shareToken": null
}
```

**Business logic:**
- Template engine: a bank of title templates per `(videoType, tone)`
  pair with `{game}`, `{topic}`, `{keyword}` slots; the service fills
  slots, deduplicates, and returns a fixed count (e.g. 8 long-form + 3
  short-form).
- A non-misleading filter step rejects/rewrites any generated title
  containing absolute superlative claims not supported by the input
  (e.g. blocks auto-inserting "WORLD RECORD" unless a keyword implies
  it) — this directly implements the spec's "clickable but non-
  misleading" requirement.
- Zero AI inference cost — pure template composition.

**Input sanitization (expanded per review):** before any field reaches
the template engine —
- Trim leading/trailing whitespace.
- Collapse repeated internal whitespace to a single space.
- Strip control characters and HTML tags (`game`, `topic`, each
  `keyword`).
- This is defense-in-depth, not the frontend's only XSS protection —
  the Next.js frontend must still encode all user-supplied and
  generated strings when rendering (React's default JSX escaping
  covers this as long as `dangerouslySetInnerHTML` is never used on
  these values).

**Error handling:**
- Blank `game` or `topic` → 400 `VALIDATION_ERROR`.
- More than 10 keywords, or any keyword exceeding 40 characters, or
  `game`/`topic` exceeding 100 characters → 400 naming the offending
  field.

**Acceptance criteria:**
- Valid input returns at least 5 unique long-form titles and at least
  3 unique short-form titles, none exceeding YouTube's 100-character
  title limit.
- No generated title contains an unsubstantiated absolute claim.
- Missing `topic` → 400 naming `topic`.
- Input containing `<script>` or excess whitespace is sanitized before
  use; the sanitized value never reaches the template engine verbatim.

---

### 5.5 Gaming YouTube Description Generator

**User story:** As a creator, I want a structured, SEO-friendly
description with hashtags and my social links filled in, so I don't
have to write it from scratch every upload.

**Route:** `/tools/gaming-description-generator`
**Endpoint:** `POST /api/v1/tools/gaming-description-generator`

**Inputs:**
| Field | Type | Required |
|---|---|---|
| game | string | yes |
| topic | string | yes |
| channelName | string | yes |
| keywords | string[] | no |
| socialLinks | array of `{ platform: string, url: string }` | no |
| save | boolean | no |

**Validation rules:**
- `game`, `topic`, `channelName`: non-blank, max length 100.
- `socialLinks[].url`: validated with a custom `@ValidHttpsUrl`
  constraint (Jakarta Validation) rather than regex alone — see below;
  invalid entries reported per-index in `fieldErrors`, not silently
  dropped.
- `keywords`: max 15 items.

**URL validation approach (revised per review):** regex alone is not
sufficient. The DTO field is annotated with a custom constraint:

```java
public class SocialLinkRequest {
    @NotBlank
    private String platform;

    @NotBlank
    @ValidHttpsUrl
    private String url;
}
```

`@ValidHttpsUrl`'s validator parses the string with `java.net.URI`
and rejects it unless: parsing succeeds, `scheme` equals exactly
`"https"`, and `host` is non-null and non-empty. This integrates with
Spring's standard Bean Validation pipeline and produces the same
per-index `fieldErrors` entries as any other `@Valid` failure — no
separate error-handling path is needed for social links.

**Request DTO (`DescriptionGeneratorRequest`):**
```json
{
  "game": "BGMI",
  "topic": "Solo vs Squad chicken dinner",
  "channelName": "EXTREMIS Plays",
  "keywords": ["bgmi highlights", "solo vs squad"],
  "socialLinks": [{ "platform": "YouTube", "url": "https://youtube.com/@extremisplays" }],
  "save": false
}
```

**Response DTO (`DescriptionGeneratorResponse`):**
```json
{
  "description": "In this video, EXTREMIS Plays takes on a full squad solo in BGMI...\n\nFollow EXTREMIS Plays:\nYouTube: https://youtube.com/@extremisplays",
  "seoKeywordsSection": "bgmi highlights, solo vs squad, bgmi gameplay",
  "hashtags": ["#BGMI", "#SoloVsSquad", "#BGMIHighlights"],
  "shareToken": null
}
```

**Business logic:**
- Template composition: intro paragraph (game/topic/channelName),
  keyword section, social links block, hashtag block.
- Hashtags derived from `game` + `topic` + `keywords`, deduplicated,
  capped at 15, formatted per YouTube convention (`#NoSpaces`).

**Error handling:**
- Malformed URL in `socialLinks[i].url` → 400 `VALIDATION_ERROR` with
  `field: "socialLinks[i].url"`.
- Missing `channelName` → 400 naming `channelName`.

**Acceptance criteria:**
- Valid input returns a non-empty `description` containing the
  channel name and all provided social links.
- `hashtags` has no duplicates and never exceeds 15 entries.
- A social link with `url: "not-a-url"` → 400 naming the exact index.

---

## 6. Cross-Tool Acceptance Criteria

- **P95 server-side response time under 300ms under normal expected
  MVP load** (revised per review from an absolute "every request"
  guarantee, which no production environment can honestly promise once
  DB latency, JVM warmup, hosting cold starts, and network variance are
  in play — the calculation logic itself is near-instant; the target
  is a percentile, not an absolute).
- Every tool endpoint is documented in the generated Swagger UI with
  example request/response bodies.
- Every tool endpoint works fully unauthenticated.
- No tool endpoint ever returns a raw stack trace — all errors pass
  through the global exception handler into `ApiErrorResponse`.
- A `save=false` calculation must never fail because of a database
  problem — the calculation path only touches the DB when `save=true`
  (see §7, Reliability).

## 7. Non-Functional Requirements (added per review — REQUIRED CHANGE 6)

**Security:**
- HTTPS enforced in production for both the Next.js frontend and the
  Spring Boot backend.
- No secrets in source control; configuration via environment
  variables / Spring `application.yml` placeholders only.
- Rate limiting on all public endpoints (see §9).
- Request body size limits (e.g. reject bodies over a small fixed
  cap, well above any legitimate tool payload, to blunt trivial abuse).
- Bean Validation (`@Valid`) enforced on every public endpoint — no
  endpoint accepts an unvalidated DTO.

**Reliability:**
- Tool calculation logic is stateless; `save=false` requests have no
  database dependency and must keep working even if the database is
  degraded or unavailable.
- Any unhandled exception is caught by the global
  `@ControllerAdvice` and returns a well-formed `ApiErrorResponse`
  (500 `INTERNAL_ERROR`) — never a raw stack trace or connection
  reset.

**Observability:**
- Structured logging (JSON) for all requests, including a
  per-request correlation/request ID propagated into log lines and
  returned as a response header for support/debugging.
- All handled and unhandled errors logged with their correlation ID.
- Health endpoint via Spring Boot Actuator: `GET /actuator/health`,
  exposed for uptime monitoring (not exposing sensitive detail —
  `management.endpoint.health.show-details` kept minimal in
  production).

## 8. CORS Requirements (added per review)

Since the Next.js frontend and Spring Boot backend are deployed as two
separate applications (Vercel + a separate Java host):

- Spring Security's CORS configuration allows **only the approved
  frontend origin(s)** (production domain + any preview/staging
  domains explicitly listed) — never a wildcard (`*`) in production.
- Preflight (`OPTIONS`) requests are handled for all `/api/v1/**`
  routes with the same origin allow-list.
- Local development uses its own explicit origin entry
  (`http://localhost:3000`), never a blanket allow-all, so the
  production config is exercised in dev too.

## 9. Rate Limiting (added per review)

All limits are per-IP (or another available anonymous device
identifier) via Bucket4j in the Spring Boot layer. **Concrete starting
thresholds (defined now per review — REQUIRED 7, not left as an
architectural placeholder):**

| Endpoint class | Limit | Rationale |
|---|---|---|
| Tool calculation (`save=false`) | 60 requests/minute per IP, burst capacity 20 | Generous — this is the core free product; sized to comfortably cover a real user rapid-testing inputs, while still blocking scripted flooding |
| Tool calculation with `save=true` | 10 requests/minute per IP, burst capacity 5 | Each one writes a `GeneratedResult` + `ToolUsage` row — this is the primary anti-abuse control from REQUIRED CHANGE 1, deliberately much stricter than plain calculation |
| `GET /api/v1/results/{shareToken}` | 30 requests/minute per IP, burst capacity 10 | Shared results are meant to be viewed by others (including people who never used the tool themselves), so looser than `save=true`, but still bounded |

These are **starting values for MVP launch**, not permanently fixed —
they're deliberately conservative-but-workable guesses sized against
expected early traffic (hundreds, not millions, of daily requests),
and should be revisited using real Actuator/logging data once the
site has actual usage (per §7's Observability requirement) rather than
re-guessed again later. Configured via Spring `application.yml`
properties (not hardcoded in Java) so they can be tuned without a
code change and a redeploy.

A rate-limited request returns 429 with the standard
`ApiErrorResponse` shape (`error: "RATE_LIMITED"`).

## 10. Next Step

Phase 2 (System Design) deliverables, once this PRD is approved: full
JPA entity classes (`GeneratedResult`, `SensitivityGameProfile`,
`BgmiSensitivityProfile`, and the rest of the Phase 0 schema), Flyway
migration SQL, the complete OpenAPI spec generated from real
controller code, the 12 signed-off `BgmiSensitivityProfile` rows, the
sourced `SensitivityGameProfile` constants for the initial supported
game list, and the auth/payment/delivery sequence diagrams referenced
in the technical blueprint.
