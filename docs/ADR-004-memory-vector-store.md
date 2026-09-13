# ADR-004 — Long-term memory recall: a second Qdrant collection, gated by `long_term_enabled`

**Status:** Accepted · **Date:** 2026-08-23 · **Decider:** Lakshya Tyagi (sole engineer)
**Supersedes:** ADR-001 §1's "only the `VectorStorePort` adapter and the knowledge-base ingestion path
are affected" claim (a second port, `MemoryVectorStorePort`, and a second ingestion-shaped write path —
`memory/service.py::MemoryService._index_for_search` — now also reach the same Qdrant deployment); closes
the "not a decision, nothing here is an ADR" candidate design note `docs/api-and-runtime-inventory.md`
§1.3.7 has carried since 2026-08-09 for the "Hybrid fallback retrieval" technique, to the extent this ADR
actually builds (see §6 for what it does not). Does not touch ADR-000, ADR-002 or ADR-003.
**Review trigger:** the still-⬜ long-term auto-extraction pipeline (§6) landing and needing its own write
path into this collection; or ADR-001's own load-test review trigger firing and finding the second
collection changes that verdict.

---

## 1. Decision

`POST /v1/agents/{id}/memories:search` (`docs/api-and-runtime-inventory.md` §1.3.7) gets a real hybrid
retrieval pipeline — dense (Qdrant) + lexical (Postgres `tsvector`), RRF-fused, optional rerank, the
identical ADR-000 §2.4 shape `POST /v1/knowledge-bases/{id}/search` already has. The dense half needs
vectors to search, and nothing wrote any for `memories` before this ADR — `POST /v1/agents/{id}/memories`
was, and for the default case remains, a pure Postgres write (`memory/README.md`'s own "no port behind it"
claim). This ADR is what makes the dense channel real without changing that default:

- A **second Qdrant collection**, `agent_memories` (`Settings.qdrant_memories_collection`), on the
  **same self-hosted Qdrant deployment** ADR-001 already stood up — not a second cluster, not a second
  vendor. `memory/README.md`'s own candidate design notes named exactly this "buildable today against the
  existing Qdrant deployment ... no new infrastructure" before it was decided; this ADR is that decision.
- A **second port**, `MemoryVectorStorePort` (`platform/ports.py`), rather than widening `VectorStorePort`
  to cover both resources — see §3 for why.
- Indexing a memory into that collection is **opt-in per agent**, gated on `agent_memory_config.
  long_term_enabled` (existing column, migration `20260804_0004` — recorded long before this ADR, never
  wired to any runtime behaviour until now). `POST /v1/agents/{id}/memories` embeds and upserts the new
  memory's content only when the calling agent has that flag set; the default (`false`) path is
  byte-for-byte what it was before this ADR — no port touched, no behaviour change, every existing test
  in `tests/integration/test_agent_memories.py` unaffected.
- The lexical half needs no new infrastructure at all: `ix_memories_content_tsv` (migration
  `20260823_0001`), the identical GIN-expression-index technique `ix_chunks_content_tsv` (migration
  `20260812_0001`) already established, applied to `memories.content`.

## 2. Why gate indexing on `long_term_enabled` rather than index unconditionally

Three options were on the table for the *write* side of this ADR (the *read* side — building the search
endpoint at all — was never in question, since it is the endpoint this ADR exists to serve):

1. **Unconditionally embed and upsert on every `POST .../memories`.** Rejected: this would turn
   `POST /v1/agents/{id}/memories` — documented, tested and shipped as "a plain Postgres CRUD surface with
   no port behind it" (`memory/README.md`) — into a route that 503s in any deployment without
   `EmbeddingPort`/`MemoryVectorStorePort` configured, silently regressing every existing caller and every
   existing integration test the moment this ADR landed. ADR-000 §2.10's own "no silent behaviour change"
   posture rules this out on its own.
2. **Embed and upsert best-effort, swallowing `UpstreamUnavailableError`.** Rejected: this is the one
   pattern this codebase's own Null-adapter convention (`platform/adapters.py`'s module docstring) exists
   specifically to prevent — `NullReranker`/`NullMemoryVectorStore` both document, in so many words, that a
   port call which silently degrades instead of failing loud is "the same misrepresentation" as a reranker
   that fakes an identity pass-through. A memory recorded but silently never indexed is exactly that: a
   `201` that lied about what actually happened.
