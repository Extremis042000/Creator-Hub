# ADR-000 — Finalized Stack & Architecture

**Status:** Accepted · **Date:** 2026-07-31 · **Decider:** Lakshya Tyagi (sole engineer)
**Supersedes:** nothing · **Review trigger:** second engineer joins, or first paying enterprise customer
**Amended by:** [ADR-001](ADR-001-qdrant-vector-store.md) — vector store: pgvector → Qdrant (§2.2, §2.2.1,
§2.4, §4, §7, §8, §9, §10 risk #3; original text below is left as the historical record, not deleted).
[ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) — agent authoring, orchestration &
deployment model: product-definition wording (§1), tools registry gains a third registry-only
`adapter_type` (§2.8), database RLS rule elaborated for one library-visibility case (§4), transports gain a
forward pointer to two later-wave vendor-TBD ports (§2.6), delivery plan and repository layout gain new
items (§7, §8), rejected-alternatives table gains one entry (§9); original text below is left as the
historical record, not deleted.

This ADR is the single source of truth for *what we use and why*. Every subsequent ADR references it.
Nothing in this repository may introduce a new language, framework, datastore, or third-party service
without a new ADR that amends this one.

---

## 1. Product definition and the independence rule

`geta-agentic-ai` is a **standalone, API-only agentic chatbot backend**. It is consumed over REST,
webhooks and streaming transports. It is not a library, not a plugin, and not a module of any other
system.

> **Amended by [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §0.1 — wording only.**
> "Chatbot" undersells what the MCP-first tool model in §2.8 already supports (any domain, not just chat —
> accounting, auditing, marketing, autonomous workflows). Recommended replacement: *"a standalone, API-only
> agentic AI-workforce backend — conversational, voice, and autonomous-workflow agents composed from
> tools/MCPs to do any task a tenant configures, not limited to chat."* The **HARD RULE — NO COUPLING**
> immediately below is untouched by this or any amendment.

> **HARD RULE — NO COUPLING.** This service must never import from, share a database with, resolve a
> Docker DNS name of, or otherwise depend on any other Geta service (`geta-core`, `geta-auth-service`,
> `conversational-service-node`, or any other). Integration with the Geta product happens exclusively
> through this service's public HTTP API and webhooks, exactly as it would for any third-party customer.
> A pull request that violates this is rejected regardless of its other merits.

The service is split into two planes that share a process and a codebase but never share
responsibilities:

| Plane | Owns | Never does |
|---|---|---|
| **Config plane** (CRUD) | tenants, workspaces, agents, knowledge bases, tools, prompts, variables, policies, keys, flags | call an LLM, run a tool, hold a stream open |
| **Runtime plane** (agentic) | agent runs, planning, tool invocation, retrieval, memory, streaming, guardrails | write configuration, define policy, own business rules |

---

## 2. Locked stack

### 2.1 Core runtime

| Layer | Decision | Licence | Why |
|---|---|---|---|
| Language | **Python 3.13** (CPython, GIL build) | PSF-2.0 | Only ecosystem with production-grade libraries for *all* of: advanced chunking, reranking, PII redaction, prompt-injection detection, RAG evaluation. Rewriting those in TS/Go is the timeline killer, not the web layer. |
| API framework | **FastAPI** `>=0.141.1,<0.142` | MIT | Native SSE since 0.135 (keepalive + `X-Accel-Buffering: no` handled), OpenAPI 3.1 generation, largest ecosystem. Pinned exactly — FastAPI is 0.x and ships breaking changes in minor bumps. |
| Validation / typing | **Pydantic v2** `>=2.13,<3` | MIT | One model definition serves DB row shape, API contract, OpenAPI schema, **and** enforced LLM structured output. This is what makes "never parse free text" cheap. |
| ASGI server | **uvicorn** `>=0.52,<0.53` (Granian evaluated as a later swap) | BSD-3 | Boring and proven. Granian revisited only if profiling shows the server is the bottleneck. |
| DI container | **Dishka** `>=1.10,<2` | Apache-2.0 | FastAPI's `Depends` is request-scoped function wiring, not IoC — it leaks the framework into the service layer. Dishka gives real APP/REQUEST scopes that map onto tenant/workspace/agent. |
| Agent runtime | **LangGraph** `>=1.2,<2` + `langgraph-checkpoint-postgres` `>=3.1,<4` | MIT | Explicit, inspectable state machine; checkpoint-per-node durability; `interrupt()` human-in-the-loop with resume semantics; richest streaming controls (`values`/`updates`/`messages`/`custom`); by far the most production-proven option (Klarna, Uber, LinkedIn, BlackRock, JPMorgan, Cisco, Replit). Stable major since Oct 2025. |
| Model abstraction | **`langchain-core`** + `langchain-openai` | MIT | Unavoidable transitive dependency of LangGraph (message types, `BaseChatModel`). Confined to the runtime layer by an import-linter contract (§2.1.1). |
| ORM / migrations | **SQLAlchemy 2.0** async + **Alembic** | MIT | Repository pattern, connection pooling, RLS policies expressed as migrations. |
| DB driver | **asyncpg** `>=0.31,<0.32` | Apache-2.0 | |

#### 2.1.1 LangGraph boundary rules (mandatory)

LangGraph was chosen for maturity and production evidence. It does **not** enforce four of this
project's hard rules the way a typed-agent framework would, so those rules are enforced by tooling
instead. These are not style guidance; CI fails on each.

| Rule LangGraph does not enforce | How we enforce it |
|---|---|
| **Orchestration must not leak into business logic.** Nodes take and return graph state, which pulls domain code toward `TypedDict` state and reducers. | Graph, nodes, edges and state live **only** in `src/agentic/runtime/`. An `import-linter` contract forbids importing `langgraph` or `langchain_core` anywhere else — `service/`, `repository/`, `router/` may not see them. Nodes are thin: they resolve deps, call a domain service, map the result into state. No business rule lives in a node. |
| **Prompts never in Python code.** LangGraph has no dynamic-instruction hook. | A `PromptResolver` port, injected through LangGraph 1.x runtime context, fetches the versioned prompt row at node entry and renders it in a `SandboxedEnvironment`. Prompt text is never threaded through graph state, never a closure constant, never a module-level string. A `ruff` custom rule + grep gate rejects string literals over 200 chars in `runtime/`. |
| **Structured output, never parse free text.** LangGraph treats this as a per-model config choice. | Every node that consumes model output declares a Pydantic model and goes through a single `StructuredOutputPort` wrapper that binds the schema, validates, and retries on validation failure. Calling a chat model directly from a node is banned by lint. Graph state schemas are **Pydantic `BaseModel`**, not `TypedDict`. |
| **Typed DI.** No `deps_type` equivalent. | LangGraph 1.x `context_schema` / `Runtime[Context]` carries a Pydantic `RuntimeContext`, built per request by **Dishka**. Globals are banned. See the design rules immediately below — the payload shape is what makes this safe across a suspend/resume boundary. |

**DI + LangGraph context design (mandatory).** Dishka is the composition root (`Scope.APP` for engines/
clients/the compiled `StateGraph`, `Scope.REQUEST` for tenant-scoped collaborators); LangGraph's
`context_schema`/`Runtime[Context]` is the *only* injection channel into nodes — no DI container calls
inside a node. Four rules, each closing a correctness trap specific to LangGraph:

- **`AgentCtx` is serializable and IDs-only** — `tenant_id`, `workspace_id`, `agent_id`, `thread_id`,
  `locale`, `byok_key_ref` (a reference, never a decrypted key). Heavy collaborators (DB session factory,
  LLM gateway, prompt resolver) are resolved *inside* the node from the container, not carried in
  context. Reason: runtime context is **not written to the checkpointer** — every resume, possibly hours
  later in a different pod, must re-supply it, so it must be cheap and reconstructable from IDs alone.
- **Never hold a live `AsyncSession` across an LLM call.** Carry `async_sessionmaker`, open a session
  per unit of work. At thousands of concurrent users against a 20-50 connection pool, holding a session
  for a 5-30 s model call exhausts the pool.
- **Nodes must be idempotent.** On resume, LangGraph re-executes a node **from its beginning** — any
  code before an `interrupt()` call runs a second time.
- **Use `context_schema`, never `config_schema`/`config['configurable']`** — deprecated since LangGraph
  0.6.0, removal planned for 2.0. (`thread_id` itself still lives in `config['configurable']`.)

**Test doubles.** No `TestModel`/`FunctionModel` equivalent ships with LangGraph, so the harness is ours:
`langchain_core.language_models.fake_chat_models` (`GenericFakeChatModel`, `FakeListChatModel`) for
scripted responses, `respx` at the HTTP layer, and **`pytest-socket` disabling network in the unit
suite** — that is our replacement for a framework-level "block all live model calls" switch, and it is
a required CI gate, not a convenience.

**Licence boundary.** Only the MIT packages are permitted: `langgraph`, `langgraph-checkpoint`,
`langgraph-checkpoint-postgres`. **`langgraph-api` / LangGraph Platform (Elastic License 2.0) is
banned** — we build our own HTTP, auth, persistence and config plane, so it buys us nothing and would
put an Elastic-licensed component in a product other companies pay to use. The `langchain`
meta-package and its chain abstractions are also banned; `langchain-core` is permitted solely as
LangGraph's message/model substrate.

**Observability.** LangGraph's gravity is toward LangSmith. We do not use it. Tracing goes through the
**Langfuse LangChain callback handler**, whose v3+ SDK emits OpenTelemetry spans, into the same OTLP
collector as everything else (§2.9). LangSmith is not a dependency.

> **Python 3.14 upgrade path:** deferred. Free-threaded builds (`cp314t`) still lack wheels for
> `grpcio` (OTLP gRPC exporter, Temporal SDK) and importing a non-ready C extension silently re-enables
> the GIL. Revisit when those wheels land; the GIL build of 3.14 is a low-risk bump before that.

### 2.2 Data

| Layer | Decision | Why |
|---|---|---|
| Primary DB | **PostgreSQL 17+ on Neon**, dev/staging **and production**, branch-per-PR for CI | See the accepted-risk box below — this is a deliberate override of the residency/latency-correct pick. |
| Tenancy | Shared schema + `tenant_id` on every table + **Postgres RLS**, partitioned by `tenant_bucket` (§2.2.1) | Belt: repository base class injects the tenant filter. Braces: RLS enforces at the engine. Partitioning: physical isolation, not just a `WHERE` clause. |
| Vector store | ~~pgvector, in the same Postgres — no separate vector database.~~ **Superseded by [ADR-001](ADR-001-qdrant-vector-store.md) — Qdrant, self-hosted on GKE Autopilot, `asia-south1`.** Original text kept below for the historical record. | ~~Eliminates the entire dual-write/reconciliation problem a second datastore (Qdrant) would have created — there is no foreign key across a Postgres↔vector-DB boundary to drift. One system to back up, monitor and operate.~~ |
| Cache / counters | **Valkey** (GCP Memorystore for Valkey) | BSD-3, drop-in Redis protocol, sidesteps the Redis RSALv2/SSPL licence question entirely. |
| Object storage | **GCS** (MinIO locally) | KB uploads, archived conversation Parquet, Temporal large-payload codec. |
| Usage/cost ledger | **Postgres + `pg_partman`** monthly partitions + rollups; BigQuery export for dashboards | The ledger must be transactional, joinable and hard-deletable. Langfuse is observability, never the billing source of truth. |

> **⚠ ACCEPTED RISK — Neon in production.** Neon has **no GCP region and no India region**; the nearest
> is AWS `ap-southeast-1` (Singapore), reached over the **public internet with no Private Service
> Connect**. This was flagged at high confidence as the wrong pick for residency and latency, and the
> decision to keep Neon anyway is a deliberate, informed override, not an oversight. Concrete
> consequences and mandatory mitigations:
> - **Latency:** every LangGraph node checkpoint and Temporal state persist pays a Mumbai↔Singapore
>   round trip (~50-60 ms typical, unmeasured on our actual path). This eats directly into the p95 TTFT
>   budget (§6). **Load-test the real number in staging before committing to it in production** — if it
>   threatens the 1.5 s RAG target, the escalation path is moving the primary DB to Cloud SQL/AlloyDB in
>   `asia-south1`, which is why every DB access already goes through the repository layer.
> - **DPDP residency:** Indian personal data is stored and transits outside India. Get explicit
>   legal sign-off that this is acceptable under the DPDP Act's cross-border transfer rules before
>   onboarding any Indian enterprise/regulated customer. If a customer contractually requires
>   India-only residency, the honest answer is a **dedicated single-tenant deployment** of the whole
>   stack in `asia-south1` for that customer — not a config flag. Treat that as a possible future
>   enterprise-tier product decision, not a technical band-aid.
> - **No private networking:** the database endpoint is reachable over the public internet. TLS is
>   mandatory (already default), and Neon's IP-allow feature should be enabled once client IPs are known.
> - **Pooler compatibility unverified:** confirm Neon's built-in pooler (PgBouncer, transaction mode)
>   is compatible with the `SET LOCAL app.tenant_id` pattern the RLS design depends on **before** writing
>   the repository layer — transaction-mode poolers commonly restrict session-level `SET`.
> - **Cost is not the concern.** The §2.2.1 pgvector design keeps the working set to tens of GB, not
>   hundreds — Neon can carry the compute load; the open questions are latency and residency, not size.

#### 2.2.1 pgvector production design (10M chunks, tenant-filtered) — superseded

> **Superseded by [ADR-001](ADR-001-qdrant-vector-store.md).** This subsection is kept in full below as
> the historical record of the original, fully-specified pgvector design — it is not the current design
> and must not be implemented. See ADR-001 §3 for the Qdrant equivalent (multitenancy, quantization,
> sharding) and ADR-001 §2 for why this design was replaced.

The naive shape — `vector(1536)` + one global HNSW index + `WHERE tenant_id = $1` — is **not viable**:
it needs ~146 GB resident, and worse, it **fails silently**: at the default `hnsw.ef_search=40`, a
filter selective enough to match our tenant sizes returns close to zero rows for a `LIMIT 20`, with no
error. pgvector's own README has a section titled *Multitenancy* warning about exactly this. The
production shape is specific and is not optional:

| Element | Setting | Why |
|---|---|---|
| Heap storage | `embedding halfvec(1536)`, chunk **text in a separate table**, `ALTER TABLE ... SET (toast_tuple_target = 8160)` | `halfvec`/`vector` are declared `STORAGE = external` in pgvector; a halfvec row past ~2032 bytes gets pushed out of line, and every rescored candidate then costs a TOAST lookup. This single setting is the highest-value line in the design. |
| Index | `HNSW` over `binary_quantize(embedding)::bit(1536)`, `m=32`, `ef_construction=200` | ~16× smaller than a float32 index at the pgvector maintainer's own measured recall (99.0% vs 99.6% at `ef_search=200`, and *faster*, not just smaller). |
| Query | wide binary candidate scan (`LIMIT 500`) → **exact rescore on `halfvec`** (`LIMIT 20`) | The standard two-stage pattern pgvector's own docs recommend for binary quantization. |
| Partitioning | `PARTITION BY LIST (tenant_bucket)` — whale tenants get a dedicated bucket, everyone else hashes into ~32 shared buckets, **local HNSW per partition** | Every query passes `tenant_bucket` so the planner prunes to one partition. This is what actually fixes the filtered-recall problem — iterative scans (`hnsw.iterative_scan`) are a correctness backstop, not the plan. |
| Session GUCs | `hnsw.ef_search=200`, `hnsw.iterative_scan=relaxed_order` (backstop only), `hnsw.max_scan_tuples=20000` | |

**Estimated working set with this design: tens of GB, not hundreds** — comfortable on a right-sized
instance. Treat the exact GB figures as estimates to be confirmed by load test, not committed numbers;
independent verification found ~20% arithmetic drift in similar published estimates once page-level
storage overhead is applied correctly.

**Build hazard:** pgvector builds the HNSW graph in `maintenance_work_mem`; when it overflows it logs
`NOTICE: hnsw graph no longer fits into maintenance_work_mem` and build time degrades sharply. Alarm on
that message. **Version floor: pgvector `>=0.8.4`** for the HNSW-relevant fixes (0.8.5/0.8.6 are
IVFFlat-only). Neon's shipped version must be confirmed against this floor before the first migration
that creates the index; if it lags, schedule rolling `REINDEX INDEX CONCURRENTLY` per partition as
mitigation (each partition's `bit(1536)` index is small and rebuilds in seconds, precisely because we
partitioned).

**CI gate:** an integration test asserts `EXPLAIN` shows an `Index Scan` on the tenant's own partition
for a representative query — not a fallback to a sequential or cross-partition scan.

### 2.3 LLM access

| Layer | Decision |
|---|---|
| Gateway | **Our own `LLMGateway` port.** Cost accounting, per-tenant budgets, retries, circuit breaker, caching, model registry all live in our code, never in a vendor. |
| Routing | **BYOK option B** — tenants supply native provider keys (OpenAI / Anthropic / Google / **xAI, added 2026-08-12** — same BYOK/OpenRouter-fallback pattern as the original three; OpenRouter's own `x-ai/*` slugs already covered it as the long-tail fallback before this), routed direct. **OpenRouter** serves the long tail and any tenant without their own keys. Resolution order (`platform/llm_gateway.py::RoutedLLMGateway`, **added 2026-08-12**): tenant's own active BYOK key (`provider_keys` module) → platform-default key for that provider (from `Settings`) → OpenRouter → `NullLLMGateway` if nothing is configured at all. Platform-default-key traffic (i.e. `used_byok=False`) is billed at **cost + 10%** (`Settings.platform_key_markup_multiplier`, default `1.10`); a tenant's own BYOK traffic is billed at cost. |
| Key storage | AES-GCM envelope encryption, DEK wrapped by **Cloud KMS**. Never logged, never returned by any API, last-used tracking. **Built 2026-08-12** — the `provider_keys` module (self-service `POST/GET/:rotate/DELETE /v1/provider-keys`) stores a per-tenant encrypted row per provider via `platform.ports.KeyStorePort` (`KmsKeyStore` when `Settings.kms_key_resource_name` is set, else `NullKeyStore` — writes 503 until KMS is configured). Per-key circuit breaker not yet built. |
| Model identifiers | **DB config rows.** No model name, no context limit, no price appears as a Python constant. |
| Pricing table | DB table keyed by `(provider, model, tier, cached|fresh)` with `valid_from` — cost is computed, not hardcoded, and is recomputable historically. |
| Embeddings | **Separate call path from chat, direct-to-provider, not proxied through OpenRouter.** Behind the same `EmbeddingPort` pattern so the provider is still a config swap, but embedding calls need `outputDimensionality`, `task_type` (`RETRIEVAL_DOCUMENT`/`RETRIEVAL_QUERY`) and region pinning that a generic multi-provider proxy is not guaranteed to preserve, and ingest is a high-volume, cost-sensitive, latency-insensitive-per-call bulk workload where a direct billing relationship matters more than provider breadth. Chat/agent traffic keeps the OpenRouter+BYOK routing above unchanged. |

### 2.4 Retrieval (RAG)

| Stage | Decision |
|---|---|
| Parsing | **Google Document AI** primary — two-tier router (Enterprise OCR v2.1 for scans/bulk, Layout Parser v1.0 for structured PDF/DOCX, consuming `document.chunked_document.chunks` directly), **Azure Document Intelligence `prebuilt-layout`** failover, **Docling** self-hosted offline path — all behind a `DocumentParser` port |
| Crawling | `Crawler` port — Trafilatura extraction, robots.txt respected, sitemap ingest, freshness re-crawl on a Temporal Schedule |
| Chunking | Layout-aware split, then **deterministic context prefix** (document title + section heading path + metadata) prepended before embedding. **LLM-generated context is opt-in per knowledge base**, run via Batch API + prompt caching on a Haiku-class model, with the cost estimated and surfaced before the job runs. Chunk target ~1,000-1,500 characters — stays inside the embedding model's 2,048-token input cap. |
| Embedding | **`gemini-embedding-001` via Vertex AI, called directly (not through OpenRouter), region-pinned to `asia-south1`, `outputDimensionality=1536`** (MRL truncation, L2-renormalized) | see box below |
| Search | **Hybrid** — dense (~~pgvector~~ **Qdrant, see [ADR-001](ADR-001-qdrant-vector-store.md)**) + lexical, RRF fusion, tenant-filtered — see Hindi note below |
| Rerank | **Cohere Rerank** primary, `BAAI/bge-reranker-v2-m3` as the self-hosted degrade path |
| Citations | Per-agent toggle; chunk id, document id, page/offset and score returned with every cited span |
| Scale target | 10M chunks in year 1 |

> **Embedding model — decided.** On XTREME-UP cross-lingual retrieval (MRR@10), Gemini Embedding scores
> **69.1 on Hindi vs OpenAI `text-embedding-3-large`'s 40.4** — not a rounding difference, and worse
> still on Marathi (68.8 vs 25.5). Neither Cohere `embed-v4` nor Voyage publish any Hindi/Indic numbers
> at all, so they were an unhedged bet on our primary language; this is not. **1536 dims** was chosen
> over 1024 because it is an explicitly documented, supported MRL truncation point for this model —
> 1024 support was unconfirmed and not worth gambling a 10M-chunk ingest on. `gemini-embedding-001` is
> GA and served from `asia-south1`, keeping ingest in-region for DPDP. Model id, dimension and
> normalization are immutable metadata on every collection, so a future swap (e.g. to `gemini-
> embedding-2` once it exits Preview) is a config change plus a backfill, not a code change. Marathi
> stays a phase-2 language per the product roadmap; re-run this same eval before Marathi ships.
>
> **Why direct Vertex AI, not OpenRouter, even though OpenRouter access is available:** the model
> choice is decided by the benchmark above regardless of routing. Direct Vertex AI access is chosen
> for the *call*, not the *model*, because embedding calls need `outputDimensionality`, `task_type`
> and region-pinning control that a generic proxy is not guaranteed to preserve faithfully, and because
> 10M chunks is a high-volume bulk workload where a direct billing relationship and predictable latency
> matter more than provider breadth. The `EmbeddingPort` interface keeps this swappable, including to
> a route through OpenRouter later if that becomes preferable.

> **Hindi hybrid search — the trap and the design.** `to_tsvector('hindi', ...)` **exists in Postgres
> and is a trap**: it maps का/की/के all to the single codepoint क and ships no stopword list. Engineers
> find it, assume Hindi is supported, and ship broken lexical search. The mandatory design instead:
> `to_tsvector('simple', body_norm)` + `ts_delete()` with our own Hindi stopword list, **NFC
> normalization, ZWJ/ZWNJ stripping, and vowel-sign folding (ँ→ं) applied at both ingest and query time**
> — without the last step, कहाँ vs कहां score only 0.43 trigram similarity. **Launch scope (B1):** Gemini
> dense + this normalized `simple`-config lexical channel, RRF-fused, no separate inference service to
> operate. **Deferred (B2):** ~~a `sparse sparsevec` column sized for BGE-M3's learned sparse output
> (top-128 non-zeros — pgvector's HNSW hard-fails above 1,000 non-zeros, a documented footgun)~~
> **superseded by [ADR-001](ADR-001-qdrant-vector-store.md) §2 — a native Qdrant sparse named vector on
> the same point as the dense one, no separate column and no non-zero ceiling at BGE-M3's top-128
> output** — populated by a self-hosted BGE-M3 pod on GKE if measured Hindi retrieval quality demands it.
> BGE-M3's single forward pass gives both dense and sparse, and its subword tokenization handles
> Devanagari morphology the Postgres stemmer destroys — the reason to hold it in reserve rather than
> reject it. Also note: **Devanagari is 3 bytes/codepoint in UTF-8** — size all Hindi content estimates
> at roughly 2.5× the English-derived number.

### 2.5 Execution

| Layer | Decision | Why |
|---|---|---|
| Durable execution | **Temporal Cloud** (`temporalio` Python SDK `>=1.31`), `temporalio/auto-setup` in local compose | Event-sourced replay: a worker dying at LLM call 8 of 12 resumes at 9 and does not re-pay for 1-8. `WorkflowIdReusePolicy` makes duplicate webhooks idempotent by construction. Fairness keys map to tenant id. Search Attributes index `tenant_id`/`workspace_id`/`agent_id` on every execution, giving the dashboard APIs a queryable substrate for free. Cloud, not self-hosted, because self-hosting is 4 server roles + OpenSearch and there is no on-prem requirement. |

**Durability split — no overlap.** LangGraph and Temporal both persist state, so the boundary is
explicit and enforced:

| Scope | Owner |
|---|---|
| **Inside one agent run** — node-level checkpoints, `interrupt()` / resume, tool-approval waits, mid-run recovery | **LangGraph** `langgraph-checkpoint-postgres`, keyed by `thread_id` |
| **Across runs** — KB ingestion, freshness re-crawls, webhook delivery + DLQ, scheduled jobs, GDPR deletion cascades, test-suite execution, and *invoking* an agent run | **Temporal** |

A Temporal Activity invokes a LangGraph run by `thread_id`. If the Activity retries, LangGraph resumes
from its last checkpoint rather than replaying completed LLM calls — retries stay cheap and idempotent
without a second replay model.

Temporal's official **LangGraph durable-execution plugin (Public Preview, 2026-07-16)** is deliberately
**not** adopted at this stage: Public Preview is not a dependency a solo engineer should put on the
critical path, and the checkpointer boundary above achieves the same outcome with MIT-stable pieces.
Revisit when it reaches GA.

**Accepted costs:** every LLM/DB/HTTP call must live in an Activity (determinism sandbox); ~2 MB payload
limit means long transcripts and chunk batches need the GCS payload codec from day one; there is no
built-in DLQ, so the failed-run table and replay endpoint are ours to build; and the LangGraph
checkpoint tables are an extra schema in Postgres with their own retention and pruning job.

**Rejected:** Trigger.dev (TypeScript-first — Python is subprocess shelling; self-host drops the
checkpoints and warm starts that make long agent waits cheap; per-second compute billing is wrong for
runs that are ~95% idle waiting on providers). Inngest (SSPL — blocker for a commercial multi-tenant
platform; Python SDK at 0.5.x; HTTP-callback model inverts network topology). Restate (BSL 1.1, and
the Python SDK is its least-exercised). Hatchet is the designated fallback if the determinism model
proves too costly — MIT, Postgres-only, but pre-1.0 with two breaking rewrites in two years.

### 2.6 Transports

All three, because the calling product needs all three:

| Transport | Use |
|---|---|
| **SSE** (`text/event-stream`) | interactive token streaming — default |
| **WebSocket** | mid-run interrupt and human-in-the-loop tool approval; scaled via Valkey pub/sub, no sticky sessions |
| **Async + webhook** | long or unattended runs on unreliable networks — `POST` returns a run id, results delivered by signed webhook |

Every event carries a typed envelope: `event`, `run_id`, `trace_id`, `seq`, `data`. The final event of
every stream is `usage` (§5).

> **Forward pointer, [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §6.2/§6.3.** Two more
> transport-adjacent ports are *named, not designed*: `VoiceTelephonyPort` (voice/telephony calling) and
> `MeetingBotPort` (joining a live Google Meet/Zoom call). Both are later-wave, vendor-TBD, and each
> requires its own dedicated future ADR before any adapter is built — this table above is unchanged and
> remains authoritative for what's actually live today.

### 2.7 Auth, authorization, security

| Concern | Decision |
|---|---|
| Authentication | **Self-issued RS256 JWT** (`pyjwt[crypto]`, JWKS published, rotation via `joserfc`) + hashed API keys. **No external IdP** — this product has zero human login surface, so Keycloak/Zitadel/Auth0 would add a stateful service, a Helm chart, an upgrade treadmill and an outage mode to serve one `client_credentials` grant. |
| API key format | `gta_live_` / `gta_test_` prefix + 32-byte CSPRNG + base62 + CRC32 checksum. Store **`HMAC-SHA256(key, pepper)` only**. Scopes, last-used, rotation, per-key circuit breaker. |
| Authorization | **RBAC tables + Postgres RLS**, behind an `AuthorizationService` port. The model is a hierarchy (tenant → workspace → agent), not a sharing graph. **OpenFGA** `v1.18` is the pre-decided swap for the day cross-workspace agent sharing ships. |
| Webhooks | **Standard Webhooks** HMAC-SHA256, both directions |
| Fallback IdP | `quay.io/keycloak/keycloak:26.7.0` pinned in docs — added only if a human admin console or SSO is ever required |

**Guardrails — own the policy plane, buy the detectors.** Every guardrail framework (NeMo, Guardrails
AI, LLM Guard) assumes policy lives in files, that there is one tenant, and that the framework owns the
LLM loop. All three are false here, so we write the multi-tenant DB-driven policy engine and buy only
the parts that are genuinely hard:

**Prompt injection — the real attack surface is Hinglish and Unicode obfuscation, not Devanagari script.**
Verified findings that shaped this design: character-injection/emoji-smuggling evasion defeats every
production guardrail tested (Vijil, ProtectAI, Azure Prompt Shield, Meta Prompt Guard) at up to 100%
attack success when Unicode isn't normalized first, against a 98-100% baseline before evasion. Separately,
romanized Hindi ("Hinglish") code-mixed with phonetic perturbation reaches up to 99% attack success
because the perturbation splits tokens so template-based filters never fire — and no per-language Hindi
*prompt-injection* benchmark (as opposed to content-safety benchmark) exists from any vendor, so every
claim below is treated as a vendor statement, not a proven guarantee, until our own red-team suite
confirms it.

- **Normalization runs before every detector, no exceptions.** NFC normalization, strip zero-width
  joiners/non-joiners, fold homoglyphs and Devanagari vowel-sign variants. This single control is what
  closes the 100%-attack-success hole above; skipping it makes every detector below theatre.
- **Primary detector: AWS Bedrock Guardrails, Standard tier**, called standalone via `ApplyGuardrail`
  (works against non-Bedrock and BYOK models). The only vendor found that explicitly documents Hindi —
  **and Hindi written in Latin script** — as "Optimized and supported" for prompt-attack detection. This
  is a cross-cloud call from a GCP-hosted service; budget its latency into the guardrail stage and give
  it its own circuit breaker (§10, risk 11).
- **Self-hosted backstop: Qwen3Guard** (Apache-2.0, GKE), the only permissively-licensed guard model with
  a dedicated jailbreak-input category. Runs as a second opinion / ensemble signal and as the fallback
  if AWS Bedrock Guardrails is unreachable — not as sole authority, since it has no published per-language
  Hindi injection number either.
- **Route Hinglish through the same pipeline as Devanagari.** A script-based router that treats Latin-
  script text as "English policy" misses the primary real-world attack surface entirely.
- **Translate-then-scan is explicitly rejected as a primary defense** — translation normalizes away the
  attack itself (homoglyphs, zero-width characters, emoji smuggling, phonetic perturbation), so the
  detector sees clean text while the downstream LLM sees the original poison. Kept only as a secondary
  signal: a large semantic delta between original and translation is itself an alarm.
- Applied to user input **and to retrieved chunks, tool outputs, and MCP tool descriptions** — indirect
  injection is the vector people miss.
- **PII** — Presidio `2.2.364` redaction before anything reaches a log, plus image redaction for scanned
  KB documents. **Custom recognizers required for Aadhaar (Verhoeff checksum), PAN, Indian mobile, IFSC,
  UPI ID** — Presidio ships English-centric recognizers and would otherwise let Indian PII into logs.
- **Output** — Pydantic strict schemas. Never trust an LLM's output; never parse free text.
- **SSRF** — resolve DNS, reject private / link-local / cloud-metadata ranges, **pin the resolved IP and
  connect to it** (defeats DNS rebinding), tenant-scoped URL allow-list.
- **Prompt templates** — rendered through `jinja2.sandbox.SandboxedEnvironment`; tenant-editable
  templates are untrusted input.

### 2.8 Tools

**MCP-first.** One `Tool` port, two adapters:

- `MCPToolAdapter` — stdio and streamable-HTTP transports, per-tenant server configs in the DB, OAuth or
  token per connection. Primary path.
- `HttpToolAdapter` — OpenAPI 3.1 spec compiled to a tool schema, for anything without an MCP server.

**No user-supplied code execution.** That needs E2B/gVisor/Firecracker sandboxing, egress isolation and
per-tenant resource quotas — deferred until a customer pays for it.

A tenant's MCP server is untrusted: tool descriptions returned by it enter our prompt, so they are an
injection vector. Descriptions are scanned on registration, the tool schema hash is pinned, and drift
raises an alarm.

We also **expose our agents as an MCP server** (phase 3) so external hosts can invoke them. This is also
the mechanism [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §13 relies on for one agent
invoking another (multi-agent pipelines) — registering another agent's MCP-server endpoint as an ordinary
tool, no new port.

> **Amended by [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §7.** The `tools` registry
> gains a third, **registry-only** `adapter_type`, `client_rendered` (whiteboard/slides/browser-drive/
> webcam-style interactive tools rendered by an external client, never invoked through `ToolPort`). This
> amends the "one `Tool` port, two adapters" statement above: `ToolPort` itself still has exactly two
> *invocable* adapters — `client_rendered` entries never reach the port at all. See ADR-003 §7.2 for why a
> fourth `ToolPort` adapter was rejected in favor of this shape.

### 2.9 Observability

| Layer | Decision |
|---|---|
| Instrumentation | **OpenTelemetry only** — the application never imports a vendor SDK, so any backend is swappable |
| Collector | `opentelemetry-collector-contrib` (pin exactly; releases weekly) |
| LLM traces / evals / prompt registry | **Langfuse Cloud** (fed via OTLP) |
| Metrics / SLOs | `kube-prometheus-stack` + **Sloth** or **Pyrra** generating SLI/SLO and error-budget rules |
| Logs | `structlog` JSON, PII-redacted, correlated by `request_id` and `trace_id` |

> **⚠ Compliance note.** Langfuse Cloud has no India region. If Indian customers require data residency
> under the DPDP Act, self-host Langfuse in `asia-south1`. Keep GCS and all compute in `asia-south1`
> from the start — it costs nothing now and saves a migration later. **Neon is the one deliberate
> exception** (§2.2, accepted risk) — its residency and latency posture is different from everything
> else in this stack and is tracked separately.

### 2.10 Configuration, prompts, flags

**Zero hardcoding.** No URL, key, model name, port, timeout, TTL, retry count, token budget or price
appears as a literal in application code.

Resolution order, each layer cached in-process (`cachetools` TTL) over Valkey, invalidated by epoch bump
on write:

```
pydantic-settings (bootstrap only — what is needed before the DB is reachable)
  └─ platform config (DB)
       └─ tenant config (DB)
            └─ agent config (DB)
                 └─ per-request override (validated allow-list)
```

Tenants **inherit** from platform config at read time — defaults are never copied at signup, so one row
fixes a bad default across every tenant.

**Prompts:** git is the source of truth → CI syncs to the Langfuse registry → checksum + drift alarm
reconciles the two. Immutable versions, label-based promotion (`dev` / `staging` / `prod`), instant
rollback, and the prompt version id stamped onto every trace span. **No prompt string is ever written
inside a `.py` file.**

**Flags:** **OpenFeature SDK** with a provider backed by our own config DB — per-tenant/workspace/agent
targeting is a join, not a vendor feature, and it satisfies the config-from-DB mandate with no extra
stateful service. Flagsmith drops in through the same interface if a standalone ops UI is ever wanted.

### 2.11 Platform

**GKE Autopilot**, regional, `regular` channel, ≥1.35. One Helm chart serves local `kind` and production.
`docker compose up` for local development.

**Ingress: the Google-managed GKE Gateway controller (Gateway API) + Cloud Armor** — not self-hosted
Envoy Gateway, and used from wave 1, not deferred. Verified: Google's Application Load Balancer does
**not** buffer SSE/chunked responses — the well-known "GCP buffers streaming" reports are a Cloud
Run/App Engine behaviour, not the GFE/ALB data path this uses. WebSocket needs zero extra config and
only terminates at a hard 24-hour cap. The real failure mode in every reported incident is the
**default 30-second backend timeout**, raised via `GCPBackendPolicy.spec.default.timeoutSec` — **not**
`HTTPRoute.spec.rules[].timeouts`, which the GKE controller rejects outright. Cloud Armor (WAF, per-IP/
header/cookie rate limiting, geo-blocking, Adaptive Protection) attaches through the same
`GCPBackendPolicy`, entirely at Google's edge, with zero pods to run or patch. Envoy Gateway was
evaluated and dropped: it would sit behind a passthrough Network Load Balancer where Cloud Armor's L7
policies are unavailable, so it buys nothing here and adds an operated system for a solo engineer.

Rolled out in waves — see §7. The full target state is GKE Autopilot + GKE Gateway/Cloud Armor
(wave 1) + Argo CD 3.4 + Argo Rollouts (canary) + KEDA 2.20 (agent workers scale on queue depth, not
CPU) + cert-manager 1.21 (confirm against Google-managed certs before adding — may be redundant) +
External Secrets Operator 2.8 + Kyverno + OpenTofu for the cluster itself.

> `kubernetes/ingress-nginx` was **retired in March 2026** with no further security patches. It was
> never a candidate — GKE Gateway is the ingress from day one.

Images: multi-stage build, `python:3.13-slim-trixie` builder with a pinned `ghcr.io/astral-sh/uv` binary
stage, distroless runtime, `cosign` signing, `syft` SBOM, `trivy` scanning.

### 2.12 Rate limiting

GKE Gateway + Cloud Armor handles TLS, WAF, coarse per-IP limits and request-id injection at Google's
edge. **All business rate limiting is in-app over Valkey** (token bucket), because the gateway cannot
see tenant, workspace, agent, model or endpoint. Limits are DB config rows, per dimension, with a
documented `429` + `Retry-After` contract.

---

## 3. API design rules

- REST, resource-oriented, `/v1` URL versioning.
- HTTP status codes per RFC 9110. `422` for validation, `429` with `Retry-After`, `409` for idempotency
  conflicts, `503` with `Retry-After` for degraded providers. Never `200` with an error body.
- **Every mutating endpoint accepts `Idempotency-Key`.**
- Errors follow **RFC 9457 Problem Details**, always carrying `trace_id`.
- Cursor pagination everywhere. Offset pagination is banned.
- OpenAPI 3.1 generated from the code, published through a **Scalar** portal, SDKs generated in CI.
- **An undocumented endpoint fails CI** (schema diff gate).

---

## 4. Database rules

- `SELECT *` is banned — columns are always explicit.
- Every foreign key is declared; every query path has a supporting index; every list endpoint paginates.
- Multi-statement writes run in an explicit transaction.
- Every table carries `tenant_id`, `created_at`, `updated_at`, and an RLS policy. **Elaborated, not
  relaxed, by [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §5.1** for one case: a
  platform-curated `scenarios` library row still carries a real, non-null `tenant_id` — pointing at one
  reserved platform tenant (`tenants.is_platform`) rather than `NULL` — so this rule has no exception
  anywhere in the schema, only one documented RLS `OR`-clause for that one table.
- Migrations are Alembic, linted by **squawk** in CI (locking DDL is blocked), following expand-contract.
  `pgroll` for the genuinely hard ones.
- High-volume tables (`usage_events`, `conversation_messages`, `run_events`) are partitioned by month via
  `pg_partman`.
- Data lifecycle: 90-day hot retention by default, per-tenant override in config, then archive to GCS
  Parquet and drop the partition.
- GDPR / DPDP hard-delete is a **Temporal workflow** cascading ~~Postgres (rows + vectors, same
  database)~~ **Postgres (rows) → Qdrant (vectors, matched by point ID — see
  [ADR-001](ADR-001-qdrant-vector-store.md) §3.3)** → GCS → Langfuse, which emits a signed deletion
  receipt. The receipt must confirm both stores, not just Postgres.

---

## 5. Usage & cost contract

Billing lives outside this service. **Reporting does not.**

Every response — and the final `usage` event of every stream — carries:

```jsonc
{
  "usage": {
    "input_tokens": 0, "output_tokens": 0, "cached_tokens": 0,
    "embedding_tokens": 0, "rerank_units": 0,
    "model": "…",                 // resolved from DB config
    "cost_usd": "0.000000",       // computed from the DB pricing table, never a code constant
    "cost_breakdown": [ { "component": "llm|embedding|rerank|parse", "model": "…", "cost_usd": "…" } ],
    "latency_ms": { "total": 0, "model": 0, "retrieval": 0, "tools": 0, "guardrails": 0 }
  }
}
```

Reported on BYOK traffic too, so tenants can reconcile against their own provider invoices. The same
record is written to the partitioned `usage_events` ledger. Quota and hard-cutoff enforcement happens
here (the request is here); invoicing does not.

---

## 6. Quality gates

**Testing matrix** — every layer has a designated tool, all wired into `pre-commit` and one GitHub
Actions matrix:

| Layer | Tool |
|---|---|
| Unit / integration | `pytest` + `pytest-asyncio` + `testcontainers` + `polyfactory` + `respx` + **`pytest-socket`** (network blocked in the unit suite — required gate) |
| Model test doubles | `langchain_core.language_models.fake_chat_models` (`GenericFakeChatModel`, `FakeListChatModel`) + recorded fixtures. Hand-rolled harness in `tests/doubles/` — LangGraph ships no equivalent, so this is a first-class deliverable, not test scaffolding |
| Graph behaviour | Deterministic graph tests: fixed fake model + asserted node visit order, state transitions, and checkpoint/resume across a simulated crash |
| API contract / fuzz | **Deferred.** Schemathesis was the pick, but every version has a hard dependency on `starlette-testclient`, which tops out at 0.4.1 and does not support Starlette ≥1.0 — since this project resolves one shared Starlette version across dev and production, keeping it would have pinned production onto a Starlette carrying real CVEs (PYSEC-2026-161/248/249/2280/2281 — Host header validation bypass, path reconstruction, unbounded form-parsing DoS). Removed 2026-08-01 (verified by hand via `pip-audit`); re-add once `starlette-testclient` supports Starlette 1.x, or replace with an alternative (e.g. Dredd, Prism) that doesn't carry this constraint. |
| Prompt + RAG regression | **promptfoo** for prompt regression and red-team, **Ragas** for RAG metrics (faithfulness, answer relevance, context precision/recall), Langfuse datasets as the trace/dataset store |
| Prompt-injection red team | **promptfoo red-team** + `garak` — ignore-instructions, reveal-prompt, leak-memory, tool-misuse, role-override |
| Load | **k6** at 1 / 100 / 1k VUs in CI; 10k as a quarterly manual run |
| Security | Semgrep, Bandit, `pip-audit`, Trivy, gitleaks, ZAP baseline |
| Chaos | Chaos Mesh (network / pod / HTTP faults; Autopilot restricts kernel-level ones) |

**Coding standards**, enforced by tooling not by review: `ruff` (format + lint + `C901`/`PLR` complexity
and length caps), `mypy --strict`, mandatory type hints, docstrings on every public symbol, `jscpd`
duplication gate, import-layering rules that make `service → repository` legal and `repository → router`
a build failure.

**SLOs:** 99.5% availability (3.6 h/month error budget). p95 time-to-first-token < 800 ms without RAG,
< 1.5 s with RAG + rerank. p95 simple turn < 4 s. Agent run timeout 5 min soft / 15 min hard, both DB
config. 99.9% is revisited when revenue justifies multi-zone everything.

**Git:** trunk-based, short-lived branches, PR + CI gate on every change (the gate is the point, not the
reviewer). Conventional Commits enforced by `commitlint`/Commitizen in `pre-commit`. `release-please`
for versioning and changelog. `gh-stack` for anything over ~3 commits. Signed commits, CODEOWNERS,
required checks, branch protection on `master`.

**Code review checklist** (applies to self-review): right problem · readable · tested · secure ·
maintainable · no duplication · comprehensible in six months.

---

## 7. Delivery plan

Sole engineer. Sequencing *is* the architecture.

**Wave 1 — 6 systems** (see [ADR-001](ADR-001-qdrant-vector-store.md), which revises this from the
original 5). FastAPI + Postgres (Neon) + **Qdrant (self-hosted, GKE Autopilot, `asia-south1`)** + Valkey
(Memorystore) + Temporal Cloud + Langfuse Cloud, fronted by the Google-managed GKE Gateway + Cloud Armor.
Deployed by `helm upgrade` from GitHub Actions onto GKE Autopilot. `docker compose up` locally.
~~Dropping Qdrant in favour of pgvector-in-Postgres removed a whole stateful system from the day-one
operational surface.~~ Superseded — Qdrant is back as a second self-operated stateful system; ADR-001 §5
tracks the added operational cost explicitly rather than treating it as free.

**v0.1 is a production-grade vertical slice**, not a prototype: one tenant → one agent → one knowledge
base → one MCP tool → streaming chat endpoint, with RLS, DI, config-from-DB, OTel, guardrails, Temporal,
tests and migrations all real. A vertical slice surfaces every integration risk while the codebase is
still small enough to fix cheaply; building the whole CRUD plane first means discovering the runtime's
actual needs after the schema has set.

**Wave 2 (when traffic justifies):** Argo CD, KEDA, Argo Rollouts canary, Prometheus/Grafana/Sloth SLO
rules, Kyverno.

**Wave 3:** chaos testing, multi-zone for 99.9%, agents-as-MCP-server, BigQuery analytics export.
(Envoy Gateway is not in any wave — GKE Gateway does that job from wave 1.)

Every wave-2/3 component already has its port or interface in wave 1, so none of them is a rewrite.

> **Additions, [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §10.** Agent authoring,
> deployment/publish/schedule modeling, session-scoring config, and two later-wave transport ports add new
> items across all three waves — full table in ADR-003 §10. Nothing above is reordered or removed.

---

## 8. Repository layout

Single repository. Modules are **domain-oriented, never technology-oriented** — there is no
`controllers/` or `services/` top-level directory.

```
src/agentic/
  core/          # config, DI wiring, errors, logging, tracing, security primitives
  platform/      # ports: LLMGateway, EmbeddingPort, VectorStore (Qdrant-backed, see ADR-001), DocumentParser,
                 #        TaskQueue, Cache, Crawler, PromptResolver, StructuredOutputPort,
                 #        AuthorizationService
  runtime/       # THE ONLY place langgraph / langchain_core may be imported.
                 #   graphs/ nodes/ state/ checkpoints/ streaming/ context.py
  agents/        # router · schemas · service · repository · models
  knowledge/
  tools/
  memory/
  runs/
  variables/
  analytics/
  guardrails/
  prompts/
migrations/      # Alembic, squawk-linted
tests/           # unit · integration · contract · load · redteam
deploy/          # Dockerfile, compose, Helm chart
docs/            # architecture ADRs, runbooks, API guides
templates/       # Copier scaffold — every new module generated identically
```

`uv` workspace. The Copier template is not a nicety: with one engineer and AI-assisted code generation,
a scaffold is what keeps forty modules structurally identical.

> **Additions, [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md) §8.** Three new sibling
> modules: `deployments/`, `scenarios/` (deliberately **not** `templates/` — that name is taken by the
> Copier scaffold directory above), `session_scoring/`. No existing module is renamed or removed.

---

## 9. Rejected, with reasons

| Rejected | Reason |
|---|---|
| Picking a telephony/meeting-bot vendor inside this ADR (or inside [ADR-003](ADR-003-agent-authoring-orchestration-deployment.md)) | New vendor/infra decisions get the same comparison rigor this document and ADR-001 already used — a `VoiceTelephonyPort`/`MeetingBotPort` are named, not designed, in ADR-003 §6.2/§6.3; each is deferred to its own dedicated future ADR |
| Node.js / TypeScript | Thin guardrail, PII, chunking and RAG-eval ecosystem — we would hand-roll the hardest parts |
| Go | Effectively no agent ecosystem |
| `langchain` meta-package / chain abstractions | Constructs prompts inside library code — breaks "no prompts in Python" and makes prompt versioning and rollback impossible for anything the library owns. `langchain-core` is permitted solely as LangGraph's message/model substrate. |
| `langgraph-api` / LangGraph Platform | Elastic License 2.0 in a product other companies pay to use, and it duplicates the HTTP/auth/persistence/config plane we are building anyway |
| LangSmith | Duplicates Langfuse and would put a second vendor in the observability path; we are OTel-only |
| Pydantic AI | Strong on this project's rules as framework primitives, but v2.0 GA is ~5 weeks old with 21 minor releases in that window. Maturity and production evidence were judged more valuable than framework-enforced discipline; the discipline is enforced by lint and CI instead (§2.1.1). Reconsider at the next major review if LangGraph's boundary rules prove costly. |
| Trigger.dev | TypeScript-first (Python = subprocess shelling); self-host loses checkpoints; per-second billing on idle-waiting runs |
| Inngest | SSPL licence; Python SDK 0.5.x; inverted network topology |
| Restate | BSL 1.1; least-exercised SDK is Python |
| Supabase | We would use ~30% of it and inherit its auth/edge opinions |
| Keycloak / Ory / Auth0 / Clerk | No human login surface exists to justify an IdP |
| Mem0 / Zep / Letta | Memory is the product's differentiator — buying it means renting the differentiator |
| NeMo / Guardrails AI / LLM Guard | Assume file-based policy, single tenant, and framework-owned LLM loop |
| Railway / Vercel / Render | Proprietary deployment models — "local == prod" would become a lie |
| `ingress-nginx` | Retired March 2026, unpatched |
| MongoDB | No transactions, FKs, constraints or joins — contradicts the DB rules above |
| Redis (RSALv2/SSPL) | Licence review cost exceeds the benefit when Valkey is a drop-in |
| ~~Qdrant~~ | ~~The §2.2.1 pgvector design (halfvec + binary quantization + tenant-partitioned HNSW) meets the 10M-chunk target inside one Postgres — no second stateful system to back up, monitor and reconcile~~ **Superseded — see [ADR-001](ADR-001-qdrant-vector-store.md), which reverses this and rejects pgvector-in-Postgres instead (§6 of that document).** |
| Cloud SQL / AlloyDB (as the production host) | Evaluated and would be the residency/latency-correct pick; the team explicitly chose to keep Neon in production and accept the risk documented in §2.2 |
| Llama Prompt Guard 2 | Ships under Meta's `llama4` community licence, not MIT — disqualified by the same permissive-licence-only rule as every other Llama-derived model in this document |
| Google Cloud Model Armor (as primary injection guardrail) | Zero Hindi/Devanagari coverage in any region, plus a silent fail-open (`EXECUTION_SKIPPED`) past 10,000 tokens |
| Envoy Gateway | Sits behind a passthrough NLB where Cloud Armor's L7 policies are unavailable; GKE's managed Gateway controller gives the same Gateway API surface with zero pods to operate |
| Translate-then-scan (as a primary injection defense) | Normalizes away the exact attack surface — homoglyphs, zero-width characters, emoji smuggling, phonetic perturbation — so the detector sees clean text while the LLM sees the original poison |
| Offset pagination, `SELECT *`, prompts in code, free-text parsing | Banned by rule, not by preference |

---

## 10. Top risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Solo-engineer operational load** — the target stack is platform-team-sized | Wave 1 is 5 systems, all but one (Postgres itself) managed. Waves 2-3 deferred behind existing ports. |
| 2 | **Neon in production is cross-region from GKE** (AWS Singapore vs GCP Mumbai) — public internet, no PSC, ~50-60 ms/hop unverified on our path, plus a DPDP residency question | Load-test the real p95 impact before committing; verify the Neon pooler against `SET LOCAL app.tenant_id`; legal sign-off on DPDP; dedicated single-tenant deployment is the escalation path for a regulated customer, not a config flag. See §2.2. |
| 3 | ~~**pgvector at 10M only works in the exact §2.2.1 shape**...~~ **Superseded by [ADR-001](ADR-001-qdrant-vector-store.md) §5, risks 3/3b/3c** (dual-write consistency, second self-operated stateful system, unverified vendor performance numbers) | See ADR-001 §5 |
| 4 | Temporal determinism sandbox misuse causes production non-determinism errors | Every I/O in an Activity; `workflow.patched()` discipline; replay tests in CI |
| 4b | **Orchestration bleeding into the domain layer** — LangGraph's main failure mode | `import-linter` contract blocks `langgraph`/`langchain_core` outside `runtime/`; nodes must be thin; reviewed on every PR |
| 4c | **Test-double harness is ours to build and maintain** — no `TestModel` equivalent | Scoped as a wave-1 deliverable in `tests/doubles/`, not deferred; `pytest-socket` gate prevents silent live calls |
| 5 | Prompt injection via Hinglish/Latin-script text, Unicode obfuscation (emoji smuggling), or indirect injection through retrieved chunks, tool output, or MCP tool descriptions | Mandatory Unicode normalization before every detector; scan Hinglish through the same pipeline as Devanagari, not a separate "English" policy; scan chunks/tool output/tool descriptions, not just user input; pin tool schema hashes |
| 6 | Indian PII leaking into logs (Presidio is English-centric) | Custom Aadhaar/PAN/mobile/IFSC/UPI recognizers before the first log line ships |
| 7 | DPDP data residency vs Langfuse Cloud (no India region) | Everything else in `asia-south1`; self-host Langfuse if residency is demanded |
| 8 | Re-embedding cost if the embedding model changes at 10M chunks | Model id in config, backfill job written up front, dimension fixed at 1536 |
| 9 | Cost blowout from LLM-generated chunk context | Deterministic prefix is the default; LLM context is opt-in with a cost estimate shown first |
| 10 | FastAPI 0.x breaking changes in a minor bump | Exact pin, Renovate PR, contract tests gate the upgrade |
| 11 | **Cross-cloud guardrail dependency** — AWS Bedrock Guardrails called from a GCP-hosted service adds a third cloud vendor to the critical guardrail path, with its own latency, outage mode and IAM/billing relationship | Own circuit breaker on the call; Qwen3Guard (self-hosted, GKE) becomes primary automatically if AWS Bedrock Guardrails is unreachable — never a single point of failure |
| 12 | **No vendor publishes a verified per-language Hindi prompt-injection benchmark** — "Optimized and supported" is a vendor claim, not a measured guarantee | Build our own Hindi + Hinglish + Unicode-obfuscated red-team set inside the promptfoo/garak regression suite (§6) before trusting any guardrail in production |

---

## 11. Amendment rule

Any change to this document is a new ADR (`ADR-00N`) that states what it supersedes and why. The stack
is not amended in passing by a pull request.
