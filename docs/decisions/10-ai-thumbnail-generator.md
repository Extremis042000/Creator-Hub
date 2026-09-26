# AI Thumbnail Generator — Ideation & Phases

Status: **Phase 40 done and live-verified; Phases 41-44 proposed, not started.**
New tool: generate a YouTube thumbnail from
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

## 3. Phase 40's live verification (2026-09-26) — all three resolved

- **Whether the reference image can be sent inline (base64 data URI) instead
  of a public URL.** ✅ **Yes, confirmed working, and genuinely used** — not
  just accepted-and-ignored. A base64 `data:image/png;base64,...` URI sent as
  the `image` field, paired with a "make the lighting/colour different"
  prompt, came back with the same subject/pose/composition recoloured as
  asked. **This means no new storage infrastructure is needed at all** — the
  uploaded photo goes straight into the API call and is never persisted
  server-side, which is also the better privacy posture by default, exactly
  as this section originally hoped. One real caveat found in the process: the
  gateway enforces its own request-body size ceiling (an nginx-level raw HTTP
  413, not a JSON API error) — a data URI built from a ~1.37MB raw reference
  PNG was rejected; one built from ~120KB succeeded. The exact boundary
  wasn't pinned further, but `OpenAiCompatibleImageProvider.REFERENCE_IMAGE_SAFE_MAX_BYTES`
  (120,000 bytes) is a deliberately conservative line Phase 41's upload path
  must downscale/compress a user-supplied photo below before calling
  `generate()`.
- **Real cost/free-tier ceiling for image generation specifically.** ✅
  **Mapped — no ceiling found at this volume.** 27 real live calls total
  against `qwen-image/z-image-turbo` (4 exploratory + 23 rapid-fire
  back-to-back), zero 402s, zero 429s, steady ~3.7-4.7s latency throughout
  (no slowdown suggesting throttling). Mirrors the Phase 26b-26c text-gateway
  finding: this free tier hasn't shown a real limit at the volumes tested so
  far either. Not exhaustive — revisit if real customer traffic ever 402s.
- **Output size/aspect ratio for real YouTube thumbnails** (1280×720, 16:9) —
  ✅ **Confirmed working and visually good.** A real `size: "1280*720"` call
  returned a genuinely good-quality esports-themed image on the first try,
  downloaded and visually inspected. 512×512 also re-confirmed.

## 4. Architecture

Mirrors the existing `AiGenerationProvider` pattern deliberately, not a
parallel design. Built and live-verified in Phase 40:

- **`ImageGenerationProvider` interface** (`com.extremis.hub.ai`, alongside
  the existing text one) — `generate(ImageGenerationRequest)` →
  `ImageGenerationResult`, throws `ImageGenerationException` on any failure so
  callers can fail clearly (there's no template fallback for an image the way
  Title/Description generation has one).
