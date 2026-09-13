# ADR-003 — Agent Authoring, Orchestration & Deployment Model

**Status:** Proposed · **Date:** 2026-08-13 · **Decider:** Lakshya Tyagi (sole engineer)
**Supersedes:** ADR-000 §1 (product-definition sentence only — broadens the "agentic chatbot backend"
framing; the HARD NO-COUPLING RULE in the same section is untouched and remains load-bearing, §0.1),
§2.6 (transports — names two later-wave, vendor-TBD port categories, §6.2/§6.3), §2.8 (tools — adds a
third, registry-only `adapter_type`; does **not** add a third `ToolPort` adapter, §7.2), §4 (elaborates,
does not relax, the tenant-scoping/RLS rule for one case — platform-visibility library rows, §5.1), §7
(Wave 1/2/3 delivery plan — adds items to each wave, reorders nothing), §8 (repo layout — adds
`deployments`, `scenarios`, `session_scoring` modules), §9 (rejected-alternatives table — adds an entry
for vendor-selection deferral). Does not touch ADR-001 or ADR-002.
**Review trigger:** first tenant asks for voice-telephony or meeting-bot-join deployment (triggers the
dedicated vendor ADR this document defers, §6.2/§6.3); or a second engineer joins and workflow-kind
agents move off the single-engineer sequencing assumption in §10.

---

## 0. Why this ADR exists

Shubham (product owner) walked through a competitor product (ToughTongue AI) and a second, unnamed
competitor product he found actively confusing, and came away with three concrete points of confusion
about our own data model that this ADR resolves directly:

1. **"Deploy" vs "Publish" vs "Schedule" are three different things wearing one word.** Both reference
   products conflate them in places — a "share link" is a deploy action; a "scenario library" listing is
   closer to publish; a recurring meeting-bot join sounds like scheduling but is really a deploy-time
   property. Our API cannot afford that ambiguity, because these are three different resources with three
   different lifecycles, owners, and failure modes. §2 gives each one a name and a table.
2. **"Conversational" agents and "workflow" agents are not the same shape**, and today's `agents` table
   has no field that says which one a given agent is. A conversational agent (chat/voice, turn-by-turn, a
   human or another system on the other end) and a workflow agent (a scheduled or triggered job that runs
   to completion with no human mid-loop) need different deployment surfaces, different session-scoring
   semantics, and eventually different runtime graphs. §1 adds the field that answers "which kind is this."
3. **"What decides deployability" had no answer** — nothing today gates whether an agent can be deployed,
   or distinguishes "this agent is configured" from "this agent is live somewhere a customer can reach
   it." §3 makes deployability a computed join condition, not a single overloaded flag.

Everything below is config-plane only, per ADR-000 §1's plane split — this ADR adds no new runtime
behavior, only the schema, endpoints, and sequencing that a future runtime (and a future UI, built
elsewhere, never in this repo) will consume.

### 0.1 Positioning — general-purpose, not a coaching vertical

Shubham was explicit that this platform's purpose is not narrowly "sales-coaching/conversation-practice"
(ToughTongue's own vertical): *"a general agentic system can do anything — accounting, auditing, ads,
everything else — with the appropriate tools and MCP. Whichever tools/MCPs are available, it uses them
automatically."*

This is **already true architecturally and needs no new mechanism.** ADR-000 §2.8's MCP-first tool model
(any MCP server or OpenAPI-described HTTP API becomes a bound tool) already makes an agent's capability a
function of *which tools a workspace has registered*, not a hardcoded vertical. A support agent, an
accounting-audit agent, and a sales-coach agent are the same `agents` row shape with different tool/
knowledge-base bindings — nothing in ADR-000 or this document special-cases "sales" anywhere.

What this ADR changes is **framing, not mechanism**: ADR-000 §1's product-definition sentence — *"a
standalone, API-only agentic chatbot backend"* — undersells what §2.8's tool model already supports.
Recommended replacement wording (recorded in §9's table below as a superseded-sentence, not a mechanism
change): *"a standalone, API-only agentic AI-workforce backend — conversational, voice, and
autonomous-workflow agents composed from tools/MCPs to do any task a tenant configures, not limited to
chat."* The **HARD NO-COUPLING RULE** immediately below that sentence in ADR-000 §1 is untouched — this is
a wording change to one descriptive sentence, nothing structural, and nothing about the independence rule.

---

## 1. Agent "kind" — conversational vs workflow

### 1.1 Decision

Add one new column to `agents` (additive migration, non-breaking against existing rows):

| Column | Type | Default | Notes |
|---|---|---|---|
| `kind` | `str` (`AgentKind = Literal["conversational", "workflow"]`) | `"conversational"` | Immutable after creation, same enforcement pattern as `knowledge_bases.embedding_model_id` (ADR-000 §2.4 — 422 at the API layer + a DB trigger) — the runtime graph shape, the deployment surfaces available (§2), and session-scoring config (§4) all branch on this value, so changing it under a live agent is not a "field edit," it's a different agent. |

- **`conversational`** — turn-by-turn, a human (or another system acting as one) on the other end, driven
  by the SSE/WebSocket/webhook transports ADR-000 §2.6 already names. This is every agent the repo
  supports today; the default preserves that.
- **`workflow`** — triggered or scheduled, runs to completion with no mid-loop human turn, driven by
  `TaskQueuePort`/Temporal directly rather than a chat transport. No runtime code for this exists yet, and
  none is being built by this ADR (§10 sequences it into Wave 3).

`AgentCreate` gains `kind: AgentKind = "conversational"` (omittable, matching how `AgentCreate.description`
already defaults rather than nullable-defaults). `AgentUpdate` does **not** gain a `kind` field at all — per
the immutability rule above, `kind` is Create-only, excluded from `AgentUpdate` entirely (the same shape
`knowledge_bases.embedding_model_id` uses — excluded from its own `Update` schema, not merely rejected at
runtime), since `kind` has no legitimate reason to ever appear in a PATCH body. `AgentRead` gains
`kind: AgentKind`.

### 1.2 Why not a separate table per kind

Rejected — see §11. `agents` stays one table because everything else about an agent (`tenant_id`,
`workspace_id`, ownership, config binding, tool binding, knowledge-base binding) is identical regardless of
kind; only the runtime graph and the deployment surface differ, and both are already modeled as separate
resources (the not-yet-built runtime graph selection, and the new `deployments` resource below), not as
agent-table columns.

---

## 2. Deploy vs Publish vs Schedule — three resources, three tables

This is the section that resolves point 1 in §0. Three words, three separate concepts:

| Word | Resource | What it means | New table? |
|---|---|---|---|
| **Deploy** | `deployments` (new module, `agentic.deployments`) | "This agent is reachable at a specific channel/transport, right now, by an external caller." A deployment is a live binding of one agent to one channel with its own credentials/config for that channel. | `deployments` |
| **Publish** | `scenarios` (new module, `agentic.scenarios`) — see §5 | "This agent (or a template of one) is discoverable in a library, browsable, remixable." Publishing has nothing to do with whether the agent is reachable anywhere — a scenario can be published with zero live deployments, and an agent can be deployed with zero scenario listing. | `scenarios` (a separate resource, not a flag on `agents` or `deployments`) |
| **Schedule** | not a new top-level resource — it is exclusively a `workflow`-kind agent's own Temporal Schedule/trigger (existing `TaskQueuePort`, ADR-000 §2.5). A `conversational`-kind agent is never "scheduled" in this sense. | "This workflow-kind agent runs on its own recurring/triggered cadence, with no end-user-facing channel at all." | no new table — reuses Temporal's own `Schedule` primitive |

The critical modeling decision: **`deployments` and `scenarios` are independent resources with no foreign
key between them, and "Schedule" is reserved for exactly one meaning.** Conflating these was the root of
Shubham's confusion — both reference products use "deploy," "share," "publish," and "schedule"
interchangeably in different places, and copying that ambiguity into a schema would reproduce the same
confusion in our own API consumers.

A separate, related-but-distinct idea — a *deployment* that isn't "always on" but recurs on a cadence (a
meeting-bot join every Tuesday, §6.3) — is deliberately **not** called "schedule" either. See §2.1's
`recurrence` column and the review note at the end of §2.2.