3. **Gate on `agent_memory_config.long_term_enabled` (chosen).** The column already existed, already
   defaulted to `false`, and `memory/README.md`'s own opening line already described it as "long-term
   auto-extraction is opt-in per agent (off by default)" — a sentence that, before this ADR, gated nothing
   at runtime (`docs/api-and-runtime-inventory.md` §1.3.7: "it does not yet gate or drive any runtime
   behaviour, since none of that runtime exists yet"). Reusing it for "should this agent's manually-written
   memories also be indexed for search" is not a repurposing so much as the first real implementation of
   what the setting has always claimed to control. An agent that opts in and calls this route against an
   unconfigured deployment gets the honest 503 option (2) above tried to avoid giving — the same "you asked
   for a capability that isn't configured" contract every other opt-in write in this codebase already
   holds callers to.

## 3. Why a second port and a second collection, not a second dimension on `VectorStorePort`

`VectorStorePort.upsert_chunks`/`.search` are keyed by `knowledge_base_id` — a memory has no
knowledge-base-shaped parent (it belongs to an agent, optionally further scoped by a caller-supplied
`participant_id`, ADR-003 §6.1). Two ways to reach a memory-shaped retrieval call existed:

- Rename/repurpose `VectorStorePort`'s `knowledge_base_id` parameter into something generic. Rejected:
  every existing caller (`SearchService`, `IngestionPipelineService`, `CrawlPipelineService`) already reads
  that parameter name as exactly what it says; renaming it to lie less to a future memory caller would lie
  more to three already-shipped, well-tested callers today.
- A second, resource-shaped port (`MemoryVectorStorePort`, chosen). The identical "two different intake
  shapes get their own port rather than a shared method with a confusing name" reasoning `IngestionQueuePort`/
  `CrawlQueuePort` already draw one hierarchy level up (`platform/ports.py::IngestionQueuePort`'s own
  docstring) — a real signature difference (`agent_id`/`participant_id` vs. `knowledge_base_id`), not a
  coincidental one.

Two Qdrant *collections* rather than one collection partitioned by both `knowledge_base_id` and `agent_id`
follows the same reasoning one level down: `chunks` and `memories` are retrieved by genuinely different
scoping keys, ADR-001 §3.1's per-tenant HNSW sub-graph (`payload_m`) is keyed on `tenant_id` either way (so
nothing about multitenancy is weakened by the split), and a shared collection would need a discriminator
column on every point plus two sets of index-creation logic anyway — no simpler than two collections, and
one collection each is what keeps `platform/vector_store.py::QdrantVectorStore`'s own already-shipped
`_ensure_collection` untouched rather than risking it on a schema migration for a collection already
carrying live chunk vectors.

## 4. Production design

- **Collection**: `agent_memories` (`Settings.qdrant_memories_collection`), created lazily and idempotently
  on first real use — the identical `_ensure_collection` shape `QdrantVectorStore` already has, applied by
  its own sibling class `QdrantMemoryVectorStore` (`platform/vector_store.py`).
- **Vector**: one named dense vector (`"dense"`, the same name `QdrantVectorStore` uses on its own
  collection — no collision, since they are different collections), sized from the same
  `Settings.qdrant_vector_dimension` every other Qdrant collection in this codebase shares (ADR-001's own
  "one fixed vector size per collection" gap, unchanged, applied to a second collection rather than solved
  differently for it). Binary quantization, `always_ram=True`, rescoring + 2x oversampling always on for
  `search` — identical to `QdrantVectorStore`, ADR-001 §3.2's reasoning carried over unchanged.
- **Payload / multitenancy**: `tenant_id` (keyword, `is_tenant=True` — the same per-tenant HNSW sub-graph
  co-location ADR-001 §3.1 established), `agent_id` (keyword, plain index — this collection's
  `knowledge_base_id`-equivalent sub-partition key) and `participant_id` (stored when a caller sets one,
  ADR-003 §6.1, not payload-indexed — the identical "no index for a field nothing filters at collection
  scale yet" gap `QdrantVectorStore`'s own `document_id` payload field already carries).
- **Point id**: the memory's own `memories.id`, verbatim — ADR-001 §3.3's "point IDs are shared UUIDs with
  the row they represent" convention, applied to `memories` instead of `chunks`.
- **Embedding model**: the platform's one configured `EmbeddingPort` (`Settings.vertex_ai_embedding_model`,
  ADR-000 §2.4) — no memory-specific model choice, since there is no per-resource "declared embedding
  config" row for memories the way `knowledge_bases.embedding_model_id` is for chunks.
