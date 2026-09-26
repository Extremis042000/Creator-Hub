# AI Thumbnail Generator — Ideation & Phases

Status: **proposed, not started.** New tool: generate a YouTube thumbnail from
a text description of the video's content, with an optional user-uploaded
reference photo the AI should incorporate or take style cues from. Already
teased on the homepage as a "Coming Soon" feature-flag placeholder (Phase 21)
and named in the Privacy Policy's AI-disclosure section (Phase 26c) as planned
but not yet built — this is that build.

## 1. The founder's own ask needs an honest correction first

The request was to build a training pipeline "using YouTube and other open
sources," comparing thumbnails against subjects and SEO descriptions/hashtags.
Read literally, that means: fetch real YouTube thumbnails + titles/descriptions/
hashtags via the YouTube Data API (or scraping), build a dataset correlating
"what a good thumbnail looks like" with "what topic/description it matched,"
and use that to train or steer the image generator.

**That specific approach is blocked by YouTube's own Developer Policies**,
read directly rather than assumed:

- *"[Developers] must not ... access or use API Data to create new or derived
  data or metrics."* Building a "good thumbnail" training/scoring dataset out
  of API-fetched thumbnails and metadata **is** creating derived data from API
  Data — this clause forbids exactly that use case, not just an edge case of it.
- *Cached "Non-Authorized Data" (public data like another channel's video
  metadata) may be stored "but not longer than 30 calendar days"* before it
  must be deleted or refreshed. A permanent or slowly-refreshed training corpus
  is structurally incompatible with a 30-day hard cap.
- There *is* a real, legitimate "third-party AI training" program YouTube
  runs — but it's an application process for large AI companies, requires
  each individual creator to explicitly opt in per-channel (default: off), and
  is about training on video content with rights-holder consent, not a
  mechanism a tool like this could realistically or appropriately use.
- **Already-published third-party datasets** (e.g. YouTube-thumbnail sets on
  Kaggle/Hugging Face) don't clean this up either — they were themselves
  almost certainly built by scraping outside the API's own terms, and reusing
  them doesn't inherit a license YouTube ever granted. Treat these as a grey
  area to avoid, not a workaround, the same conclusion already reached for
  Slickdeals/camelcamelcamel in `03-development-roadmap.md`'s Phase 18/25 gear-
  deals research.

**What's still genuinely fine, and this doc builds instead:**

- **Ephemeral, per-request use of the YouTube Data API** — at generation time,
  optionally fetch 2-3 real top results for a similar public search term purely
  as transient style inspiration for *that one request*, never cached past the
  30-day ceiling (in practice: never cached at all — used and discarded within
  the same request). This is closer to a live web search than "training," and
  doesn't create any derived dataset. Scoped as optional (§5, Phase 41), not
  required for launch.
- **The image model's own pretraining.** A capable text/vision model already
  has broad exposure to publicly-written, published commentary on thumbnail
  design (contrast, faces, text-overlay conventions, color theory) from its own
  training data — using that existing knowledge via good prompting is not
  "training on YouTube," it's using a general-purpose model the way it's meant
  to be used. This is the actual mechanism behind "SEO-friendly, effective
  thumbnails" in this plan, not a bespoke scraped corpus.
- **First-party feedback**, exactly as described in
  `09-ai-model-optimization.md` §2.4 — this product's own users' copy/refine/
  regenerate signals on their own generated thumbnails, which this product
  unambiguously owns the rights to.

## 2. Real, live-verified finding: the image model is already in hand

Before assuming a new provider was needed, the already-integrated FreeModel
gateway (`alli.website`, same key already configured on Render — see
`07-ai-generation-initiative.md`) was checked for image capability:

- **`GET /v1/models` lists real image models**: `qwen-image/*` (several tiers,
  served by Alibaba's own Qwen-Image/DashScope backend) and `grok-image/*`.
- **Live-tested `qwen-image/z-image-turbo` via `POST /v1/images/generations`**
  (OpenAI-compatible shape: `{model, prompt, n, size}`, size as `"512*512"` not
  `"512x512"` — confirmed by a real 400 error naming the expected format).
  Result: a genuinely good-quality, photorealistic image, on the first real
  call, with **no 402 (paid-tier) error** — this tier appears free-tier
  accessible, matching the existing `fm-v1-lite` pattern for text.
- **Reference-image input works on the same endpoint**: passing an `image` URL
  field alongside the prompt (e.g. "make the controller blue instead" +
  `image: <url of the first generated image>`) produced a second image that
  correctly picked up the requested change while keeping the reference's
  composition/style — real, working image-to-image on the same call shape,
  not a separate, unverified `/edits` endpoint.

This means **no new third-party account, no new API key, no new founder
action** is needed to start building this tool — it reuses the exact
`AI_API_CHAT_URL`/`AI_API_KEY`/`AI_MODEL` pattern already live in production,
just against a sibling `/v1/images/generations` endpoint instead of
`/v1/chat/completions`.

**One real caveat found mid-research, flagged rather than glossed over:** a
*third*, differently-branded FreeModel-lookalike domain (`freemodel.app`, with
its own docs at `docs.freemodel.app`) surfaced during this research, distinct
from both `freemodel.online` and the currently-configured `alli.website` —
adding to the same "multiple FreeModel-branded domains with different
catalogs/behavior" confusion already documented in
`07-ai-generation-initiative.md` §9-10. **Nothing here depends on
`freemodel.app`** — this plan uses only the already-verified `alli.website`
endpoint — but it's worth remembering if `alli.website` ever behaves
differently than expected, rather than assuming a docs page found for a
same-named-but-different domain applies.

## 3. What still needs verifying before Phase 40 ships

- **Whether the reference image can be sent inline (base64 data URI) instead
  of a public URL.** Only tested with a URL so far. This matters because the
  founder's ask is "user can upload a sample photo," and this product has no
  existing image-upload/storage infrastructure (digital products' files sit on
  Render's ephemeral disk, flagged back in Phase 19/23 as needing Cloudflare
  R2 before real scale — the same problem would apply to storing user uploads).
  **If base64 inline works, no new storage is needed at all** — the uploaded
  photo goes straight into the API call and is never persisted server-side,
  which is also the better privacy posture by default. If only public URLs
  work, a short-lived (single-request, auto-expiring) upload path would be
  needed instead — more infrastructure, worth avoiding if the simpler path
  works. This is the first thing to test in Phase 38, before committing to
  either shape.
- **Real cost/free-tier ceiling for image generation specifically.** Text
  generation's free-tier behavior (which models 402, which are actually free)
  was mapped empirically over several live tests in Phases 26b-26c. Image
  generation has had exactly one successful test so far — enough to prove it
  works, not enough to know its real rate limit or whether heavier use
  eventually 402s. Phase 38 includes mapping this the same way text was
  mapped, before this becomes a customer-facing feature people rely on.
- **Output size/aspect ratio for real YouTube thumbnails** (1280×720, 16:9) —
  only tested at 512×512 (a fast, cheap default for a first connectivity
  check). Needs a real test at the actual target resolution before shipping.

## 4. Architecture

Mirrors the existing `AiGenerationProvider` pattern deliberately, not a
parallel design:

- **New `ImageGenerationProvider` interface** (`com.extremis.hub.ai`,
  alongside the existing text one) — `generate(ImageGenerationRequest)` →
  `ImageGenerationResult`, throws `ImageGenerationException` on any failure so
  callers can fail clearly (there's no template fallback for an image the way
  Title/Description generation has one — see §5, Phase 40's error handling).
- **`ImageGenerationRequest`**: prompt, optional reference image (as a data URI
  once §3's question is answered), target size, and the same
  `maxOutputTokens`-style cost ceiling concept if the upstream API has an
  equivalent knob.
- **`OpenAiCompatibleImageProvider`** implementing it against
  `{chatUrl-sibling}/v1/images/generations` — reuses the *same*
  `OpenAiCompatibleProperties`-style config binding already established
  (§2's finding means this can point at the same `alli.website` config, or a
  dedicated `extremis.ai.image.*` slot if the founder wants the text and image
  models configured independently later).
- **New `ImageGenerationLog`-style entry reusing Phase 28's
  `AiUsageGuard`/`ai_generation_log` pattern**, not a parallel cost-control
  system — image generation is likely more expensive per call than text, so
  it needs the *same* rate-limit/daily-ceiling discipline already proven live,
  extended to cover a new `ToolType.THUMBNAIL_GENERATOR`.
- **Premium-gated from day one**, no free tier at all for this tool (unlike
  Title/Description, which keep a $0 template fallback) — there's no
  deterministic "template" equivalent for an image, so this tool simply
  doesn't render for a non-premium user, the same pattern already used for
  `gaming-description-generator`'s tool-level `premiumOnly` flag (Phase 22).

## 5. Master phases

| Phase | Task | Depends on | Complexity | Completion criteria |
|---|---|---|---|---|
| 38 | `ImageGenerationProvider` + live verification of open questions (§3) | Phase 26's provider pattern | Medium | Real test at 1280×720; confirms whether base64 reference images work; maps the free-tier ceiling with several real calls (matching how text generation was mapped). Admin-only smoke test endpoint, same shape as `POST /api/v1/admin/ai/ping`. |
| 39 | Backend tool service + endpoint (`gaming-thumbnail-generator`) | Phase 38 | Medium | `POST /api/v1/tools/gaming-thumbnail-generator`: game/topic/tone input (matching Title/Description's existing request shape for consistency) + optional reference image, returns a generated thumbnail URL/data. Premium-gated (no free path). Reuses Phase 28's `AiUsageGuard` under a new `ToolType.THUMBNAIL_GENERATOR`. Validates upload size/type defensively (never trust a client-supplied content-type) before it ever reaches the provider. |
| 40 | Prompt construction tying into SEO/description context | Phase 39, Phase 27's Description Generator | Medium | The prompt sent to the image model incorporates the same game/topic/keywords a user would put into the Description Generator, so the thumbnail and the description/hashtags it's paired with are visually and topically consistent — this is the "SEO-friendly, video-context-friendly" part of the founder's ask, achieved through shared input context, not scraped training data. |
| 41 *(optional)* | Live style-reference lookup via the YouTube Data API | Phase 40, founder's own `GOOGLE_SERVICES_SETUP.md`-style API key | Medium | Strictly ephemeral, per-request use (§1) — fetches 2-3 real thumbnails for a similar public search term as transient style context for one generation, never cached. Explicitly optional and clearly gated on being implemented in a way that stays inside YouTube's Developer Policies -- if that can't be done cleanly at build time, this phase is skipped, not forced. |
| 42 | Frontend UI (`/tools/gaming-thumbnail-generator`) + homepage "Coming Soon" flag flip | Phase 39 | Medium | New tool page matching the existing tool-page template (`PremiumToolGate`-wrapped, like `gaming-description-generator`), an image upload control, generated-image preview + download. Once live, the homepage's existing "Coming Soon: AI Thumbnail generator" card (Phase 21's feature flag) is turned into a real link, and the Privacy Policy's existing "planned, not yet built" language (Phase 26c) is updated to reflect it now existing. |

## 6. What's needed from the founder

Nothing to start Phase 38 — it reuses the AI provider credentials already on
Render. Two things worth a decision before Phase 39 ships to real customers:

1. **Confirm premium-only, no free tier, is the right call** for this tool —
   matches the "no template fallback exists for an image" reasoning in §4, but
   it's a monetization choice worth the founder's explicit sign-off, not just
   an assumption.
2. **If Phase 41 (YouTube style-reference lookup) is wanted**, a Google Cloud
   Console API key with the YouTube Data API v3 enabled (same console already
   used for OAuth/Analytics, see `GOOGLE_SERVICES_SETUP.md`) — otherwise Phase
   41 is simply skipped and the tool ships without it.