### 2.1 `deployments` table

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid.UUID` | |
| `tenant_id` | `uuid.UUID` | FK -> `tenants.id` ondelete=CASCADE, indexed, not null — same `TenantScopedMixin` convention as `agents` |
| `workspace_id` | `uuid.UUID` | Composite FK -> `(workspaces.id, workspaces.tenant_id)`, same pattern as `agents.workspace_id` |
| `agent_id` | `uuid.UUID` | Composite FK -> `(agents.id, agents.tenant_id)` — mirrors the `workspace_id`/`tenant_id` composite-FK pattern `agents` itself uses against `workspaces`, so a deployment can never reference an agent belonging to a different tenant even under a race |
| `channel` | `str` (`DeploymentChannel` enum — see §2.2) | Not null |
| `channel_config` | `dict[str, Any]` (JSONB) | Channel-specific config (e.g. `{"widget_theme": "..."}` for `web_widget`, branding for `share_link`). Validated per-channel at the service layer, not by a DB constraint — same "typed at the API boundary, JSONB at rest" convention `config.config` already uses (ADR-000 §2.10) |
| `recurrence` | `dict[str, Any] \| None` (JSONB) | Nullable. Cron-like cadence for channels where "deploy" implies a recurring action rather than a standing listener (e.g. `meeting_bot` joining a recurring meeting, §6.3). `None` for every channel that's just "always on" (`web_widget`, `api`, `share_link`). **Deliberately not named `schedule`** — see the review note at the end of §2.2 |
| `status` | `str` (`DeploymentStatus = Literal["active", "paused", "archived"]`) | Not null, default `"active"`. `paused` stops the channel from accepting new sessions without deleting the row (a deploy toggle, not a soft-delete state — §2.3) |
| `created_at`, `updated_at` | | Standard |

### 2.2 `DeploymentChannel` — Wave-sequenced, not all built at once

```python
DeploymentChannel = Literal[
    "web_widget",       # Wave 2 — embeddable widget config surface (config only; no widget code ships from this repo, ADR-000 §1)
    "api",               # Wave 1 — this repo's own REST/SSE/WebSocket surface, i.e. "deployed" means "credentials issued, reachable via /v1/agents/{id}/runs" — the degenerate case that already works today in all but name
    "share_link",        # Wave 2 — a signed, revocable public URL wrapping the api channel for a single agent, no auth header required from the far end
    "voice_telephony",   # Wave 3 — vendor TBD, see §6.2. Config row only; no runtime adapter until its own dedicated ADR lands
    "meeting_bot",        # Wave 3 — vendor TBD, see §6.3. Config row only; same caveat
]
```

`api` is called out specifically because **it is not new work** — every agent already reachable via
`POST /v1/agents/{id}/runs` today is retroactively "deployed to the `api` channel" the moment this table
exists; Wave 1 needs only a `deployments` row with `channel="api"` and no adapter changes, since the
transport itself (ADR-000 §2.6) is already live. This is what makes `deployments` a Wave-1-viable resource
even though `voice_telephony`/`meeting_bot` are not — see §10.

> **Review note.** An earlier draft of this document named the `recurrence` column `schedule` and, in the
> same breath, described it as "closer to schedule" in prose while modeling it as a property of a
> *deployment*. An adversarial pass on that draft correctly flagged this as reintroducing, under a new
> name, the exact one-word/two-concepts collision this whole document exists to eliminate: "schedule" would
> have meant two unrelated things (a workflow-kind agent's Temporal trigger, §2 table row 3; and a
> deployment's recurring-join cadence). Renamed to `recurrence` here specifically so "Schedule," as a word,
> keeps exactly one meaning anywhere in this API.

### 2.3 Why `deployments.status` is a third state machine, not reuse of `agents.status`

`agents.status` (`active`/`archived`/`deleted`) answers "does this agent config exist and is it usable at
all." `deployments.status` (`active`/`paused`/`archived`) answers "is this specific channel binding
currently live" — an agent can be `active` with zero, one, or many deployments in any mix of states. Soft
delete of a deployment reuses the same `archived` terminal state the rest of the repo uses for "gone but
kept for audit" (no `deleted` state on `deployments` — a deliberate, narrow deviation from the
`active/archived/deleted` convention `agents`/`workspaces`/`tenants` share, flagged explicitly here so a
future reviewer doesn't "fix" it into a fourth state to match). `DELETE .../deployments/{id}` sets
`status -> "archived"`, matching this table's own state set — see §2.4.

### 2.4 Endpoints

`deployments` is a **binding set** in the `build_agent_binding_router` sense (per-agent, potentially many
rows) but does **not** fit that factory's whole-resource-atomic-PUT shape — a deployment has its own
identity (`id`), its own independent lifecycle (pause one channel without touching another), and needs
individual GET/PATCH/DELETE, unlike `agent_tools`/`agent_knowledge_bases` which replace the entire set in
one PUT. So `deployments` is its own full CRUD module, nested the same way `knowledge`'s
`documents_router`/`sources_router` are nested under `knowledge-bases` rather than forced through the
binding-router factory:

| Method | Path | Notes |
|---|---|---|
| POST | `/v1/workspaces/{workspace_id}/agents/{agent_id}/deployments` | 201, `Idempotency-Key`-aware, rate-limited (`deployments` dimension). 409 if `agents.status != "active"` — see §3 |
| GET | `/v1/workspaces/{workspace_id}/agents/{agent_id}/deployments/{id}` | 200; 404 via `NotFoundError` |
| GET | `/v1/workspaces/{workspace_id}/agents/{agent_id}/deployments` | Cursor-paginated, same shape as every other list route |
| PATCH | `/v1/workspaces/{workspace_id}/agents/{agent_id}/deployments/{id}` | Partial update (`channel_config`, `recurrence`, `status`); `channel` itself is immutable (same 422-on-attempted-change pattern as `tools.adapter_type`) — a channel swap is a new deployment, not an edit |
| DELETE | `/v1/workspaces/{workspace_id}/agents/{agent_id}/deployments/{id}` | 204, soft "archive" per §2.3; `Idempotency-Key`-aware |

Deliberately **not** built via `build_agent_binding_router` — that factory's own docstring
(`core/routing.py`) scopes it to the "replace the whole resource atomically" PUT/GET shape shared by
`tools`/`knowledge`/`memory`/`guardrails`/`config`; forcing a per-row-identity resource through it would be
factory misuse in the opposite direction of what `jscpd`'s clone-detection gate normally catches.

### 2.5 The default-surface rule — the literal answer to "how does it decide Deploy or not"

One sentence, and the only rule a future UI needs to implement:

> **A `conversational`-kind agent defaults to showing "Deploy" (to a `deployments` channel); a
> `workflow`-kind agent defaults to showing "Schedule" (its own Temporal trigger). Both actions stay
> available on either kind — the default is a UI convenience inferred from `agents.kind`, never a hard
> lockout.**

`AgentRead.kind` is the only field a future UI needs to read to implement this — no `is_deployable`/
`show_deploy_button` computed flag is added (that would duplicate `kind` and drift out of sync with it,
against ADR-000 §2.10's "config is DB rows, never a redundant derived constant" philosophy).
`kind == "conversational"` **is** "show Deploy by default," full stop. This is the literal mechanism
Shubham proposed near the end of the recorded conversation — by default the system leans toward Schedule
for a workflow-shaped agent (deploy stays present, just not the default emphasis) and toward Deploy for a
conversational one — reframed here as a plain data contract, since this repo renders no UI to enforce it
directly.

---

## 3. What decides deployability

Deployability is a **join condition evaluated at `POST .../deployments` time**, not a single stored flag:

1. `agents.status == "active"` (not `archived`/`deleted`) — enforced today's `AgentService`-level check,
   extended to this new endpoint. 409 `ConflictError` otherwise, the same status-gate pattern
   `documents_router`'s `POST .../documents` already uses against a non-`active` knowledge base.
2. For `kind == "workflow"` agents specifically: at least one `TaskQueuePort`-registered workflow type must
   exist for that agent (checked at deploy time, not agent-create time) — a workflow-kind agent with no
   workflow wired is "configured" but not "deployable." Wave 3 concern (§10) — the workflow-kind runtime
   doesn't exist before then — documented here so the eventual implementation has a named rule to satisfy,
   not one invented ad hoc.
3. Channel-specific preconditions belong to the channel, not to this shared gate — e.g. `voice_telephony`
   deployability additionally depends on a telephony number being provisioned (out of scope until its own
   ADR, §6.2), so the shared check above is a floor, not the whole story per channel.

No new column is added to `agents` for "is deployable" — the answer is always computed, never cached,
because a stale cached flag (e.g. an agent that becomes non-deployable because its bound knowledge base was
archived) is exactly the kind of drift ADR-000's "config is DB rows, never a constant" philosophy and this
repo's general allergy to redundant derived state both argue against.

---

## 4. Session-scoring / post-session-analysis config

ToughTongue's post-session analysis (verified live on the product: structured scored reports with
criteria-level breakdowns and transcript citations, plus a Premium-tier multimodal voice+video review
mode) maps onto a new **singleton settings object**, same shape as `agent_memory_config`/
`agent_guardrail_policy` — one row per agent, whole-object PUT/GET via `build_agent_binding_router`:

New module `agentic.session_scoring`, table `agent_session_scoring_config`:

| Column | Type | Notes |
|---|---|---|
| `agent_id`, `tenant_id`, `workspace_id` | | Same composite-FK convention as every other agent-scoped table |
| `enabled` | `bool` | Default `False` — scoring is opt-in per agent, same secure/inert-by-default posture `guardrail_policy` uses for its own toggles |
| `rubric` | `dict[str, Any]` (JSONB) | Tenant-authored criteria (e.g. `{"criteria": [{"name": "...", "weight": ...}]}`) — deliberately unstructured JSONB at this layer, not a normalized `criteria` table, since the shape is still evolving and ADR-000 §2.10's config-from-DB rule is satisfied by "a DB row holds it," not "a fully normalized schema holds it, day one" |
| `mode` | `str` (`Literal["transcript_only", "multimodal"]`) | Default `"transcript_only"`. `multimodal` (voice+video, ToughTongue's confirmed Premium-tier feature) requires a video/audio-capable channel — validated at the service layer against the agent's own deployments, not at the schema layer, since the two tables have no FK between them |
| `citation_mode` | `str` (`Literal["none", "transcript_span"]`) | Default `"transcript_span"` — whether a scored report cites the specific transcript span that earned/lost a rubric point, mirroring the existing RAG citation-toggle precedent ADR-000 §2.4 already sets |

This is **config only** — no scoring runs, no report is generated, by this ADR. The runtime piece (running
a rubric against a completed session transcript, or a multimodal analysis pass) is `runtime/` work,
sequenced into Wave 3 (§10), gated on `runs/` producing something to score in the first place.

`StructuredOutputPort` (already declared, ADR-000 §2.1.1) is the sanctioned path for the eventual scoring
node's LLM call — a scored report is exactly the "bind a Pydantic schema, validate, retry" output that port
exists for; no new port is needed for transcript-only mode. Multimodal analysis is different: it needs a
model call carrying audio/video content, a `LLMGatewayPort.complete` question (provider/model multimodal
capability) *if* the chosen provider supports it, or a dedicated new port if none do. **This ADR does not
resolve which** — flagged as an open Wave-3 question (§10), a provider-capability research question outside
this document's remit to guess at.

---

## 5. `scenarios` — the template/publish library

Per the hard constraint on module naming (`templates/` is taken by the repo-root Copier scaffold), this
module is named **`agentic.scenarios`**, not `agentic.templates`.

### 5.1 What it is

A **read-heavy library resource** of reusable agent starting points — the "Publish" concept from §2, and
the direct analogue of ToughTongue's own confirmed public/featured scenario library (weekly refresh,
homepage gallery, remix/scaffold):

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid.UUID` | |
| `tenant_id` | `uuid.UUID` | **Not null, always a real `tenants` row — no exception.** Platform-curated scenarios reference one reserved platform tenant (see below), never a NULL. Preserves ADR-000 §4's unconditional "every table carries `tenant_id`... and an RLS policy" rule without an exception |
| `workspace_id` | `uuid.UUID \| None` | Nullable — a platform-curated scenario need not belong to any specific workspace even of the reserved platform tenant. (§4 does not mandate `workspace_id`, only `tenant_id`, so this is not a deviation from that rule.) |
| `visibility` | `str` (`Literal["private", "tenant", "platform"]`) | `private` = one workspace only; `tenant` = shared across a tenant's workspaces; `platform` = the platform-curated public library (ToughTongue's confirmed "featured agents, updated weekly") — `platform`-visibility rows are written by an internal/admin process, not exposed on any tenant-facing write endpoint in Wave 1/2 (§10) |
| `name`, `description` | `str` | Same bounds convention as `AgentCreate` |
| `kind` | `AgentKind` | Mirrors §1 — a scenario is kind-typed the same way an agent is, since remixing it produces an agent of that kind |
| `config_snapshot` | `dict[str, Any]` (JSONB) | A serialized snapshot of an agent's config surface (prompt key, model, tool bindings, knowledge-base bindings, guardrail policy, session-scoring config) — a snapshot, not a live reference, so editing the source agent after publishing a scenario never mutates already-published copies |
| `source_agent_id` | `uuid.UUID \| None` | Nullable — which agent this scenario was captured from, if any |
| `status` | `str` (`Literal["active", "archived"]`) | Two states, not three — no soft-delete-with-audit-trail need beyond `archived`, matching `deployments`' own reasoning (§2.3) |
| `created_at`, `updated_at` | | Standard |