- **Write path**: `MemoryService._index_for_search`, called synchronously from `POST /v1/agents/{id}
  /memories` when (and only when) `long_term_enabled` is set (§2) — not a Temporal workflow, unlike
  `knowledge`'s own ingestion pipeline. A single memory is one short string with no parse/chunk step; the
  entire justification for `knowledge`'s workflow (`ADR-000 §2.5`: "across runs: KB ingestion ... owned by
  Temporal" — durability across a parse → chunk → embed → upsert pipeline that can take real wall-clock
  time) does not apply to embedding one already-atomic piece of text. If a future extraction pipeline (§6)
  starts writing many memories per run, revisit this against the identical `IngestionQueuePort`-shaped
  trigger `knowledge` uses — not solved speculatively here.
- **Degrade path**: `NullMemoryVectorStore` (`platform/adapters.py`), wired by `core/di.py::
  memory_vector_store` whenever `Settings.qdrant_url` is unset — the identical `UpstreamUnavailableError`
  (503 + `Retry-After`) shape every other Null adapter in this codebase gives, reached only by an agent
  that opted in (§2).
- **Read path**: `MemorySearchService` (`memory/service.py`) — `EmbeddingPort.embed_query` → `Memory
  VectorStorePort.search` (dense) and `MemoryRepository.search_lexical` (lexical, `ix_memories_content_tsv`,
  migration `20260823_0001`) → `core/retrieval.py::reciprocal_rank_fusion` (shared with `knowledge/
  service.py::SearchService`, factored out the day this ADR needed the identical arithmetic over a
  different id type) → `MemoryRepository.list_for_search` (hydration/isolation boundary, the identical
  "never trust a second datastore's claim" role `ChunkRepository.list_for_search` already plays) →
  optional `RerankerPort.rerank`. `{"mode": "lexical", "rerank": false}` is fully live in every deployment
  today, the identical reason `SearchService`'s own docstring gives for the same default-mode choice —
  `hybrid`/`dense` degrade as a clean 503 until both `EmbeddingPort` and `MemoryVectorStorePort` are
  configured.

## 5. What changes elsewhere

| Location | Original | Now |
|---|---|---|
| ADR-001 §1 | "only the `VectorStorePort` adapter and the knowledge-base ingestion path are affected" | A second port (`MemoryVectorStorePort`) and a second, non-Temporal write path now also reach the same Qdrant deployment — see §3/§4 above. Text left in place per ADR-000 §11 as the historical record of what ADR-001 itself changed; this row is the correction |
| `docs/api-and-runtime-inventory.md` §1.3.7 | `⬜ POST /v1/agents/{id}/memories:search` — "Qdrant recall + Postgres metadata. Candidate hybrid-retrieval shape below" | ✅/🟡 — see that file's own updated row for the exact split (lexical fully real; dense/rerank config-gated) |
| `memory/README.md` | "Still empty: `POST /v1/agents/{id}/memories:search` ... along with the summarisation worker and the long-term auto-extraction pipeline itself" | The search endpoint moves out of "still empty"; the summarisation worker and the L0-L3 auto-extraction pipeline stay ⬜ — see §6 below for why this ADR does not attempt either |

## 6. What this ADR deliberately does not build

`memory/README.md`'s own candidate design notes (2026-08-09) named three techniques worth carrying into
this module: layered extraction tiers (L0-L3), hybrid fallback retrieval, and offload/compact. This ADR
builds the retrieval *mechanism* the second technique needs (hybrid search over whatever is in `memories`
today) — it does **not** build:

