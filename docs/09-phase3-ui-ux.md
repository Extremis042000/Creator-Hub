# EXTREMIS Creator Hub — Phase 3: UI/UX

Covers every screen the technical blueprint's Phase 3 scope requires:
homepage, tools directory, the shared tool-page template, dashboard,
login, pricing, store, product page, checkout success, and admin
shell. Mobile-first, dark gaming aesthetic, accessible, SEO-friendly —
per `02-technical-blueprint.md` §"Styling" and the PRD's tool specs.

**UI-shell rule (carried over from Phase 2 review — applies to every
screen below):** a screen may exist as a static shell before its
backing feature is built, but it must never *imply* a live capability
that doesn't exist yet. Each section below is explicitly labeled
**LIVE** (backed by a real Phase 4 endpoint) or **SHELL** (visual
only, clearly marked as not-yet-available in the UI itself, not just
in this document).

---

## 1. Design System Foundations

### 1.1 Brand direction

Per the technical blueprint: premium gaming, modern, fast, minimal,
competitive, creator-focused — explicitly **not** a generic AI-SaaS
look (no generic gradient-blob hero, no generic rounded-everything
dashboard aesthetic). Visual references to aim for: competitive-gaming
tool sites and esports org sites — high contrast, sharp edges mixed
with a few angled/diagonal accent shapes, monospace accents for
numeric data (sensitivity values, KD ratios), restrained motion.

### 1.2 Color tokens (dark-first; the whole product ships dark-only for MVP — no light theme toggle, since a light "gaming tool" reads as off-brand and it removes a whole QA surface for a $0-budget MVP)

```css
:root {
  /* Surfaces */
  --bg-canvas:      #0a0b0f;   /* page background */
  --bg-surface:     #12141a;   /* cards, panels */
  --bg-surface-alt: #191b22;   /* input fields, nested panels */
  --bg-elevated:    #20232c;   /* modals, dropdowns */

  /* Brand */
  --brand-primary:    #ff3b3b;  /* EXTREMIS red — CTAs, active states */
  --brand-primary-hover: #ff5c5c;
  --brand-accent:     #3bd6ff;  /* cool accent — links, secondary highlights */

  /* Text */
  --text-primary:   #f4f5f7;
  --text-secondary: #9ca0ab;
  --text-muted:     #5c6070;

  /* Confidence / status (BGMI + sensitivity confidence badges) */
  --status-high:    #33d17a;   /* HIGH confidence */
  --status-medium:  #ffb020;   /* MEDIUM confidence */
  --status-low:     #ff8a3d;   /* LOW confidence */
  --status-none:    #6b7280;   /* INSUFFICIENT_DATA / inactive */

  /* Borders */
  --border-subtle:  #262933;
  --border-strong:  #383c47;
}
```

### 1.3 Typography

