# AI-Powered Generation — Ideation & Master Phases

Status: **Phases 26/26b/26c/27/28/29 built, deployed, and live-verified; Phase 30
proposed.** Implementation note: the direct
Anthropic path uses the **official Anthropic Java SDK**
(`com.anthropic:anthropic-java`); the openai-compatible path (the one actually
live today) is a plain `RestClient` call, since arbitrary gateways don't ship a
Java SDK. See §8-9 for the real provider work and what was measured live.

## 1. Where this came from

The founder has an older project, **Triage Desk** (`lms-ticket-bot`), that talks to
Claude for real production work (Redmine ticket investigation). Its architecture was
documented directly from source in `Claude CLI and standard Large Language Model
(LLM) working/` (6 files, read in full for this plan). The founder asked: replicate
that same interaction pattern here, so the two content-generation tools stop
producing templated, "manipulated hardcoded" output and start producing real,
context-aware Claude output.

## 2. What Triage Desk actually does (the honest summary)

Triage Desk has **exactly one** mechanism for talking to Claude: it spawns the local
`claude` CLI binary as a subprocess (PowerShell `& claude @args`, or Node
`spawn('claude', args)`). There is no direct call to `api.anthropic.com` anywhere in
that codebase. What varies is *how that one CLI is configured* — three tiers, in
increasing order of "how agentic is this":

| Tier | Mechanism | Session | Tools | Closest analogy |
|---|---|---|---|---|
| **1. Single-agent triage** | `claude -p <prompt> --output-format json --tools Read,Grep,Glob --no-session-persistence --max-budget-usd N --effort medium` | None — fresh process, one shot | Narrow allowlist | A plain LLM completion call |
| **2. Deep investigation** | Same `-p` one-shot pattern, run 3× (Agent A → Agent B → Synthesis), text from each stage pasted into the next prompt | None across stages | Broad denylist (everything but writes/Bash) + read-only browser MCP | A scripted, unattended Claude Code agent run |
| **3. Live agent chat** | `claude -p --input-format stream-json --output-format stream-json --include-partial-messages`, process kept alive, stdin/stdout stay open | Full, for the session's lifetime | Same denylist as Tier 2 | A human's own interactive Claude Code terminal |

Triage Desk's own documentation makes an important, directly-relevant observation
about Tier 1: *"this call could be replaced with a direct call to the Anthropic
Messages API... and the app's behavior would barely change — the only things
actually gained from routing this narrow case through the Claude Code CLI instead
are: reusing already-configured local auth, JSON-shaped output, and the
`--max-budget-usd` spend guard."* That single sentence is the crux of the
architecture decision below.

## 3. The honest architecture-fit question

Triage Desk runs on **the founder's own machine** — one trusted human operator, a
local git checkout of ~230 repos, a locally-authenticated Claude Code CLI session
(a subscription login, not per-token API billing), and a Windows Scheduled Task.
None of that matches EXTREMIS Creator Hub's actual deployment:

- **Backend runs on Render** (a remote, ephemeral, headless container) — not the
  founder's own machine. The `claude` CLI needs an authenticated session to run at
  all; that authentication is normally an interactive browser OAuth flow tied to a
  personal Claude subscription, which doesn't survive in a stateless cloud
  container the way it does on a machine the founder is logged into every day.
- **This is a public, multi-tenant product**, not a single-operator internal tool.
  Spawning a subprocess with any real tool access, triggered indirectly by
  anonymous customer input, is a materially larger attack surface than a bounded
  HTTPS call — and Render's $0 free tier has none of the sandboxing this would
  really want.
- **Cost model mismatch:** a personal Claude subscription's usage terms aren't
  meant for reselling generated content to unknown third-party customers at scale
  — the same category of concern already surfaced this session for Vercel's Hobby
  ToS. A real business needs metered, billable API usage tied to a business
  Anthropic account, not a personal subscription's included quota.