- **`ImageGenerationRequest`**: prompt, optional reference image (a base64
  data URI or public URL, per §3's now-resolved finding), target width/height,
  and `maxImages` as the cost-ceiling knob — the image path's equivalent of
  `maxOutputTokens`, since an image call has no token budget to cap
  (`usage.input_tokens`/`output_tokens`/`total_tokens` are always 0 on this
  gateway for image calls).
- **`OpenAiCompatibleImageProvider`** implementing it against a dedicated
  `extremis.ai.image.*` config slot (own `generations-url`/`model`, not the
  text path's `extremis.ai.compat.*`, since the request/response shapes
  differ) — `api-key` falls back to the same `AI_API_KEY` already on Render
  via a nested placeholder default, so no new secret is needed for the
  founder's already-configured FreeModel account.
- **Null-bean gate** (`ImageProviderConfig`/`ImageCompatPropertiesConfig`),
  same discipline as `AiProviderConfig`/`AiCompatPropertiesConfig` —
  `Optional<ImageGenerationProvider>` stays empty unless enabled AND all of
  url/key/model are non-blank.
- **Admin smoke-test endpoint** `POST /api/v1/admin/ai/ping-image`, same shape
  as the existing `/ping` for text.
- **Not yet built** (Phase 41): `ImageGenerationLog`-style reuse of Phase 28's
  `AiUsageGuard`/`ai_generation_log` pattern under a new
  `ToolType.THUMBNAIL_GENERATOR`, and the premium-only gate (no free tier at
  all for this tool, unlike Title/Description's $0 template fallback — there's
  no deterministic template equivalent for an image).

## 5. Master phases

*(Renumbered from this doc's original 38-42 — those numbers were since taken
by `09-ai-model-optimization.md`'s Phase 37/38, both shipped before this
doc's phases were started.)*

| Phase | Task | Depends on | Complexity | Completion criteria |
|---|---|---|---|---|
| 40 | `ImageGenerationProvider` + live verification of open questions (§3) | Phase 26's provider pattern | Medium | ✅ **Done and live-verified (2026-09-26).** Real test at 1280×720 (good quality, confirmed); base64 reference images confirmed working AND genuinely used (not ignored), with a real ~120KB-1.37MB size ceiling found and documented (`REFERENCE_IMAGE_SAFE_MAX_BYTES`); free-tier ceiling mapped via 27 real calls, zero 402/429. `ImageGenerationProvider`/`OpenAiCompatibleImageProvider`/config wiring shipped; admin `POST /api/v1/admin/ai/ping-image` smoke test live. 159 backend tests (10 new), 0 failures. |
| 41 | Backend tool service + endpoint (`gaming-thumbnail-generator`) | Phase 40 | Medium | `POST /api/v1/tools/gaming-thumbnail-generator`: game/topic/tone input (matching Title/Description's existing request shape for consistency) + optional reference image, returns a generated thumbnail URL/data. Premium-gated (no free path). Reuses Phase 28's `AiUsageGuard` under a new `ToolType.THUMBNAIL_GENERATOR`. Downscales/compresses any uploaded reference image below `REFERENCE_IMAGE_SAFE_MAX_BYTES` before it ever reaches the provider, and validates upload size/type defensively (never trust a client-supplied content-type). |
| 42 | Prompt construction tying into SEO/description context | Phase 41, Phase 27's Description Generator | Medium | The prompt sent to the image model incorporates the same game/topic/keywords a user would put into the Description Generator, so the thumbnail and the description/hashtags it's paired with are visually and topically consistent — this is the "SEO-friendly, video-context-friendly" part of the founder's ask, achieved through shared input context, not scraped training data. |
| 43 *(optional)* | Live style-reference lookup via the YouTube Data API | Phase 42, founder's own `GOOGLE_SERVICES_SETUP.md`-style API key | Medium | Strictly ephemeral, per-request use (§1) — fetches 2-3 real thumbnails for a similar public search term as transient style context for one generation, never cached. Explicitly optional and clearly gated on being implemented in a way that stays inside YouTube's Developer Policies -- if that can't be done cleanly at build time, this phase is skipped, not forced. |
| 44 | Frontend UI (`/tools/gaming-thumbnail-generator`) + homepage "Coming Soon" flag flip | Phase 41 | Medium | New tool page matching the existing tool-page template (`PremiumToolGate`-wrapped, like `gaming-description-generator`), an image upload control, generated-image preview + download. Once live, the homepage's existing "Coming Soon: AI Thumbnail generator" card (Phase 21's feature flag) is turned into a real link, and the Privacy Policy's existing "planned, not yet built" language (Phase 26c) is updated to reflect it now existing. |

## 6. What's needed from the founder

Nothing to start Phase 41 — it reuses the AI provider credentials already on
Render. Two things worth a decision before Phase 41 ships to real customers:

1. **Confirm premium-only, no free tier, is the right call** for this tool —
   matches the "no template fallback exists for an image" reasoning in §4, but
   it's a monetization choice worth the founder's explicit sign-off, not just
   an assumption.
2. **If Phase 43 (YouTube style-reference lookup) is wanted**, a Google Cloud
   Console API key with the YouTube Data API v3 enabled (same console already
   used for OAuth/Analytics, see `GOOGLE_SERVICES_SETUP.md`) — otherwise Phase
   43 is simply skipped and the tool ships without it.