- Display/headings: a condensed, slightly-aggressive sans (e.g. a
  self-hosted or Google Fonts variable font — pick one at
  implementation time that's free-licensed) — used sparingly, only
  for hero/section headers, never for body copy.
- Body/UI: a clean system-adjacent sans (Inter or similar) for
  readability across the SEO content blocks — this is a content site
  first, and body text must stay highly legible.
- Numeric/data (sensitivity values, KD ratios, DPI, cm/360, gyro
  percentages): a monospace font (e.g. JetBrains Mono / Fira Code) —
  gives calculator outputs a "precision tool" feel and makes numbers
  easy to scan/copy.

### 1.4 Core components (built once in the shared UI kit, per roadmap Phase 6)

- `Button` — primary (filled, brand-primary), secondary (outline),
  ghost (text-only); all with a visible focus ring (accessibility).
- `Card` — used for tool cards, result panels, product cards.
- `Input` / `Select` / `Toggle` — dark-surface form controls with a
  clear focus/error state (red outline + inline message, matching the
  API's `fieldErrors` shape one-to-one so validation errors map
  directly onto the field that caused them).
- `ConfidenceBadge` — small pill component: `HIGH` (green),
  `MEDIUM` (amber), `LOW` (orange), `INSUFFICIENT_DATA`/inactive
  (grey, with a tooltip: "not enough data yet"). Used on every BGMI
  recommendation row and could extend to a sensitivity-conversion
  "community-verified vs. official" indicator later.
- `CopyButton` — copies a result value/link, shows a brief
  "Copied!" confirmation (client-side only, no backend call).
- `ShareLinkBox` — shown after `save=true`; displays the
  `/results/{shareToken}` URL with a copy action.
- `Disclaimer` — a consistently-styled callout block, used verbatim
  wherever the API returns a `disclaimer` field, so the UI never
  paraphrases or drops it.
- `ComingSoonBadge` — used on every SHELL screen element (see §1
  above) — a small, unmissable "Coming soon" tag, not a disabled-
  looking-but-ambiguous button.

---

## 2. Homepage — **LIVE**

```
┌──────────────────────────────────────────────────────────┐
│  EXTREMIS CREATOR HUB          [Tools] [Blog] [Sign in]▸  │ ← sticky nav
├──────────────────────────────────────────────────────────┤
│                                                            │
│   FREE TOOLS FOR COMPETITIVE PLAYERS & GAMING CREATORS    │ ← H1, condensed display font
│   Sensitivity converters, KD tracking, title generators — │
│   no signup required.                                     │
│                                                            │
│   [ Browse all tools → ]                                  │ ← primary CTA → /tools
│                                                            │
├──────────────────────────────────────────────────────────┤
│  FEATURED TOOLS (5 cards, responsive grid → 1-col mobile) │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐             │
│  │ Valorant   │ │ BGMI       │ │ KD Ratio   │  ...         │
│  │ Sensitivity│ │ Sensitivity│ │ Calculator │             │
│  │ Converter  │ │ Helper     │ │            │             │
│  │ [Try it →] │ │ [Try it →] │ │ [Try it →] │             │
│  └────────────┘ └────────────┘ └────────────┘             │
├──────────────────────────────────────────────────────────┤
│  WHY EXTREMIS (3-column trust section: Free, No signup,   │
│  Built by a gaming creator — links EXTREMIS Plays brand)  │
├──────────────────────────────────────────────────────────┤
│  FOOTER — About / Privacy / Terms / Contact / EXTREMIS     │
│  Plays social links                                        │
└──────────────────────────────────────────────────────────┘
```

Fully static-generated (SSG) for SEO. Tool cards link directly to each
`/tools/{slug}` page. No login required anywhere on this page.

---

## 3. Tools Directory — **LIVE**

`/tools`

```
┌──────────────────────────────────────────────────────────┐
│  All Tools                                                 │
│  [ Search tools... ]     Filter: [All] [Competitive] [Creator] │
├──────────────────────────────────────────────────────────┤
│  ┌────────────┐ ┌────────────┐ ┌────────────┐             │
│  │ (tool card)│ │ (tool card)│ │ (tool card)│   grid,      │
│  └────────────┘ └────────────┘ └────────────┘   1-col at   │
│  ┌────────────┐ ┌────────────┐                  <640px    │
│  │ (tool card)│ │ (tool card)│                             │
│  └────────────┘ └────────────┘                             │
└──────────────────────────────────────────────────────────┘
```

Each card: tool name, one-line description, category badge
(`competitive`/`creator`, matching `Tool.category`), "Try it" CTA.
Search/filter are client-side only (5-10 tools at MVP scale — no need
for a backend search endpoint). SSG with the tool list baked in at
build time from the `Tool` table (a small build-time fetch script,
not a client-side API call, to keep this page's SEO value maximal).

---

## 4. Shared Tool Page Template — **LIVE**

Applies to all 5 MVP tools at `/tools/{slug}`. Structure is identical
across tools; only the input form and result panel differ per tool.

```
┌──────────────────────────────────────────────────────────┐
│  Breadcrumb: Tools / Valorant Sensitivity Converter         │
├──────────────────────────────────────────────────────────┤
│  H1: Valorant Sensitivity Converter                         │
│  One-line description + last-updated note if relevant       │
├───────────────────────────┬──────────────────────────────┤
│  INPUT FORM                │  RESULT PANEL                 │
│  (tool-specific fields)    │  (empty state until submit,   │
│  [ Convert → ]              │   then populated result +     │
│                             │   [Copy] [Share] actions)     │
│  Disclaimer block, if the  │                                │
│  tool's API response       │  On save=true: ShareLinkBox    │
│  includes one (BGMI: yes;  │                                │
│  sensitivity: no)          │                                │
├───────────────────────────┴──────────────────────────────┤
│  HOW TO USE  (numbered steps, original written content)     │
├──────────────────────────────────────────────────────────┤
│  FAQ  (accordion, 4-6 original Q&As per tool — required by  │
│  the technical blueprint's SEO/thin-content rules)           │
├──────────────────────────────────────────────────────────┤
│  RELATED TOOLS (2-3 cards, cross-links within the tool set) │
└──────────────────────────────────────────────────────────┘
```

Mobile: input form and result panel stack vertically (form first,
result below it, so submitting scrolls naturally to the answer rather
than requiring the user to scroll back up).

**Per-tool input/result specifics** (fields match `06-prd.md` §5
exactly — this doc does not redefine them, only lays out where they
go):

| Tool | Input form fields | Result panel content |
|---|---|---|
| KD Ratio Calculator | Kills, Deaths (2 number inputs) | Large KD ratio display (or "Perfect / No Deaths" / "No Data" per §5.1's `displayValue`), performance-category badge |
| Valorant Sensitivity Converter | Source game (select), Target game (select), DPI, Source sensitivity | Target sensitivity (large), eDPI, cm/360 (source vs. target, shown equal — a small visual "✓ physical distance matched" note reinforces *why* the numbers differ) |
| BGMI Sensitivity Helper | Single toggle: "I use gyroscope" (yes/no) | 5-row table (one per `scopeLevel`), each row: setting name, recommended range, starting value, `ConfidenceBadge`. Disclaimer block always visible above the table, not hidden in a tooltip. |
| YouTube Title Generator | Game, Topic, Video type (select), Tone (select), Keywords (tag input, max 10) | List of long-form titles + short-form titles, each with its own `CopyButton` |
| YouTube Description Generator | Game, Topic, Channel name, Keywords (tag input), Social links (repeatable platform+URL rows) | Formatted description block (monospace-ish, preserves line breaks) with one `CopyButton` for the whole thing, plus a separate copy action for just the hashtag line |

Every tool page's input form calls its real `POST /api/v1/tools/...`
endpoint from Phase 4 — this page is genuinely **LIVE** once that
endpoint exists; there is no scaffolded/fake version of a tool result.

---

## 5. Dashboard — **SHELL**

`/dashboard` (only reachable after clicking "Sign in," which itself is
a SHELL — see §7)

```
┌──────────────────────────────────────────────────────────┐
│  Welcome back                              [Coming soon]   │ ← ComingSoonBadge, unmissable
├──────────────────────────────────────────────────────────┤
│  "Your saved results and account features are launching    │
│   soon. For now, every tool works without an account —      │
│   just use [Browse tools →]."                                │
└──────────────────────────────────────────────────────────┘
```

No saved-history list, no real account data — because there is no
authentication yet to produce any. Building a fake list here would
violate the UI-shell rule directly.

---

## 6. Login — **SHELL**

`/login`

```
┌──────────────────────────────────────────────────────────┐
│  Sign in to EXTREMIS Creator Hub                            │
│                                                              │
│  [ Continue with Google ]  (button visually styled, but      │
│                              disabled + labeled "Coming soon" │
│                              — see note below)                │
│                                                              │
│  "You don't need an account to use any tool. Sign-in is     │
│   only needed for saved history and premium features,        │
│   launching in a future update."                              │
└──────────────────────────────────────────────────────────┘
```

The Google button renders in its final visual style (so the design is
validated now) but is `disabled` with a `ComingSoonBadge` — clicking
it does nothing, rather than starting an OAuth flow that doesn't exist
yet or, worse, silently failing. This is the literal application of
the review's rule #29: the button *looks* like the real thing so the
design doesn't need rework later, but it cannot *act* like the real
thing before Phase 16.

---

## 7. Pricing — **SHELL**

`/pricing`

```
┌──────────────────────────────────────────────────────────┐
│  Free forever. Premium coming soon.                         │
├───────────────────────┬────────────────────────────────┤
│  FREE                  │  PREMIUM                          │
│  ✓ All 5 tools          │  Advanced generators               │
│  ✓ No signup            │  Saved history                     │
│  ✓ Unlimited sharing    │  Higher usage limits                │
│                          │  [ Notify me → ]  (email capture,   │
│                          │   NOT a checkout button)            │
└───────────────────────┴────────────────────────────────┘
```

No price is quoted for Premium yet (pricing itself is a Phase 22
decision, not a Phase 3 one). The "Notify me" action is a simple email
capture (stored as an `EmailEvent`-adjacent lead record, or even just
an honest `mailto:` link at MVP) — it must not look like or lead into
a checkout flow.

---

## 8. Store / Product Listing — **SHELL**

`/store`

```
┌──────────────────────────────────────────────────────────┐
│  Digital products — launching soon                [Coming soon] │
├──────────────────────────────────────────────────────────┤
│  Preview cards (greyed/lower-opacity) for the founder's     │
│  planned product categories: thumbnail packs, overlays,     │
│  creator templates — each card says "Coming soon," none      │
│  are clickable into a real product/checkout page.            │
└──────────────────────────────────────────────────────────┘
```

Exists so the store's eventual layout is validated early and so the
homepage/nav can link somewhere real instead of a 404 — but it must
not present any product as purchasable.

---

## 9. Product Page + Checkout Success — **SHELL**

`/store/{product-slug}` and `/checkout/success` — designed (so Phase
5's real implementation has a target to build into) but not linked
from anywhere live yet, since there are no real `Product` rows to
point to. No fake "Buy now" button that leads to a real payment form —
per the review's rule, no fake payment, ever, even in a shell.

---

## 10. Admin Shell — **SHELL**

`/admin` (not linked from any public nav; reachable only by direct
URL, and even then gated — see below)

```
┌──────────────────────────────────────────────────────────┐
│  Admin — [Coming soon]                                      │
│  "Admin tooling launches with Phase 17. This route will      │
│   require an authenticated AdminUser."                        │
└──────────────────────────────────────────────────────────┘
```

Deliberately minimal — a full admin dashboard mockup isn't useful
design work yet (its real requirements depend on Phase 17-22 scope,
which hasn't started), and building a convincing-looking fake admin
panel is exactly the kind of "fake capability" the review flagged.
This route should also **not** be discoverable via the public nav or
sitemap — it's a placeholder for a future authenticated area, not a
public page.

---

## 11. Accessibility Requirements

- All interactive elements (buttons, inputs, tag-input chips,
  accordion headers) have a visible focus ring meeting WCAG 2.1 AA
  contrast against `--bg-canvas`/`--bg-surface`.
- Every `ConfidenceBadge` and `Disclaimer` has real text content (not
  color-only meaning) — confidence is never conveyed by badge color
  alone, always paired with the word (`HIGH`/`MEDIUM`/`LOW`).
- Form validation errors are associated with their field via
  `aria-describedby`, matching the API's `fieldErrors[].field` so a
  screen-reader user gets the same specificity a sighted user does.
- `ComingSoonBadge` elements use `aria-disabled="true"` on their
  parent control, not just a visual dimming, so assistive tech
  correctly announces the control as unavailable.
- Color contrast for body text (`--text-secondary` on `--bg-surface`)
  must be verified ≥ 4.5:1 at implementation time — the token values
  above are a starting point, not a guarantee, and should be checked
  with a real contrast tool once the exact hex values are finalized.

## 12. Responsive Behavior

- Breakpoints: mobile-first, single-column below 640px, 2-column
  tool-directory/homepage grids from 640-1024px, full grid above
  1024px.
- The tool-page template's two-column input/result layout (§4)
  collapses to a single column (form → result) below 900px.
- Sticky nav collapses to a hamburger menu below 640px; "Sign in"
  stays visible (not hidden inside the hamburger) since it's a
  primary-ish action even while it's a SHELL.

## 13. Next Step

Phase 4 (MVP Development), per `03-development-roadmap.md`: project
init (two repos/modules), database + Flyway migrations (V1-V4, the
last two now containing the activated data from §6 of
`07-phase2-system-design.md`), shared UI kit implementing the design
tokens/components above, then the homepage → tools directory → 5
tools in the roadmap's stated order, building each **LIVE** screen
against its real Spring Boot endpoint from day one — no tool ships
with mocked/hardcoded results standing in for the real API.