**Recommendation: replicate the *pattern*, not the *mechanism*.** Implement Tier 1
(the only tier that actually fits this product's tools) as a direct call to the
real **Anthropic Messages API** over HTTPS — same shape as every other outbound
integration already in this codebase (CheapShark, PhonePe, Sentry): one prompt in,
one structured result out, no tools, no session, a bounded `max_tokens` (this
product's equivalent of `--max-budget-usd`). This is deployable on Render exactly
as-is, with zero new infrastructure, using the same "safe, inert-until-configured"
dual-switch pattern (`ANTHROPIC_API_KEY` unset → feature inert, existing template
engine remains the fallback) already proven across GA4/AdSense/Sentry/PhonePe.

Tiers 2 and 3 don't currently have a natural home in this product:
- **Tier 2 (adversarial multi-agent investigation)** exists to cross-check a
  debugging hypothesis against real code. There's no equivalent "is this correct?"
  question for a generated YouTube title — forcing this tier in would be
  complexity with no real payoff. Noted as a non-goal, not silently dropped.
- **Tier 3 (live streaming chat)** *does* have a real, later use case: an
  "iterate on this result" conversation ("make it punchier," "shorter") layered on
  top of a generated title/description. Scoped as an optional future phase (§6),
  not part of the initial build.

If the founder specifically wants to preserve the CLI-subprocess mechanism itself
(e.g. to reuse an existing Claude subscription's included usage rather than pay
per-token), that's only realistic if the backend runs somewhere with a persistent,
authenticated local CLI session under the founder's control — a self-hosted
machine/VM, not Render. That's a real, available option, just a different (and
currently unplanned) hosting decision — flagged here rather than assumed.

## 4. Which tools this actually applies to

Not all 5 tools are good candidates. Checked against the actual current
implementation (`TitleGeneratorService.java`, `DescriptionGeneratorService.java`,
etc. — pure `String.replace()` slot-filling into a fixed template bank, zero AI
today):

| Tool | Nature | AI candidate? |
|---|---|---|
| KD Ratio Calculator | Deterministic arithmetic | **No** — there's one correct number; nothing to generate |
| Valorant Sensitivity Converter | Deterministic math conversion | **No** — same reasoning |
| BGMI Sensitivity Helper | Structured lookup table | **No** — not free-text generation |
| Gaming YouTube Title Generator | Template slot-filling from free-text `topic` | **Yes** — exactly the "read the user's message" case the founder described |
| Gaming YouTube Description Generator | Template composition from free-text `topic` | **Yes** — same reasoning |

The initiative is scoped to these two "creator" tools. The three "competitive"
tools stay exactly as they are — matching the project's standing rule against
building capability nothing actually needs.

## 5. The monetization insight this unlocks

Real AI generation costs real money per call; the current templates cost nothing.
Making AI generation free-and-unlimited on a public, anonymous endpoint is a genuine
new risk this project hasn't had before (a script hammering the free title
generator could rack up a real bill with no revenue behind it). Rather than solving
that with a blunt rate limiter alone, **gate the AI-generation path behind the
premium entitlement system already built in Phase 22**:

- **Free tier (unchanged):** the existing deterministic templates — still fully
  functional, still fast, still $0 marginal cost. Nothing free gets worse.
- **Premium:** real Claude-generated titles/descriptions, actually reading the
  user's specific topic/context rather than slot-filling a fixed phrase bank.

This turns the AI feature into the first concrete, tangible reason to want premium
access at all (today premium-gating exists as infrastructure with nothing
compelling behind it yet, per `MONETIZATION.md` §6) — directly serving the
founder's own stated goal ("create great impact with our customers") while solving
the cost-control problem in the same design decision, at no extra engineering cost.

## 6. Master phases