- **The L0-L3 layered extraction pipeline**, or any automated writer of `memories` rows at all. That
  pipeline reads a run's conversation and decides what is worth remembering — an LLM-structured-output
  call driven by the runtime plane, which is still entirely ⬜ (`docs/api-and-runtime-inventory.md`'s own
  headline: "the runtime plane (LangGraph) ... at zero"). Building it is a multi-module undertaking with no
  runtime to hang it off yet, not a follow-up to a single retrieval endpoint.
- **The summarisation worker.** Same dependency — nothing runs a conversation turn yet for it to
  summarise.
- **Per-memory deletion from Qdrant on soft-delete.** `MemoryService.delete` still only flips
  `memories.status` to `deleted` in Postgres; the Qdrant point survives. This is not an oversight — it is
  the identical, already-accepted gap `knowledge/service.py::DocumentService.delete`'s own docstring
  documents for `chunks`' Qdrant points ("genuinely destroying is the GDPR/DPDP Temporal cascade, not this
  verb"), and it is harmless here for the same structural reason: `MemorySearchService`'s hydration step
  (`MemoryRepository.list_for_search`) only resolves `status = 'active'` rows, so a stale point simply
  becomes an `unresolved_dense_candidates` count on the next search, exactly the honest drift signal
  ADR-001 §5 risk 3 already names and accepts for the chunks collection.
- **A reconciliation workflow between Postgres and this new collection.** ADR-001 §5 risk 3's own
  mitigation ("scheduled Temporal reconciliation workflow") was never built for `chunks` either
  (`docs/api-and-runtime-inventory.md` §4.2's own still-⬜ "Outbox drain activity" row) — this ADR does not
  add a second copy of a gap the codebase already carries and has not yet closed for the first collection.

## 7. Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | **A third Qdrant collection someday (per ADR-001 §3.1's own "one fixed vector size" gap) would need a genuinely different embedding dimension** — this ADR reuses `Settings.qdrant_vector_dimension` unconditionally, the same simplification ADR-001 already accepted for `chunks` | Named per-dimension vectors (ADR-001 §3.1's own stated follow-up), the day a second dimension is actually needed — not before |
| 2 | **`long_term_enabled` becomes a footgun if a tenant flips it on expecting automatic extraction** — the setting's own name and `memory/README.md`'s historical wording both say "auto-extraction," but this ADR only wires it to *manual* `POST .../memories` calls, not to anything that reads a run's conversation | `docs/api-and-runtime-inventory.md`/`memory/README.md` both state plainly, in this pass, that auto-extraction itself is still ⬜ (§6) — the gap is documented, not hidden, until the extraction pipeline (§6) actually exists to close it |
| 3 | **Two Qdrant collections on one self-hosted deployment double the blast radius of ADR-001's own risk #3b (second self-operated stateful system, solo-engineer operational load)** — not a second *system*, but a second thing that can independently fail its own collection-creation/index calls | Reuses the identical Helm/GKE Autopilot deployment and `replication_factor` config ADR-001 §3.4 already established — no new operational surface, only a second collection inside the one already-operated cluster |

## 8. Rejected, with reasons

| Rejected | Reason |
|---|---|
| A shared `VectorStorePort`/collection for both chunks and memories | §3 — no natural shared scoping key, and a shared collection needs a discriminator column plus two index-creation code paths anyway, no simpler than two collections |
| Unconditional embed-on-write for every memory | §2 option 1 — regresses an already-shipped, documented, tested "no port behind it" endpoint |
| Best-effort/silent-degrade indexing | §2 option 2 — the exact misrepresentation this codebase's Null-adapter convention exists to prevent |
| Building the L0-L3 extraction pipeline now, since this ADR was already touching `memory/` | §6 — no runtime plane exists yet to drive it; out of scope for a retrieval-endpoint ADR |

## 9. Follow-ups this ADR intentionally defers

- Per-memory Qdrant point deletion on soft-delete (§6) — worth doing the day the accepted-drift cost (§6,
  risk-3-shaped) is measured to matter, mirroring whatever the eventual `chunks` fix turns out to be rather
  than solving it differently for this collection first.
- A reconciliation workflow between Postgres and either Qdrant collection (§6) — genuinely shared follow-up
  work with `knowledge`'s own still-⬜ outbox-drain activity, not memory-specific.
- The L0-L3 extraction pipeline and summarisation worker themselves (§6) — blocked on the runtime plane,
  tracked in `memory/README.md`.