**Reserved platform tenant.** Add `tenants.is_platform: bool` (default `false`), enforced to at most one
`true` row by a partial unique index. Every `scenarios` row keeps a real, non-null `tenant_id`; a
`platform`-visibility row's `tenant_id` is that one reserved tenant's id, resolved by the service layer via
a small cached lookup (`WHERE is_platform`), never hardcoded. The RLS policy on `scenarios` is the one
place this ADR **elaborates**, not relaxes, ADR-000 §4's rule: `USING (tenant_id = current_setting(...) OR
visibility = 'platform')` — an explicit, documented, tested OR-clause, not a NULL-tenant special case that
every other RLS policy in the codebase would then have to account for.

### 5.2 Endpoints

Standard registry CRUD, workspace-scoped for `private`/`tenant` visibility (same shape as `tools`/
`knowledge-bases` registries), plus authoring actions (§5.3):

| Method | Path | Notes |
|---|---|---|
| POST | `/v1/workspaces/{workspace_id}/scenarios` | Create directly, or... |
| POST | `/v1/workspaces/{workspace_id}/agents/{agent_id}:publish-as-scenario` | ...capture an existing agent's current config as a new scenario row (`config_snapshot` populated from that agent's live config at call time). 201, `Idempotency-Key`-aware |
| GET | `/v1/workspaces/{workspace_id}/scenarios/{id}` | 200; visibility-checked (platform + own-tenant rows visible, other tenants' `tenant`/`private` rows never) |
| GET | `/v1/workspaces/{workspace_id}/scenarios` | Cursor-paginated; includes `platform`-visibility rows by default (the "gallery" — filterable by `visibility` query param) |
| PATCH | `/v1/workspaces/{workspace_id}/scenarios/{id}` | Partial update; `platform`-visibility rows reject a tenant-originated PATCH (403) |
| DELETE | `/v1/workspaces/{workspace_id}/scenarios/{id}` | Soft archive |
| POST | `/v1/workspaces/{workspace_id}/scenarios/{id}:remix` | Authoring entry point 1 — start from a template. §5.3 |
| POST | `/v1/workspaces/{workspace_id}/agents:draft-from-prompt` | Authoring entry point 2 — start from a blank prompt, no scenario required. §5.3 |

### 5.3 The agent-builder authoring surface — two entry points, one mechanism

ToughTongue's confirmed "remix/scaffold" and "plain-language prompt-based configuration" features, plus
Shubham's own explicit ask ("paste a prompt, it auto-configures the AI instructions, session structure,
scenario rules... **or** start from the scenario library and remix an existing setup — both are valid entry
points"), map onto one shared authoring mechanism with two doors in:

1. **`POST .../scenarios/{id}:remix`** — start from an existing scenario (template or a previously
   published agent). Request body:

   ```jsonc
   {
     "name": "string, required — the new agent's name",
     "description": "string, optional override",
     "prompt_hint": "string, optional — free-text description of desired changes, e.g. \"make it more concise and focused on billing questions\""
   }
   ```

   Response: a newly created `agents` row (`AgentRead`), `kind` inherited from the scenario, config
   bindings copied from `config_snapshot`.

2. **`POST .../agents:draft-from-prompt`** — start from nothing but a freeform description, ToughTongue's
   "build from scratch by pasting a prompt" path. Request body:

   ```jsonc
   {
     "name": "string, required",
     "prompt": "string, required — freeform description of the agent to build, e.g. \"an SDR that qualifies inbound leads and rates lead temperature\""
   }
   ```

   Response: a newly created `agents` row with no `source_agent_id`/scenario parent at all — `config_snapshot`
   doesn't apply because there is no snapshot to start from.

Both routes funnel through the same **`StructuredOutputPort`-backed rewrite step**: `prompt`/`prompt_hint`
is never written directly into a `prompts` row as raw text (ADR-000 §2.1.1's "no prompt string in Python,
structured output only" rule applies here exactly as anywhere else — an agent-builder endpoint is not an
exception to "never parse free text," it is a `StructuredOutputPort` consumer like every other node). The
LLM call proposes a structured config diff (instructions, suggested `kind`, suggested tool bindings drawn
from the workspace's *already-registered* `tools`/`knowledge-bases` — this is the concrete mechanism behind
"whichever tools/MCPs are available, it uses them automatically," §0.1 — suggested guardrail level), which
the caller reviews/edits before it is committed. This is also the natural home for ToughTongue's confirmed
"quick-prompt" one-click tuning shortcuts ("make it more guarded," "add voice-pipeline safety rules") —
those are canned strings a future UI passes as `prompt_hint`/`prompt`; no separate mechanism is needed.

Sub-case sequencing (§10): a plain copy (`remix` with no `prompt_hint`, or a `draft-from-prompt` call that
only needs template substitution) is synchronous, config-plane only, no LLM call — Wave 1/2 viable the
moment `scenarios`/`agents:draft-from-prompt` exist. The LLM-assisted rewrite sub-case (`prompt_hint`/
`prompt` actually driving a structured rewrite) is **runtime-plane-adjacent** — same category of boundary
`knowledge`'s `:estimate-context-cost` already crosses (a config-plane endpoint that calls a port) — and is
sequenced one wave later, gated on `StructuredOutputPort` having a real adapter rather than a `Null*` stub.

### 5.4 Explicitly not built by this ADR

Weekly-refresh curation of `platform`-visibility scenarios, the actual gallery UI (no UI, ever, per the hard
constraint), and any ranking/"featured" algorithm — `visibility="platform"` plus ordinary
`created_at`/`updated_at` is the entire mechanism this ADR specifies, sufficient for a future consumer to
build a "what's new this week" view over via a query, not a feature this repo computes.

**Marketplace/monetization is explicitly deferred, not designed.** Shubham raised a template-marketplace
idea (pay a small one-time fee, e.g. $5, to reuse someone else's published scenario) but was explicit he
hasn't ideated on it yet. This ADR reserves the concept's *name* (`scenarios`, distinct from the free
internal `:remix` authoring flow) but adds no `price_usd`/purchase table — that is a follow-up design once
the product idea is actually scoped, the same "don't build for a hypothetical" posture this repo applies
everywhere else, not a gap in this document.

---

## 6. Cross-participant memory scoping, and the two later-wave transport ports

### 6.1 Cross-participant memory scoping

ToughTongue's confirmed "agents remember progress across sessions" requires memory to be keyed by
**participant**, not just by `thread_id`/session, when the same external user returns across multiple
sessions with the same agent. Today's `agent_memory_config` (singleton per agent) has no participant
dimension at all — it configures *how* memory behaves, not *whose* memory is being kept.

Decision: extend the memory module's **future storage tables** (not yet built — `memory` is config-only
today per the inventory) with a `participant_id` column, sourced from a new required field on session
creation: `external_participant_ref` (a caller-supplied opaque string, e.g. the calling system's own user
id — this service never authenticates end-users directly, only tenants via API key/JWT, so "who is the
human/system on the other end" is necessarily caller-asserted, the same trust boundary `runs`' caller-
supplied `thread_id` already sits at). Memory retrieval scopes by `(tenant_id, agent_id, participant_id)`,
falling back to `(tenant_id, agent_id, thread_id)`-only behavior (today's implicit scope) when no
`external_participant_ref` is supplied — additive, not breaking.

This is a **schema decision recorded now, implementation deferred** — the memory module's actual storage
tables are Wave 2/3 work regardless of this ADR (§10); this section exists so that when they are built, the
participant dimension is designed in from the first migration rather than retrofitted.

### 6.2 `VoiceTelephonyPort` — later-wave, vendor-TBD, own future ADR

ToughTongue's outbound-calling and per-minute BYOK pricing implies a voice telephony capability this repo
has zero infrastructure for today. Per the hard constraint against picking new vendor/infra decisions
casually in this document:

- **A `VoiceTelephonyPort` interface is named here, not designed here.** Shape TBD — plausibly something
  like `async initiate_call(*, tenant_id, agent_id, to_number, from_number, deployment_id) -> CallHandle`
  plus a webhook-delivered call-event stream — but the exact method signatures, the choice of provider
  (Twilio, Vonage, Plivo, or a voice-AI-specific layer like Vapi/Retell — both named as ToughTongue's own
  comparison-page competitors, itself a reason to research rather than default to either), and the
  SIP/PSTN/WebRTC transport question all require the same rigor ADR-000/ADR-001 gave their own vendor
  choices — a comparison table, licence/pricing/region check, a load-bearing decision record — not a
  paragraph in this document.
- **Sequencing:** Wave 3 (§10), contingent on its own dedicated future ADR (tentatively "ADR-004 — Voice
  Telephony Provider," not written here, not numbered as a commitment) landing first.
- `deployments.channel` already reserves the `"voice_telephony"` literal (§2.2) so the schema doesn't need
  to change shape when that ADR lands — only a real adapter and finalized method signatures get added, the
  same "port exists early, adapter lands later" pattern ADR-000 §7 already establishes for every other port.

### 6.3 `MeetingBotPort` — later-wave, vendor-TBD, own future ADR

Same reasoning as §6.2, for ToughTongue's confirmed Google Meet/Zoom join capability (its "Note Taker" bot
joins a live meeting automatically):

- **A `MeetingBotPort` interface is named here, not designed here.** Plausibly
  `async join_meeting(*, tenant_id, agent_id, meeting_url, deployment_id) -> BotSessionHandle` plus a
  recording/transcript-delivered webhook — but which meeting-bot SDK/vendor (Recall.ai, a build-it-ourselves
  headless-browser approach — ToughTongue's own confirmed "driving a browser on Meet/Zoom" detail suggests
  this is a materially harder integration than a simple bot-join API, worth research before commitment) is
  explicitly deferred.
- **Sequencing:** Wave 3, contingent on its own dedicated future ADR ("ADR-005," not written here).
- `deployments.channel` reserves `"meeting_bot"` (§2.2) and `deployments.recurrence` (§2.1) is exactly the
  field a recurring "join this meeting every week" deployment would populate, so no schema rework is
  anticipated when that ADR lands.

Both ports are listed in ADR-000 §2.6's transports row as a forward pointer — see §9 of this document for
the exact amendment.

---

## 7. New client-rendered/interactive tool type

ToughTongue's confirmed mid-conversation tools (whiteboard/diagram sketching, Google Slides generation,
live browser-drive, webcam/facial-expression analysis) don't fit `ToolPort`'s existing two adapters
(`MCPToolAdapter`, `HttpToolAdapter`, ADR-000 §2.8) cleanly — both model "the agent calls out to something
and gets a result back." A whiteboard the *end user* interacts with mid-session is the reverse shape: the
agent emits a render instruction, a client (built elsewhere, never this repo) renders it, and optionally
streams interaction back.

### 7.1 Decision

Add a third `adapter_type` value to the existing `tools` registry: `"client_rendered"`, alongside today's
`"mcp"`/`"http"` (`ck_tools_mcp_transport` constraint, migration `20260813_0001`, extended to allow this
third value with `mcp_transport` still NULL for it, same as `"http"` today). **This amends ADR-000 §2.8's
"MCP-first. One `Tool` port, two adapters" statement** — recorded explicitly in this document's own
"Supersedes" header and in §9's table, not left as a silent change. A `client_rendered` tool has no live
invocation step at all through `ToolPort.invoke`:

| Column addition to `tools` | Type | Notes |
|---|---|---|
| `render_schema` | `dict[str, Any] \| None` (JSONB) | Required when `adapter_type == "client_rendered"`, NULL otherwise (same NULL-safe CHECK-constraint pattern `mcp_transport` already uses). A JSON Schema describing the payload shape a compliant client must know how to render (e.g. `{"type": "whiteboard", "props": {...}}`) — this repo defines and validates the schema; it renders nothing, per the hard "no frontend, ever" constraint. |

`ToolPort.fetch_schema`/`invoke` are **not called** for a `client_rendered` tool — `:refresh-schema`
degrades to a schema-shape validation only (does the stored `render_schema` parse as valid JSON Schema),
and `:test-invoke` is rejected (422) for this adapter type, since there is nothing to invoke server-side.
The actual "invocation" happens entirely inside a LangGraph node emitting a typed `custom` stream event
(ADR-000 §2.6's streaming controls already name `custom` as a mode) carrying the `render_schema`-conformant
payload — runtime work, not built by this ADR beyond noting the seam exists (Wave 3, §10).

### 7.2 Why not a new port

A fourth `ToolPort` adapter (`ClientRenderedToolAdapter`) was considered and rejected: `ToolPort`'s two
methods (`fetch_schema`, `invoke`) both model a request/response round trip to something *this backend*
calls. A client-rendered tool is never called by this backend at all — it's a payload-shape contract
between the runtime and an external renderer. Modeling it as a `ToolPort` adapter would mean writing
`invoke` implementations that do nothing; treating it as a tool *registry* entry (same CRUD, same tenant
scoping, same binding-to-agent shape) with a different `adapter_type` the runtime graph branches on is the
better fit, and it means `ToolPort` itself genuinely still has exactly two *invocable* adapters — the
`client_rendered` registry entry never reaches the port at all.

---

## 8. Repository layout additions (elaborates ADR-000 §8, annotation only)

```
src/agentic/
  ...                     # unchanged, see ADR-000 §8
  deployments/            # NEW — router · schemas · service · repository · models (§2)
  scenarios/               # NEW — router · schemas · service · repository · models (§5). NOT "templates" — see hard constraint
  session_scoring/         # NEW — settings-object module via build_agent_binding_router (§4)
  runtime/
    ...                   # unchanged; eventual workflow-kind graph and client_rendered custom-event
                           # emission both land here, not as new top-level modules