| Phase | Task | Depends on | Complexity | Completion criteria |
|---|---|---|---|---|
| 26 | AI generation provider abstraction (backend) | Phase 20's `PaymentProvider` pattern | Medium | `AiGenerationProvider` interface + `ClaudeGenerationProvider` (real Anthropic Messages API call, structured-JSON prompting, bounded `max_tokens`) + `AiProviderConfig` (null-bean gate on `ANTHROPIC_API_KEY`, same pattern as every other integration). Zero visible behavior change yet — this phase is the plumbing, verified by a real (small, cheap) live API call succeeding and a clear inert-when-unset path. |
| 27 | Title & Description Generator AI upgrade | Phase 26 | Medium | Both services call the AI provider when available and premium-entitled; validate the structured response (right shape, right length limits, still passes the existing "no unsubstantiated absolute claims" review the template bank already guarantees); fall back to the existing template engine on any AI failure, timeout, or malformed response — the tool must never break for a customer even if the AI call fails. Non-premium/no-provider path is pixel-for-pixel today's behavior. |
| 28 | Cost & abuse controls | Phase 27 | Medium | Per-user/IP rate limiting on the AI path; a daily spend ceiling with automatic circuit-breaker to the template fallback once hit (this product's version of `--max-budget-usd`); a new `ai_generation_log` table (prompt metadata, tokens, cost, latency, success/failure — not full prompt/response text, to avoid storing unnecessary user content) for real cost visibility; a small "AI spend today" card in the admin panel. |
| 29 *(optional, later)* | Interactive refine chat | Phase 27 | Medium-High | A Tier-3-style follow-up: after a generation, let the user say "make it punchier" / "shorter" and get a revised result in the same context — short-lived server-side session (not a full CLI subprocess; same Messages API, just carrying conversation turns), closed automatically after inactivity or a small turn cap. Explicitly deferred — not required for the initial launch of real AI generation. |
| 30 *(optional, only if ever needed)* | Tool-augmented generation | Phase 29 | Unscoped | Only relevant if a future feature genuinely needs the model to *do* something (e.g. look up real trending hashtags) rather than just generate text. Evaluate the Anthropic Agent SDK then, on its merits, against whatever that concrete need turns out to be — not speculated further here. |

## 7. Founder action needed before Phase 26 can go live

Same pattern as every other paid integration this session (Sentry, PhonePe): an
Anthropic Console account with billing enabled, to get a real `ANTHROPIC_API_KEY`.
Nothing in Phases 26-27 requires that key to exist yet — the whole thing is built
inert-by-default and can be developed, tested (with a placeholder/sandbox check),
and deployed before the key is ever supplied, exactly like PhonePe was.

## 8. Provider decision: FreeModel (founder-chosen, 2026-09-22) — ideation & guardrails

The founder chose **FreeModel** (`freemodel.online`) as the AI backend instead of a
direct Anthropic key. The `AiGenerationProvider` interface was built for exactly this
swap. What was verified (public site + its unauthenticated `GET /v1/models`, not the
logged-in console):

- **What it is:** an aggregator gateway. OpenAI-compatible endpoint
  `https://freemodel.online/v1`, Anthropic-compatible `https://freemodel.online/api/gateway`,
  `sk-` keys. "Auto" routing "tries free models first, and falls back automatically"
  — i.e. the model behind a call can change from call to call. 490 model IDs are
  listed publicly (the dashboard says ~3,000). Model IDs include `auto/*` combos and
  explicit Claude IDs (`dva/claude-opus-5-low`, ...).
- **Not stated anywhere public:** privacy/retention, commercial-use terms, rate
  limits, SLA, per-model pricing (the model list has no price field).
- **Where the "free" comes from is the real risk.** The `owned_by` field on the
  model list shows upstreams such as `duckduckgo-web`, `felo-web`, `veoaifree-web`,
  `cloudflare-playground`, `codex-app-server`, `devin-cli-agentic`, `auggie`,
  `aihorde` (a volunteer network). Those are consumer web chat UIs, coding-agent
  CLIs and community networks wrapped as an API — not official commercial APIs.
  Such upstreams break or get blocked without notice, and routing paying customers'
  inputs through them is ToS-uncertain and privacy-uncertain. Provenance of the
  Claude IDs is unverified. (Similarly named sites — freemodel.dev / .app — exist;
  one review says a free tier there ended in Sep 2026. Confirm the console's own
  terms rather than assuming.)

**Decision: proceed, but architect it as a swappable, low-trust backend.**

1. **One generic `OpenAiCompatibleGenerationProvider`** (plain `RestClient` POST to
   `{baseUrl}/chat/completions`; base URL, key, model, timeout all config). FreeModel
   is one configuration of it; **Cloudflare Workers AI** (official, OpenAI-compatible,
   10,000 free neurons/day on the account we already have, ToS-clean for the platform
   — confirm Workers AI commercial terms) or OpenRouter are alternates by changing
   three env vars, no code change. The Anthropic-SDK provider stays for a future
   direct key. An `AI_PROVIDER` setting picks which one is live.
2. **Templates are always the fallback** and the AI call is never on the critical
   path: short timeout (~20s), zero/one retry, any error/empty/malformed output →
   current template result. Free-first routing means variable latency; this makes it
   harmless.
3. **Pin a model, don't use `auto/*` for production quality.** Run a small bake-off
   (same 5 title/description prompts across 3-4 candidate models) and pick by
   measured output; `auto/*` can silently swap models and change brand quality.
4. **Tolerant output handling** — arbitrary models ignore "JSON only": strip code
   fences and `<think>` blocks, parse leniently, validate shape/length/banned-claim
   rules, else fall back.
5. **Data minimisation + disclosure:** send only the tool's own fields (game, topic,
   tone, keywords, channel name); never account identifiers. Update the Privacy
   Policy to disclose third-party AI processing before this goes live.
