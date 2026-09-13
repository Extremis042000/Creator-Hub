# ADR-001 — Vector store: Qdrant replaces pgvector-in-Postgres

**Status:** Accepted · **Date:** 2026-08-02 · **Decider:** Lakshya Tyagi (sole engineer)
**Supersedes:** ADR-000 §2.2 (vector store row), §2.2.1 (pgvector production design), §2.4 (dense-search
row + the deferred sparse-vector plan), §4 (GDPR/DPDP hard-delete cascade), §7 (Wave 1 system list),
§8 (repo layout annotation), §9 (the "Qdrant" rejected-alternatives entry), §10 (risk #3)
**Review trigger:** first production load test, or a confirmed Qdrant Cloud India/APAC region landing

---

## 1. Decision

Embeddings move out of Postgres and into a **dedicated Qdrant cluster**, self-hosted on GKE Autopilot in
`asia-south1` (§3.4). Postgres/Neon remains the system of record for everything else — tenants,
workspaces, agents, config, RBAC, the usage ledger — unchanged from ADR-000. This is a **narrower**
change than it might sound: only the `VectorStorePort` adapter and the knowledge-base ingestion path are
affected. No other port, and nothing in `runtime/`, changes.

## 2. Why

ADR-000 §2.2 chose pgvector specifically to avoid running a second stateful system, and documented that
reasoning carefully. Two things earn revisiting it:

1. **The filtered-recall failure mode ADR-000 itself flagged as pgvector's central risk (§10 risk #3) is
   structural, not a config mistake.** At the default `hnsw.ef_search`, HNSW returns a fixed candidate
   batch *before* applying a selective tenant filter — a filter selective enough to match single-tenant
   result sizes can return close to zero rows, silently, no error. pgvector 0.8.0 added iterative index
   scans as a backstop for exactly this (`pgvector/pgvector#671`, `#721`, `#980`), and ADR-000's §2.2.1
   design worked around it with an exact, non-optional shape (`tenant_bucket` list partitioning + local
   HNSW per partition). That workaround is real engineering debt: it has to be gotten exactly right and
   re-verified (`EXPLAIN` asserted in CI) on every change to the knowledge-base schema. Qdrant's
   filterable HNSW pushes the payload filter *into* graph traversal instead of applying it after, which
   architecturally avoids this class of bug rather than working around it — sourced from Qdrant's own
   comparison writeup, so treat it as a vendor claim pending our own load test (§3.6), the same posture
   ADR-000 already takes toward every unverified number in this document.
2. **Removing the Neon pgvector version-floor risk entirely.** ADR-000 §2.2.1 flagged a required
   `pgvector >=0.8.4` floor with no confirmed way to check Neon's shipped version ahead of the first
   HNSW-index migration. Moving vectors off Postgres removes that dependency outright — Neon's pgvector
   version stops being something this service's availability depends on.
3. **Native sparse vectors remove a real footgun from the deferred Hindi B2 plan.** ADR-000 §2.4 held
   BGE-M3's sparse output in reserve specifically because pgvector's `sparsevec` HNSW hard-fails above
   1,000 non-zero dimensions — workable at BGE-M3's top-128 output today, but a ceiling to design around
   forever. Qdrant supports named dense *and* sparse vectors on the same point natively (no repurposed
   type, no separate table); BGE-M3's single forward pass giving both dense and sparse output maps onto
   one Qdrant point with two named vectors.

This is not a free change. It reintroduces exactly the dual-write/reconciliation problem ADR-000 §2.2
explicitly chose pgvector to avoid, and it adds a second stateful system to a solo-engineer's Wave 1
(§3.4, §5). Both are accepted, not dismissed — see §5 for how each is mitigated and what's now a
standing risk instead of an eliminated one.

## 3. Production design

### 3.1 Multitenancy

**Single collection, payload-based tenant partitioning** — Qdrant's own documentation recommends this
over one-collection-per-tenant except for a small number of tenants needing hard physical isolation;
many collections increase per-collection resource overhead at our scale (10M chunks, growing tenant
count). Concretely:

| Element | Setting | Why |
|---|---|---|
| Tenant field | `tenant_id` keyword payload field, indexed with **`is_tenant=true`** (Qdrant ≥1.11) | Tells storage to co-locate a tenant's vectors on disk for sequential reads instead of random seeks across the whole collection ("tenant defragmentation") |
| HNSW config | `m=0` (disable the global graph), `payload_m=16` (per-tenant sub-graphs keyed on the `is_tenant` field) | Every tenant gets its own HNSW sub-graph instead of one shared graph the query has to filter after traversing — this is what actually closes the filtered-recall gap in §2 above |
| Sharding | **Custom shard key = `tenant_id`** (mirrors ADR-000's `tenant_bucket` design almost exactly) — a whale tenant gets a dedicated shard, everyone else hashes into a shared pool of shards | Same physical-isolation rationale ADR-000 already argued for pgvector partitioning; Qdrant's custom-shard-key feature is a direct analogue |
| Replication | `replication_factor=3` set explicitly | Qdrant's default is `1` — single-node is non-production by Qdrant's own operational guidance; this is a setting to get right at cluster creation, not a default to trust |

### 3.2 Quantization

**Binary quantization with rescoring enabled** (GA since Qdrant 1.5.0) — same design philosophy as
ADR-000's pgvector binary-quantized HNSW, carried forward: ~32× memory reduction, ~40× search speedup,
documented recall ~0.98 *with* rescoring + oversampling — Qdrant's own numbers drop to ~0.67 without
rescoring, so rescoring is a requirement, not a tunable to skip under load. Scalar quantization (4×, <1%
accuracy loss) and product quantization (up to 64×, slower) remain available as a memory-pressure escape
hatch if binary's recall profile doesn't hold up under our own eval (§3.6). **TurboQuant**, new in
Qdrant 1.18.0, is worth a follow-up eval once this design is live — it claims 1-bit compression
(~32×, matching binary) at recall closer to scalar quantization, which would be a strict improvement if
it holds — but it's too new (May 2026) to commit to sight-unseen here.

### 3.3 Sync with Postgres

Point IDs are shared UUIDs with the `chunks` table row they represent — there is no foreign key across
this boundary (Qdrant has no cross-store referential integrity, same limitation pgvector's replacement
was meant to remove, now back by necessity). Qdrant's own documented pattern, and the one adopted here:

- **Transactional outbox** — the ingestion transaction writes the `chunks` row *and* an outbox event
  row atomically in the same Postgres transaction; an async worker (a Temporal Activity, reusing the
  ingestion workflow's own machinery rather than a new queue) drains the outbox and upserts into Qdrant.
  This is Qdrant's own stated recommended default for the majority of production Postgres+Qdrant
  deployments, not a bespoke design.
- **Periodic reconciliation job** — Postgres is the source of truth; a scheduled Temporal workflow
  diffs `chunks` point IDs against what Qdrant actually holds and re-drives the outbox for any drift.
  This is the piece ADR-000's single-database design didn't need and this design does — track it as a
  real, standing operational cost (§5), not a one-time build item.
- **GDPR/DPDP hard-delete** (ADR-000 §4) now cascades **Postgres (rows) → Qdrant (vectors, matched by
  point ID) → GCS → Langfuse**, not "Postgres (rows + vectors, same database)" as originally written.
  The Temporal workflow must confirm the Qdrant delete before emitting the signed deletion receipt —
  a receipt that doesn't verify both stores is not a compliance guarantee.

### 3.4 Hosting: self-hosted on GKE Autopilot, `asia-south1` — not Qdrant Cloud

Qdrant Cloud (managed SaaS) was evaluated first, consistent with ADR-000's "prefer managed" Wave-1
philosophy (§10 risk #1). It's rejected here for a residency reason specific to this product, not a
maturity or licensing one: **Qdrant Cloud's specific region list is a live console dropdown, not a
published, crawlable set of region codes, and an India/APAC region could not be confirmed one way or the
other during this review.** ADR-000 already carries one deliberate residency exception (Neon, cross-region
to AWS Singapore, §2.2) and is explicit that "Neon is the one deliberate exception... everything else in
this stack [stays] in `asia-south1` from the start" (§2.9). Accepting a second, *unverified* residency
exception for the store that holds retrieved chunk content (frequently the most PII-dense data in the
whole RAG pipeline) is a materially different risk than accepting one verified, load-tested exception —
so it isn't accepted here.

Self-hosting instead:
- Uses the same GKE Autopilot + Helm deployment path already standing up everything else in Wave 1 (no
  new deployment tooling to learn) — the official `qdrant/qdrant-helm` chart, community-supported,
  matches this project's existing "managed control plane, Helm-deployed workload" pattern. Google also
  publishes an official GKE deployment tutorial for Qdrant, independently confirming the pattern is
  well-trodden.
- Keeps the retrieval path **in-region** end to end (GKE Autopilot, Qdrant, Vertex AI embeddings are all
  `asia-south1`) — this is a **latency win**, not just a residency one: the RAG p95 TTFT budget (§6,
  <1.5 s with RAG + rerank) already has to absorb one unavoidable cross-region hop to Neon; adding a
  *second* cross-region hop for every vector search would have made that budget materially harder to
  hit. In-region Qdrant adds zero incremental cross-region latency.
- **Reopens the DPDP legal-sign-off question ADR-000 raised for Neon** — but answers it, rather than
  deferring it, for the specific data that most needs an answer (retrieved chunk text).

**Trade-off accepted, stated plainly:** this makes Qdrant the *second* self-operated stateful system in
Wave 1 (alongside the FastAPI service itself), not zero as pgvector-in-Postgres would have kept it. See
§5.

**Review trigger:** if Qdrant Cloud later publishes a confirmed India/APAC region with acceptable terms,
re-evaluate — self-hosting is the residency-correct default today, not a permanent rejection of managed
Qdrant.

### 3.5 Version floor

**Qdrant `>=1.18.3`** (current stable at time of writing) — pin exactly, the same discipline ADR-000
already applies to pgvector, Temporal, and every other version-sensitive dependency in this stack. Note
for whoever upgrades past this: Qdrant 1.17 changed the gRPC vector response format in a client-breaking
way — check the client SDK pin against the server version on every Qdrant upgrade, not just at initial
adoption.

### 3.6 What's still unverified — do before trusting this design under load

Consistent with ADR-000's own standard (nothing in this document is treated as fact until checked): the
filtered-HNSW claim in §2 and the binary-quantization recall numbers in §3.2 are Qdrant's own published
numbers, not independently reproduced against our actual 10M-chunk, tenant-skewed shape. **Run our own
load test before the first production knowledge-base ingest**, the same gate ADR-000 already required
for the pgvector design it's replacing (§2.2.1's `EXPLAIN`-asserted CI gate). A single vendor-adjacent
benchmark (pgvectorscale vs Qdrant, Tiger Data, May 2025) claims pgvectorscale wins on raw QPS at 50M
vectors — from pgvectorscale's own maker, so treated as non-neutral and not cited as a reason for or
against this decision either way.

## 4. What changes in ADR-000

| ADR-000 location | Original | Now |
|---|---|---|
| §2.2 Data table, "Vector store" row | pgvector, same Postgres, no separate vector database | Superseded — see §1, §3 above |
| §2.2.1 (entire subsection) | pgvector production design (halfvec, binary-quantized HNSW, `tenant_bucket` partitioning) | Superseded — see §3 above. Text left in place as a historical record of the original, fully-specified design; do not implement it |
| §2.4, "Search" row | Hybrid — dense (pgvector) + lexical | Hybrid — dense + sparse (Qdrant, both native) + lexical (Postgres `tsvector`, unchanged), RRF-fused |
| §2.4, Hindi hybrid search box, B2 sparse plan | `sparse sparsevec` column, 1,000-non-zero HNSW ceiling flagged as a footgun | Native Qdrant sparse named vector on the same point as the dense one — no separate column, no ceiling at BGE-M3's top-128 output |
| §4, GDPR/DPDP hard-delete | "cascading Postgres (rows + vectors, same database)" | Cascading Postgres (rows) → Qdrant (vectors) → GCS → Langfuse — see §3.3 |
| §7, Wave 1 system list | "5 systems... Dropping Qdrant in favour of pgvector-in-Postgres removed a whole stateful system" | 6 systems — Qdrant (self-hosted) added back. See §5 for the honest operational-cost accounting |
| §8, repo layout comment | `VectorStore (pgvector-backed)` | `VectorStore (Qdrant-backed)` |
| §9, rejected-alternatives table | "Qdrant | The §2.2.1 pgvector design... meets the 10M-chunk target inside one Postgres — no second stateful system" | Entry removed from "rejected" — see §6 below for what's rejected now instead |
| §10, risk #3 | "pgvector at 10M only works in the exact §2.2.1 shape" | Replaced — see §5 below |

Per ADR-000 §11, the original text in each location is left intact rather than deleted, as the historical
record of what was decided and why at the time; each location gets a short pointer to this document.

## 5. Risk table updates (supersedes ADR-000 §10 risk #3)

| # | Risk | Mitigation |
|---|---|---|
| 3 | **Dual-write consistency between Postgres (chunk metadata, source of truth) and Qdrant (vectors)** — point IDs can drift on partial failure; this is the exact problem ADR-000 chose pgvector-in-Postgres to avoid, now reintroduced | Transactional outbox (§3.3) for the write path; scheduled Temporal reconciliation workflow treating Postgres as source of truth; GDPR hard-delete workflow explicitly confirms the Qdrant-side delete before emitting a receipt |
| 3b | **Second self-operated stateful system in Wave 1** — solo-engineer operational load (ADR-000 §10 risk #1) goes from one self-run system (the app itself) to two (app + Qdrant) | Reuses the existing GKE Autopilot + Helm deployment pattern (§3.4); official `qdrant-helm` chart, not bespoke tooling; `replication_factor=3` and standard Qdrant backup/snapshot tooling from day one, not deferred |
| 3c | **Qdrant's own recall/performance numbers (filtered-HNSW, binary quantization) are vendor-published, not independently verified against our tenant-skewed 10M-chunk shape** | Load test required before first production ingest (§3.6), same gate ADR-000 already mandated for the pgvector design being replaced |

## 6. Rejected, with reasons (supersedes the ADR-000 §9 "Qdrant" entry)

| Rejected | Reason |
|---|---|
| pgvector-in-Postgres (ADR-000's original choice) | Filtered-recall failure mode is structural, not a config mistake (§2); the exact-shape workaround is real, ongoing engineering debt; removing it also removes the Neon pgvector-version-floor risk entirely |
| Qdrant Cloud (managed) | Residency-unconfirmed for India/APAC at time of writing (§3.4) — the one thing this product cannot be casual about for the store holding retrieved chunk text. Revisit if a confirmed region lands |
| Weaviate, Milvus, Pinecone | Not evaluated for this ADR — Qdrant was chosen as pgvector's direct, ADR-000-anticipated replacement (ADR-000 §9 already named it as the alternative considered and rejected at the time); reopening the full vector-database field is out of scope for this amendment. If Qdrant's own risks (§5) prove unworkable in the load test (§3.6), that's a trigger for a fresh, wider evaluation, not a silent swap |

## 7. Follow-ups this ADR intentionally defers

- `qdrant-client` is **not yet added to `pyproject.toml`** — no `VectorStorePort` adapter exists yet
  (`platform/ports.py` is still Protocol-only). Add it when the `knowledge` module's adapter is actually
  built, not speculatively now.
- `docker-compose.yml` gets a `qdrant` service for local dev parity as part of this change (see repo
  diff) — the local Postgres image stays `pgvector/pgvector:pg17` rather than plain `postgres:17`,
  because the already-applied `20260801_0001_core_tenancy` migration creates the `vector` extension and
  changing the base image would break that migration on a fresh local bring-up. That `CREATE EXTENSION`
  call is now dead weight (harmless, unused) rather than something worth a migration to remove — flagged
  here so it isn't mistaken for an oversight.
- Confirming a Qdrant Cloud India/APAC region (§3.4) is an explicit review trigger, not a closed
  question.