```

No existing module is renamed or removed. `tools/` gains a new `adapter_type` value and one nullable
column (§7); no new module for it. `memory/` gains a `participant_id` dimension in its still-unbuilt
storage tables (§6.1); no new module for it either. `tenants/` gains one boolean column (`is_platform`,
§5.1); no new module.

---

## 9. What changes in ADR-000

| ADR-000 location | Original | Now |
|---|---|---|
| §1, product-definition sentence | "a standalone, API-only agentic **chatbot** backend" | Recommended reword, see §0.1: "a standalone, API-only agentic **AI-workforce** backend — conversational, voice, and autonomous-workflow agents..." The HARD NO-COUPLING RULE in the same section is unchanged and remains load-bearing |
| §2.6, Transports section | Lists SSE/WebSocket/async+webhook only, no telephony/meeting mention | Add a forward pointer: two later-wave transport-adjacent ports, `VoiceTelephonyPort` and `MeetingBotPort`, are named (not designed) in ADR-003 §6.2/§6.3 — each requires its own dedicated future ADR before any adapter is built. Original transport table is unchanged and remains authoritative for what's actually live |
| §2.8, tools | "MCP-first. One `Tool` port, two adapters" (`MCPToolAdapter`, `HttpToolAdapter`) | Adds a third, **registry-only** `adapter_type`, `client_rendered` — not a third `ToolPort` adapter; `ToolPort` itself still has exactly two invocable adapters. See ADR-003 §7 |
| §4, database rules | "Every table carries `tenant_id`... and an RLS policy" | Elaborated, not relaxed, for one case: `scenarios` (ADR-003 §5.1) keeps `tenant_id NOT NULL` on every row, including platform-owned ones, by reserving one designated platform tenant (`tenants.is_platform`) rather than permitting NULL |
| §7, Wave 1 system list / delivery plan | 6 systems, no mention of `deployments`/`scenarios`/`session_scoring`/agent `kind`/`client_rendered` tools | See ADR-003 §10 for the full wave placement of every new piece this document adds. No existing Wave-1 system is removed or reordered |
| §8, Repository layout | Lists `agents/`, `knowledge/`, `tools/`, `memory/`, `runs/`, `variables/`, `analytics/`, `guardrails/`, `prompts/` | Add `deployments/`, `scenarios/`, `session_scoring/` — see ADR-003 §8 |
| §9, Rejected-alternatives table | No entry for telephony/meeting-bot vendor selection | Add: "Picking a telephony/meeting-bot vendor inside this ADR" — rejected, see ADR-003 §6.2/§6.3 and §11 below; each needs its own dedicated ADR with the same comparison rigor ADR-000/ADR-001 already established |

Per ADR-000 §11, the original text in each location is left intact rather than deleted, as the historical
record of what was decided and why at the time; each location gets a short pointer to this document.

---

## 10. Delivery plan — sequencing every new piece into Wave 1/2/3

Sole engineer; sequencing is the architecture (ADR-000 §7's own framing, unchanged). Nothing below reorders
an existing Wave-1/2/3 item; every row is an addition.

| Wave | Item | Why here |
|---|---|---|
| **1** | `agents.kind` column + `AgentCreate`/`AgentRead` schema changes (§1) | Additive migration, zero runtime dependency, unblocks every later item that branches on kind. Cheapest possible slice |
| **1** | `deployments` table + CRUD, `channel="api"` only wired to a real check (§2, §2.4) | `api` channel needs no new adapter — `POST /v1/agents/{id}/runs` already exists. This is the resource that resolves "what decides deployability" (§3) and should land early precisely because it's cheap and unblocks the conversation, not because anything downstream needs it |
| **1** | `deployments.channel` reserving `"web_widget"`, `"share_link"`, `"voice_telephony"`, `"meeting_bot"` as literals with no adapter (§2.2) | Schema stability — adding a literal to an enum later is free; adding it now costs nothing and avoids a migration when Wave 2/3 adapters land |
| **2** | `scenarios` module — registry CRUD + `:publish-as-scenario` + plain-copy sub-case of `:remix`/`:draft-from-prompt` (§5, §5.3) | Pure config-plane copy semantics, no LLM call in the base case — buildable once the config-snapshot shape is stable, no runtime dependency |
| **2** | `deployments` — `web_widget`, `share_link` channels get real config validation (still no new runtime, both ride the existing `api`/SSE transport under the hood) | Config-plane work only; no new port |
| **2** | `session_scoring` config module (settings object, PUT/GET only) (§4) | Same shape as `memory_config`/`guardrail_policy`, both already built — config storage with zero runtime dependency |
| **2** | `tools.adapter_type = "client_rendered"` registry support (schema/CRUD only, no runtime emission) (§7) | Registry-only change, same shape as adding any new enum value with a validated companion column |
| **2** | `memory` storage tables gain `participant_id` (§6.1) | Contingent on `memory`'s storage tables being built at all (still not started per the current inventory) — this ADR specifies the column now so whichever PR builds those tables doesn't retrofit it |
| **3** | LLM-assisted rewrite sub-case of `:remix`/`:draft-from-prompt` (`prompt_hint`/`prompt` actually driving a `StructuredOutputPort` rewrite) (§5.3) | Depends on `StructuredOutputPort` having a real adapter, not a `Null*` stub — currently unbuilt per the inventory |
| **3** | `kind == "workflow"` runtime graph + deployability check (§1, §3 item 2) | Depends on `runtime/` existing at all (currently two files) and a workflow-triggering path being designed — behind the v0.1 conversational vertical slice ADR-000 §7 already prioritizes |
| **3** | Agent composition via agents-as-MCP-server (§13) | Unchanged from ADR-000 §7's own existing Wave-3 item — not moved earlier by this document |
| **3** | `session_scoring` — actual scoring runtime (transcript-only mode) | Depends on `runs/` producing a real, completed transcript to score — behind the v0.1 slice |
| **3** | `session_scoring` — multimodal mode, pending the open provider-capability question (§4) | Explicitly unresolved by this ADR; revisit once a provider/model with acceptable multimodal input support and cost is identified |
| **3** | `client_rendered` tool runtime emission (`custom` stream event) (§7.1) | Depends on `runtime/` existing and the streaming/`custom`-event machinery being built |
| **3 (contingent)** | `VoiceTelephonyPort` real adapter | Blocked on its own dedicated future ADR (§6.2) — not scheduled until that ADR is written and a vendor is chosen |
| **3 (contingent)** | `MeetingBotPort` real adapter | Blocked on its own dedicated future ADR (§6.3) — same caveat |

---

## 11. Rejected, with reasons

| Rejected | Reason |
|---|---|
| Overloading `agents.status` to also mean "deployed" (e.g. adding a `"deployed"` status value) | Conflates config existence with channel reachability — an agent can be `active` and deployed to zero, one, or many channels simultaneously; a single status enum can't express "many" |
| A single `agent_kind`/"mode" flag instead of a `kind` column plus separate `deployments`/`scenarios` resources | Was the shape of the original confusion — a single enum trying to answer "conversational vs workflow," "deployed vs not," and "published vs not" at once is exactly the ambiguity this ADR removes |
| Modeling `scenarios` as a boolean flag on `agents` (`is_template: bool`) | Can't hold a snapshot, a visibility tier, or survive the source agent being edited/archived/deleted after publication — `config_snapshot` requires its own row regardless |
| Naming the template-library module `templates` | Hard constraint — collides with the repo-root Copier scaffold directory (`templates/module/`) already serving an unrelated purpose |
| A fourth `ToolPort` adapter for client-rendered tools | `ToolPort`'s methods model a backend-initiated call/response; a client-rendered tool is never called by this backend — see §7.2 |
| Picking a telephony vendor (Twilio/Vonage/Plivo/Vapi/Retell) inside this ADR | Hard constraint — new vendor/infra decisions require their own dedicated ADR with the same comparison rigor ADR-000/ADR-001 used |
| Picking a meeting-bot SDK (Recall.ai, build-it-ourselves) inside this ADR | Same reasoning, see §6.3 |
| Building `deployments`/`scenarios`/`session_scoring` as sub-resources nested inside the `agents/` module rather than sibling modules | Would violate the module-per-resource convention every existing binding/registry (`knowledge`, `tools`, `memory`, `guardrails`, `config`) already follows |
| Making `deployments` a consumer of `build_agent_binding_router` | The factory's own docstring scopes it to whole-resource-atomic-PUT singleton/set semantics; `deployments` needs per-row identity and independent lifecycle |
| Nullable `tenant_id`/`workspace_id` on `scenarios` for platform-owned rows | Directly contradicted ADR-000 §4's unconditional "every table carries `tenant_id`... and an RLS policy" rule — caught during adversarial review of this document's own first draft. Fixed by reserving one designated platform tenant (`tenants.is_platform`, §5.1) instead; `tenant_id` stays `NOT NULL` everywhere, RLS gets one documented OR-clause rather than a NULL-tenant special case |
| Reusing the word "schedule" for both a workflow-kind agent's Temporal trigger and a deployment's recurring-join cadence | The exact one-word/two-concepts collision this whole document exists to eliminate — caught during adversarial review. Renamed the deployment-side field to `recurrence`; "Schedule" is now reserved exclusively for §2 table row 3's meaning |
| A new port/table for agent-to-agent composition (multi-agent pipelines) | Unnecessary — ADR-000 §7's already-planned Wave-3 "agents-as-MCP-server" item already gives one agent a way to invoke another, expressed as an ordinary `tools` registry row. See §13 |

---

## 12. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **`kind` immutability might prove too rigid** — a tenant may reasonably want to convert a conversational agent into a workflow one without losing its `id`/history | Documented as a deliberate constraint (§1.1); the escape hatch is `:publish-as-scenario` + `:remix` into a new agent of the other kind. Revisit if real usage shows this friction is common enough to justify a dedicated "convert kind" endpoint |
| 2 | **`deployments`/`scenarios`/`session_scoring` add three more tenant-scoped tables to RLS-verify, three more modules to keep `jscpd`-clean, three more OpenAPI surfaces to keep the schema-diff gate honest** — non-trivial solo-engineer load per ADR-000 §10 risk #1 | Wave-sequenced deliberately thin in Wave 1 (§10) — `deployments` ships with exactly one working channel (`api`) at first, `scenarios` ships with plain-copy authoring only; both follow the existing binding-router/registry pattern rather than inventing a new shape |
| 3 | **`config_snapshot` (JSONB) on `scenarios` can drift out of sync with `agents`/`config`/`tools`/`knowledge` schema changes over time** — a snapshot format frozen at publish time, replayed at remix time, against schemas that keep evolving | No versioning field is added to `config_snapshot` by this ADR — flagged as a known gap; the honest fix (a `snapshot_schema_version` column + migration path) is deferred until the first real schema drift is observed, not solved speculatively |
| 4 | **Two new ports (`VoiceTelephonyPort`, `MeetingBotPort`) are named without method signatures finalized** — a future ADR could pick shapes that don't fit `deployments.channel_config`/`recurrence` as anticipated in §6.2/§6.3 | Accepted deliberately — guessing signatures now would be exactly the casual vendor/infra decision this ADR is required not to make. `channel_config` is JSONB specifically so it can absorb whatever shape the eventual ADRs choose without a schema migration |
| 5 | **`client_rendered` tools have no server-side invocation to smoke-test** — `:test-invoke`'s existing "live diagnostic" value proposition (ADR-000 §2.8) doesn't apply | `:refresh-schema`'s degraded validate-JSON-Schema-shape behavior (§7.1) is the only server-side check available; document this limitation explicitly in the eventual `tools/README.md` update rather than pretend parity with `mcp`/`http` tools exists |
| 6 | **Multimodal session-scoring's provider-capability question is unresolved** — Wave 3 work could stall on discovering no current LLM provider in our routing table supports acceptable video input | Flagged explicitly as open (§4/§10); the fallback if no provider qualifies is to scope multimodal analysis down to voice-only rather than block the whole feature — a decision to make at Wave 3 time with then-current provider capabilities |
| 7 | **A reserved "platform" tenant row (§5.1) is a special case every RLS policy and every "list my tenant's X" query touching `scenarios` must remember to carry** (`OR visibility = 'platform'`) — easy to forget in a new query and silently under- or over-return rows | Regression-test from day one, following this repo's existing "RLS tested through a real non-superuser role" convention (`app_role_session_factory`): one dedicated test asserting a `scenarios` query returns platform rows for every tenant and never leaks another tenant's `private`/`tenant`-visibility rows, written alongside the first `scenarios` migration, not retrofitted |

---

## 13. Agent composition — one agent invoking another (multi-agent pipelines)

A real target scenario (§14.D) requires one agent or workflow to invoke another as a step — e.g. a support
agent that raises a ticket, hands it to a second agent that generates a code fix, then a third step that
verifies the fix. **No new mechanism is needed for this — it already exists, unchanged, in ADR-000 §2.8's
own text:** *"We also expose our agents as an MCP server (phase 3) so external hosts can invoke them."*

Once that lands (already Wave 3 in ADR-000 §7, unchanged by this document), invoking "another agent" from
inside an agent's own tool-calling loop is not a new port, table, or concept — it's registering that other
agent's MCP-server endpoint as an ordinary `tools` registry row (`adapter_type="mcp"`) and binding it,
exactly like any third-party MCP tool. A `workflow`-kind orchestrator agent with three such tool bindings
(a ticket-intake agent, a code-fix agent, a verification agent, each exposed as its own MCP endpoint) *is*
§14.D, expressible entirely with primitives ADR-000 and this document already define. This section exists
only to make that connection explicit and confirm no gap exists here.

**Sequencing:** unchanged, still Wave 3 (ADR-000 §7) — this document does not move it earlier, since it
depends on the same not-yet-built runtime plane every other Wave-3 item here does.

---

## 14. Worked examples — validating this model against four concrete target agents

Acceptance-test scenarios, not new mechanism — each maps entirely onto §1-§13 above and ADR-000's existing
tool model, included so the schema can be checked against real intent rather than only internal
consistency.

| # | Agent | `kind` | Deployment | Tools/MCPs needed | Notes |
|---|---|---|---|---|---|
| A | Weekly LinkedIn content-posting agent — drafts from the tenant's own site + industry content | `workflow` | none (Schedule only — a weekly Temporal trigger) | web-search/content-fetch tool, a LinkedIn-posting MCP tool | Textbook `workflow`-kind: no end-user-facing channel at all, defaults to Schedule per §2.5, never needs a `deployments` row |
| B | Autonomous outreach/BDR agent — finds tool-directory sites, emails personalized listing proposals enriched with the tenant's own live traffic stats | `workflow` | none (Schedule, or a Wave-3 workflow-triggered ad hoc run) | web-search tool, email-send tool, an internal-dashboard-read tool (the tenant's own API, `HttpToolAdapter`) | Same shape as A — "needs a lot of tools, discovers them itself" is exactly ADR-000 §2.8's MCP-first model; no special discovery mechanism needed beyond registering more tools in the workspace's `tools` registry |
| C | Embedded website FAQ/conversion agent — answers "how do I get listed," cites live stats, hands off to checkout | `conversational` | `web_widget` or `share_link` (§2.2) | a knowledge base (FAQ/stats) + a `client_rendered` tool (§7) for the deep-link/navigate-to-checkout action | Defaults to Deploy per §2.5; "navigate the visitor to a page" is exactly the `client_rendered` tool type §7 adds, not a new mechanism |
| D | Support pipeline — video bug report → ticket → code fix → verify | `workflow` orchestrator composing sub-agents via §13 | none directly (Schedule/webhook-triggered on ticket creation) | a ticketing MCP tool, a code-repository MCP tool, a coding-LLM tool, plus each sub-agent's own MCP-server endpoint (§13) | The clearest test of §13 — multi-step, verifiable, tool-using pipeline, expressible with zero new primitives beyond ADR-000 §7's own Wave-3 "agents-as-MCP-server" item |

None of A-D required a new mechanism beyond what §1-§13 already define — this document's job was to give
the original confusion a name and a schema, not to invent new runtime capability beyond what ADR-000
already scoped.

---

## 15. Terminology note — "Employee" framing (documentation/copy only, no API change)

The stated preference is to frame an agent as a hire, not a config object ("I hired Rishikesh for sales,
now I'll hire this one for outreach"), matching ToughTongue's own "AI teammates"/"home of the AI workforce"
marketing language. **This is a documentation/copy recommendation only — the API resource stays named
`agents`, unchanged** — renaming a shipped resource is a breaking change this ADR has no reason to force.
Recommendation for any future-facing documentation, module README prose, or a UI built elsewhere: refer to
an agent informally as "an AI teammate you hire/configure," and to `agents.kind` as answering "does this
teammate talk to people directly, or work in the background." No schema or endpoint is affected by this
section.

---

## 16. Amendment rule

Unchanged from ADR-000 §11: any change to this document is a new ADR that states what it supersedes and
why. In particular, the two deferred vendor ADRs this document anticipates (§6.2, §6.3) are expected to
supersede this ADR's §6.2/§6.3 placeholder text the same way ADR-001 superseded parts of ADR-000 — named
here so whoever writes them knows the convention to follow.