6. **Key hygiene:** `FREEMODEL_API_KEY` only as a Render env var; never committed.
7. **Premium gating stays** (Phase 27): with a free backend the *cost* argument
   weakens, but quota exhaustion, abuse and the differentiation argument remain.

Founder actions (see chat for the exact steps): create a key in the console; read the
console's Guide/terms for commercial use, retention and limits; run one Playground
test; confirm comfort with the risk above.

## 9. Live verification, Free.ai fallback, and the round-robin provider (2026-09-22)

**FreeModel's console `/guide` page is client-side rendered and login-gated** — a
direct fetch returns only an empty shell (title, no body). It could not be read
without the founder's own logged-in browser; still unread as of this entry.

**The founder's key round-tripped against the *wrong* domain at first.** The
console's own quick-start snippet uses `https://alli.website/v1`, not
`https://freemodel.online/v1` researched in §8 — different DNS (`alli.website`
resolves to a single IPv4; `freemodel.online` sits behind Cloudflare) and a
different, smaller public model list (207 vs 490 ids). Both were checked live before
sending the real key anywhere. Only `alli.website` accepted it.

**Real calls against `alli.website`, with the founder's key:**
- `fm-v1-lite` works, ~2-4s per call. `fm-v1-standard` / `fm-v1-pro` return HTTP 402
  (paid points only) — confirmed by calling all three.
- The model actually serving `fm-v1-lite` (per the response's own `model` field) is
  `inclusionai/ling-3.0-flash-sante:free` — a free upstream, not Claude. Output
  quality on 3 real title-generation prompts was template-comparable, not frontier.
- It is a reasoning model: at `max_tokens=900`, 2 of 3 real calls returned **empty
  content** (`finish_reason=length` — the budget was consumed by hidden reasoning
  before any visible output). At `max_tokens=3000`, 5 of 5 calls returned valid JSON.
  `OpenAiCompatibleProperties.maxTokensFloor` (default 3000) exists specifically
  because of this measured result, not a guess.

**Config semantics corrected:** the original design assumed every gateway follows
one URL convention (`{base}/chat/completions`) and could store just a base URL.
Reading the founder-supplied Free.ai documentation (`Free.ai API Documentation.html`)
showed a second, real, differently-shaped convention: Free.ai's chat endpoint is
`https://api.free.ai/v1/chat/` — no `completions`, a trailing slash. So
`OpenAiCompatibleProperties.baseUrl` (implicit suffix) was renamed to `chatUrl` (the
exact, complete endpoint) — this is a breaking rename from Phase 26b's original
`AI_API_BASE_URL`, now `AI_API_CHAT_URL`; the Render value had to be corrected from
`https://alli.website/v1` to `https://alli.website/v1/chat/completions` accordingly.

**Free.ai, from the founder's own documentation (not yet live-verified — no key
supplied for it):** `POST https://api.free.ai/v1/chat/`, `Authorization: Bearer
sk-free-...`, free plan = 30,000 tokens/day pool + 10 requests/minute, self-hosted
free model id `qwen7b`. Its response wraps usage under `free_ai_usage`, not the
standard `usage` key `OpenAiChatResponse` reads — text extraction still works, but
`inputTokens`/`outputTokens` read back as 0 for this specific backend (a documented,
accepted cosmetic gap, not worth a per-provider parser for a cost-visibility-only
field).

**`CompositeAiGenerationProvider` (round-robin + failover), per the founder's
explicit ask to "equally distribute the load":** `AiProviderConfig` now binds
`OpenAiCompatibleProperties` at **two** prefixes (`extremis.ai.compat` /
`extremis.ai.compat2`, via a separate `AiCompatPropertiesConfig` — binding two
`@Bean`-producing methods for the same properties class *inside* `AiProviderConfig`
itself would be a circular dependency, since Spring must construct that class via its
constructor before it can call any of its own `@Bean` methods; caught before it ever
reached a running context). Neither slot configured → inert (unchanged). Exactly one
configured → that provider directly, no wrapping (today's live state: FreeModel only).
Both configured → wrapped in `CompositeAiGenerationProvider`, which starts each call
at the next delegate in rotation (genuinely round-robin, not "primary until it dies")
and falls over to the other delegate on any `AiGenerationException` before surfacing a
failure. 5 new tests cover even distribution across 10 calls, fail-over, and
all-delegates-failed. 76 backend tests total, 0 failures, including the real Spring
context booting with both compat-properties beans present.

**Verified live end-to-end in production after the fix:** admin ping →
`{"provider":"openai-compatible","reply":"pong","model":"fm-v1-lite","servedBy":
"inclusionai/ling-3.0-flash-sante:free", ...}`; health up; a pre-existing endpoint
(`/api/v1/game-deals`) unaffected, confirming no regression.

**Privacy Policy updated** with a new, positively-framed "Smarter results from
AI-assisted tools" section naming the two live AI-candidate tools (Title Generator,
Description Generator) and the planned-but-not-yet-built AI Thumbnail Generator
(currently only a "Coming Soon" feature-flag placeholder — the policy is careful not
to claim it exists yet). Deliberately doesn't name FreeModel/Free.ai specifically in
the public-facing text, since which backend is active is config-driven and can change
without a deploy; it does disclose that third-party AI providers are used, that only
the tool's own fields are sent (never account identifiers), and that a template
fallback always exists.

## 10. Both slots live-verified with a real key each; round-robin confirmed in production (2026-09-22, same day)

**The `/guide` page turned out to be a technical quick-start generator, not a terms
page** — the founder relayed its full content: copy-paste config snippets for Claude
Code (MCP), Codex CLI, Cursor, Continue.dev, Cline, cURL, Python, and Node.js. No
retention, commercial-use, or rate-limit language anywhere on it. **The commercial-use
and data-retention question is therefore still genuinely open** — there is simply no
policy page in this product to read yet, not an unread one. This is a real, standing
gap for Phase 27, not resolved by anything found today.

**The guide's own recommended defaults were checked and don't currently work.** Every
snippet on that page uses `https://freemodel.online/v1` (not `alli.website`) and
model `auto/best-chat` (not `fm-v1-lite`). Tested live with the real key:
`freemodel.online` + `auto/best-chat` → HTTP 502 (upstream routing failure, not a
request error); `alli.website` + `auto/best-chat` → HTTP 400; `freemodel.online` +
`fm-v1-lite` → HTTP 400 with `"Unable to determine provider for model 'fm-v1-lite'"`
— confirming `freemodel.online` and `alli.website` are two genuinely different
backends with two different model catalogs, both reachable with the same key, neither
one a subset of the other. The one combination that has ever actually worked across
every test this session is `alli.website` + `fm-v1-lite`, and it's what stays
configured. Worth re-checking `auto/best-chat` again later — a 502 suggests a
transient upstream issue on FreeModel's side, not a configuration mistake here.

**Free.ai, live-verified with the founder's real key:** `qwen7b` returned valid JSON
on a real title-generation prompt **5 of 5 times** (`max_tokens=3000`, ~5-6s per
call) — matching FreeModel's reliability. Its usage block matched the documentation
exactly (`free_ai_usage: {tokens_used, tokens_charged, source, model}`), confirming
the earlier note that token counts read back as 0 through this code (it reads
`usage`, not `free_ai_usage`) is a real, now-confirmed-live limitation, not a guess.

**Round-robin confirmed live in production, both slots configured:** six consecutive
calls to `/api/v1/admin/ai/ping` alternated **perfectly** —
`inclusionai/ling-3.0-flash-sante:free` (FreeModel) → `Qwen/Qwen3-30B-A3B-Instruct`
(Free.ai) → repeat, three full cycles, 404ms-2006ms per call. This is the actual
distribution behavior in production, not the unit tests' simulated delegates.
`/api/v1/game-deals` re-checked unaffected after both deploys.

**Still open:** FreeModel's (and Free.ai's) actual commercial-use/retention terms —
no policy page has been found for either yet; this needs resolving, by the founder
finding and reading whatever the real terms document is (or contacting support
directly), before Phase 27 sends real, non-test customer input through this path.
