# ADR-002 — HTTP middleware architecture

**Status:** Accepted · **Date:** 2026-08-02 · **Decider:** sole engineer
**Supersedes:** nothing — elaborates ADR-000 §2.7 (auth/authz/security), §2.9 (observability), §2.11
(platform/edge), §2.12 (rate limiting), §3 (API design rules) with the concrete decisions those sections
left to implementation time. Nothing here contradicts a position ADR-000 already took.
**Review trigger:** the first streaming (`text/event-stream`) route landing in `runs/`, the DB-driven
config plane landing (ADR-000 §2.10), or a FastAPI/Starlette major-version bump (§8 below documents two
internal-shape dependencies this design leans on).

---

## 1. Decision

Every cross-cutting HTTP concern lives in one of two tiers, never mixed:

- **Tier 1 — `core/middleware/*`, true ASGI/Starlette middleware**, registered once via
  `install_middleware(app, settings)` and run on *every* request, before routing, before any identity is
  known: correlation id, access log + metrics, security headers, Host validation, body size limit, CORS,
  request timeout.
- **Tier 2 — `Depends()` / `route_class=` — identity- and route-aware**, wired per-router: authentication
  (`core/security.py::get_tenant_id`, pre-existing), authorization (`core/authorization.py::require_scope`),
  business rate limiting (`core/middleware/rate_limit.py`), idempotency replay
  (`core/middleware/idempotency.py::IdempotentRoute`).

The split is not stylistic. Starlette's router runs *inside* the middleware stack — a true middleware
never sees a matched route, path params, or a verified tenant, because none of those exist yet when it
runs. Auth, scopes, per-route rate policy and idempotency all need at least one of those, so they cannot
be tier 1 without either re-implementing routing inside a middleware or re-verifying credentials twice.
`core/security.py` already made this call for authentication before this ADR existed; tier 2 here is that
same reasoning applied consistently to the newer concerns.

## 2. Tier 1 — execution order

Outermost (runs first) to innermost, as actually wired in `install_middleware`:

| # | Middleware | Why here |
|---|---|---|
| 1 | `CorrelationIdMiddleware` | Assigns `request_id`/`trace_id`. Must run before anything else that logs or correlates. |
| 2 | `AccessLogMiddleware` | Wraps everything below — a request rejected by any later layer still produces exactly one log line and one metrics point. |
| 3 | `SecurityHeadersMiddleware` | Stamps response headers on every outcome, including a later rejection. |
| 4 | `TrustedHostMiddleware` (Starlette) | Host-header validation — the cheapest possible security check, before anything reads the body. |
| 5 | `RequestTimeoutMiddleware` | Bounds everything still downstream — **including** the body-size limiter's own drain loop (see below), CORS, the Dishka request scope, routing, the handler. |
| 6 | `RequestBodySizeLimitMiddleware` | Rejects an oversized body before CORS, DI, or routing run. |
| 7 | `CORSMiddleware` (Starlette, optional) | Only mounted when `cors_allowed_origins` is non-empty. |
| 8 | Dishka `ContainerMiddleware` (`setup_dishka`, called *before* `install_middleware`) | Opens the per-request DI scope; innermost tier-1 layer, immediately outside routing. |

**Timeout wraps the body limiter, not the other way around — this was a real bug, caught in review, not a
hypothetical.** An earlier version of this ordering put `RequestTimeoutMiddleware` innermost (position 7)
and the body-size limiter at position 5, outside it. `RequestBodySizeLimitMiddleware`'s own drain loop
(`_drain_up_to_limit`) is a plain `await receive()` loop with no timeout of its own — bounded in *bytes*
(never more than `max_bytes` accumulate), but not in *time*. With the limiter outside the timeout
middleware, a client that trickles a body slowly while staying under the byte limit the whole time could
hold the request — and the connection, and the task — open indefinitely, because `RequestTimeoutMiddleware`
never even started its clock until the body finished draining. Moving the timeout middleware to wrap the
limiter (§2 table, position 5 vs. 6) closes this: the drain loop now runs inside the same
`asyncio.wait_for` budget as everything else.

**The add-order gotcha, verified by hand.** Starlette's `Starlette.add_middleware(X)` calls
`self.user_middleware.insert(0, Middleware(X, ...))` — it *prepends*. `build_middleware_stack` then wraps
outward-in over `reversed(user_middleware)`. Net effect: **the last middleware added via `add_middleware`
ends up outermost and runs first** — the opposite of "first added, first to run". Two throwaway probe
scripts nailed this down during development (three middleware recording their own dispatch order; a
`ContainerMiddleware`-reachability check) — the mechanism is also locked in as a regression test,
`tests/unit/test_middleware_ordering.py::test_last_added_middleware_runs_first`. `install_middleware`
therefore calls `app.add_middleware(...)` in the *reverse* of the table above, and `setup_dishka` must run
*before* `install_middleware` so Dishka's own middleware ends up innermost. Both call sites carry a
comment pointing back to this section — if a future edit "fixes" the call order because it reads
backwards at a glance, `test_last_added_middleware_runs_first` fails immediately instead of the bug
surfacing as "security headers missing from a 413 response" weeks later.

**Deliberately absent from tier 1:**

- **CSRF.** This API is Bearer-token-only, never cookie-based. CSRF requires an ambient credential a
  browser attaches automatically to a cross-site request; a Bearer token in an `Authorization` header is
  never attached that way. Add double-submit-cookie CSRF protection only if cookie-based session auth is
  ever introduced.
- **gzip/response compression.** ADR-000 §2.11 puts this at the GKE Gateway edge. Starlette's
  `GZipMiddleware` also compresses synchronously in-process — a real "never block the event loop" risk for
  a large response this API has no reason to take on itself.
- **Per-IP rate limiting.** ADR-000 §2.12: Cloud Armor's job at the edge. In-app limiting is intentionally
  business-dimension-scoped (§3 below), not a duplicate of the edge control.
- **SSRF protection.** Not an inbound-HTTP concern — it governs *outbound* calls the agent runtime/tools
  make (crawler, MCP tool calls), owned by `guardrails`/`tools` per ADR-000 §2.7, not yet built.
- **Path traversal.** No endpoint accepts a filesystem path today (every path param is a typed `uuid.UUID`
  converter). Revisit when a document-upload endpoint exists — validate against an allow-list and resolve
  + contain within a base directory rather than trusting the value.
- **SQL injection.** Not a middleware concern here: every query in this codebase already goes through
  SQLAlchemy's parameterized `text()`/ORM layer (existing convention, unchanged by this ADR); none of the
  new code introduces string-formatted SQL.

## 3. Tier 2 — authentication, authorization, rate limiting, idempotency

### 3.1 Authentication (`core/security.py`, extended)

Pre-existing (`get_tenant_id`, self-issued RS256 JWT + hashed API keys, ADR-000 §2.7). This ADR adds:

- **Audience.** `settings.jwt_audience: str | None`. `None` leaves PyJWT's own default (`verify_aud=True`)
  in effect: a token with no `aud` claim still validates either way; a token that *does* carry one is
  rejected the moment there's no configured audience to check it against — PyJWT's secure default, not
  extra behavior layered on here. Verified by hand (`tests/integration/test_jwt_auth.py::TestAudience`).
- **Revocation.** JWTs carrying a `jti` claim are checked against a Redis deny-list
  (`REVOKED_JWT_KEY_PREFIX`); `core/security.py::revoke_jwt` populates it (no admin endpoint calls it
  yet — the storage layer exists so one can be added without inventing it). A token minted without `jti`
  cannot be individually revoked before it expires; that's a property of the token, not a gap. **Fails
  open** on a Redis outage — see §7.
- **Scopes.** JWT `scope` claim (OAuth2-conventional, space-delimited) and the `api_keys.scopes` column
  (already in the schema, not previously read) both populate `request.state.scopes`.
- **Memoization.** `get_tenant_id` now caches its result on `request.state.tenant_id`/`.scopes` and binds
  `tenant_id` into the structlog context — closing a pre-existing gap where `bind_request_context`'s
  `tenant_id` parameter was defined but never actually supplied (`RequestContextMiddleware` runs before
  auth resolves it). The memoization also makes `IdempotentRoute` (§3.4) free for the common
  API-key-authenticated case: it calls `get_tenant_id` directly, outside `Depends`, and the endpoint's own
  `Depends(get_tenant_id)` then hits the cache instead of a second Postgres round trip.

### 3.2 Authorization (`core/authorization.py`, new)

`require_scope(scope)` — a `Depends()` reading `request.state.scopes`, nothing else. Authentication answers
"who is this caller"; authorization answers "is this caller allowed to do *this specific thing*" — a valid,
unexpired, correctly-signed token can still be denied a specific action (`403`, not `401`) because its
scopes don't cover it. **Not wired into `agents/router.py` or any other real route** — no code path issues a
`scope` claim or populates `api_keys.scopes` yet, so attaching this to a live route would lock out every
caller. It's built, tested (`tests/unit/test_authorization.py`, `tests/integration/test_jwt_auth.py::
TestScopes`), and ready to attach via `dependencies=[Depends(require_scope("agents:write"))]` the day scope
issuance exists.

### 3.3 Rate limiting (`core/middleware/rate_limit.py`, new)

ADR-000 §2.12: edge does coarse per-IP; **business rate limiting is in-app over Valkey, token bucket, per
dimension**. `rate_limiter(dimension, policy=None)` returns a dependency; wired at router level
(`agents/router.py`: `dependencies=[Depends(rate_limiter("agents"))]`, the reference example). Bucket key:
`(dimension, tenant_id, route)`. Atomicity comes from one Lua `EVAL` doing read-refill-check-write against
Redis server time (`TIME`, not client wall-clock — correct under clock skew across replicas) —
`tests/integration/test_middleware_rate_limit.py::TestTokenBucket::
test_concurrent_requests_never_exceed_capacity` runs 10 concurrent requests against a capacity-3 bucket and
asserts exactly 3 succeed, which a naive GET-then-SET implementation would not guarantee.

Declaring `tenant_id: Annotated[uuid.UUID, Depends(get_tenant_id)]` as the dependency's own parameter
(rather than calling `get_tenant_id(request)` directly) is what makes this free: FastAPI's per-request
dependency cache, keyed by the callable, means a route that separately declares `Depends(get_tenant_id)`
for its own tenant scoping triggers exactly one verification no matter how many rate-limited/scoped
dependencies also ask for it.

Config-driven, bootstrap-level today (`rate_limit_capacity`, `rate_limit_refill_per_second`,
`rate_limit_enabled`) — ADR-000 §2.10 wants this DB-driven per tenant/workspace/agent/model/endpoint
eventually; the `RateLimitPolicy` value object and `policy=` override parameter are the seam a DB-backed
resolver drops into without changing the bucket algorithm. **Fails open** on a Redis outage — see §7.

### 3.4 Idempotency (`core/middleware/idempotency.py::IdempotentRoute`, new)

ADR-000 §3: "every mutating endpoint accepts `Idempotency-Key`". A custom `APIRoute`
(`route_class=IdempotentRoute`), not a dependency: a dependency can block an endpoint from running (a
`raise` does that fine) but has no supported way to *capture the response a first execution produced* so a
retry can replay it — FastAPI finishes resolving every dependency, `yield`-style included, strictly before
it builds that response. Only activates for `POST`/`PUT`/`PATCH`/`DELETE` requests that carry the
`Idempotency-Key` header — zero overhead otherwise, safe to attach at the router level even for GET-heavy
routers (`agents/router.py`, the reference example).

Mechanics: `SET NX` claims `idem:{tenant_id}:{scope_fingerprint}:{route_fingerprint}` — `scope_fingerprint`
is a SHA-256 of the caller's sorted scopes (closes a same-tenant, different-privilege replay-leak: without
it, `tenants`'s self-vs-`tenants:admin` split would let a privileged caller's cached response replay
verbatim to a less-privileged credential of the same tenant reusing the same key+path+body), and
`route_fingerprint` is a SHA-256 of `{path}\x00{key}` rather than a bare `f"{path}:{key}"` join — this
app's own route templates use literal `:` suffixes (`:rotate`, `:revoke`), and `key` is fully
client-controlled, so a `:`-joined key let two different (method, path) pairs collide (e.g. `DELETE
/v1/api-keys/{id}` with `Idempotency-Key: rotate:X` computed the same string as `POST /v1/api-keys/{id}
:rotate` with `Idempotency-Key: X`) — a confirmed bug, fixed and regression-tested
(`TestCacheKeyCollision`). The claim carries a short "pending" TTL before the endpoint runs (blocking a
genuinely concurrent duplicate — `tests/integration/test_middleware_idempotency.py::
TestConcurrentDuplicates` sends 5 concurrent requests at a deliberately slow handler and asserts exactly one
runs to completion, the other four see `409`); on success (`< 500`) the response is cached with the real
TTL; on an exception or a `5xx`, the claim is released rather than poisoned, so a retry is free to try
again immediately. A same-key request with a different body fingerprint (SHA-256 of the raw bytes) is a
`409`, not a silent overwrite. **Fails open** on a Redis outage before the claim resolves; a Redis failure
*after* the handler already ran successfully no longer re-runs it a second time (also a confirmed,
fixed bug — `TestRedisFailureAfterSuccess`) — see §7.

**Restricted to non-streaming JSON responses** — `runs/router.py`'s `runs_stream_router` emits a real
`StreamingResponse` for the SSE path (`POST /v1/agents/{id}/runs/stream`) and stays on plain `DishkaRoute`
for exactly this reason; buffering `response.body` to cache it, which is exactly what this does, is
wrong for a stream by construction. Do not attach `route_class=IdempotentRoute` to a streaming router.

## 4. Context object — the `request.state` contract

The only sanctioned channel from one piece of middleware to another, or to a route handler — never a
module-level global (which would be shared, mutable, and wrong the instant two requests overlap on the
event loop; every new piece of state introduced by this ADR was checked against that specifically).

| Attribute | Set by | Type | Notes |
|---|---|---|---|
| `request_id` | `CorrelationIdMiddleware` | `str` | Client-supplied if it matches `^[A-Za-z0-9_-]{1,128}$`, else a fresh UUID4 — an adversarial value is never echoed into the response header or log lines. |
| `trace_id` | `CorrelationIdMiddleware` | `str` | Current OTel span's trace id if valid, else falls back to `request_id`. |
| `tenant_id` | `core/security.py::get_tenant_id` | `uuid.UUID` | Set once, on first successful authentication; a second `get_tenant_id`/`IdempotentRoute` call in the same request hits this cache instead of re-verifying. |
| `scopes` | `core/security.py::get_tenant_id` | `frozenset[str]` | Empty set if the credential carries none — `require_scope` denies by default, never grants by default. |
| `dishka_container` | Dishka's `ContainerMiddleware` | `AsyncContainer` | Pre-existing; every tier-1 middleware added after `setup_dishka` and every tier-2 dependency reaches DI-resolved collaborators (`Redis`, `Settings`) through this. |

Nothing in this ADR mutates the request body or headers in place — `RequestBodySizeLimitMiddleware` and
`IdempotentRoute` both *read* the body (`request.body()`, which Starlette caches after the first read, so
downstream code sees the identical bytes) but never rewrite it; `CorrelationIdMiddleware` only *adds* a
response header, never strips or rewrites a request one.

## 5. Configuration (`Settings`, all `AGENTIC_`-prefixed)

| Field | Default | Notes |
|---|---|---|
| `allowed_hosts` | `"*"` | Comma-separated. Refused outright in `prod` (`Settings._reject_wildcard_host_in_prod`). |
| `cors_allowed_origins` | `""` (empty) | Comma-separated. Empty mounts no `CORSMiddleware` at all. |
| `max_request_body_bytes` | `2 MiB` | Enforced via bounded buffer-then-replay, not an unbounded stream count — see §6's body-limit design note. |
| `request_timeout_seconds` | `30.0` | Not the agent-run timeout (5 min soft / 15 min hard, DB config, ADR-000 §6) — see `middleware/timeout.py`'s docstring for why those must never be conflated. |
| `jwt_audience` | `None` | See §3.1. |
| `rate_limit_enabled` / `rate_limit_capacity` / `rate_limit_refill_per_second` | `true` / `60` / `1.0` | Kill-switch + token-bucket parameters. |
| `idempotency_enabled` / `idempotency_ttl_seconds` | `true` / `86400` | Kill-switch + how long a completed record is replayable. |

Every one of these is a bootstrap-level default per ADR-000 §2.10 ("everything else is DB-driven, never an
env var" — these exist because they're needed before the DB-driven config plane is reachable). Moving any
of them to a per-tenant DB row later is additive, not a rewrite: `RateLimitPolicy`/the idempotency TTL are
already passed as values, not read from `Settings` deep inside the algorithm.

## 6. Failure modes

| Condition | Status | Error type | Retry-After |
|---|---|---|---|
| Bad/spoofed `Host` header | 400 | (Starlette `TrustedHostMiddleware`, plain text) | — |
| Body exceeds `max_request_body_bytes` (declared `Content-Length` or streamed) | 413 | `PayloadTooLargeError` | — |
| Handler exceeds `request_timeout_seconds` | 503 | `RequestTimeoutError` | 1s |
| Missing/malformed `Authorization` header | 401 | `UnauthorizedError` | — |
| JWT: bad signature / wrong issuer / wrong or missing audience (when configured) / expired / unknown `kid` / revoked `jti` | 401 | `UnauthorizedError` | — |
| API key: unknown / revoked | 401 | `UnauthorizedError` | — |
| Valid identity, missing required scope | 403 | `ForbiddenError` | — |
| Rate limit bucket exhausted | 429 | `RateLimitedError` | computed from the policy |
| `Idempotency-Key` reused with a different body, or a concurrent duplicate still in flight | 409 | `IdempotencyConflictError` | — |
| Unmatched route, or matched route with a disallowed method | 404 / 405 | `StarletteHTTPException` (remapped by `register_exception_handlers`) | — |
| Request body/path/query fails Pydantic validation | 422 | `RequestValidationError` (remapped by `register_exception_handlers`) | — |
| Anything genuinely unhandled | 500 | generic `ProblemDetail`, no exception detail leaked | — |

Every row *except the first* renders as RFC 9457 Problem Details (`application/problem+json`), carries
`trace_id`, and — where a `Retry-After` applies — a real HTTP header, not only a JSON field
(`core/errors.py::problem_response`, shared by the registered `AgenticError` handler and by tier-1
middleware that rejects a request before routing even happens; see that function's docstring for the
`ServerErrorMiddleware` re-raise subtlety that made a shared, raise-nothing renderer necessary rather than
"just raise and let the handler catch it"). The 404/405 and 422 rows are included in that "every row except
the first" — `register_exception_handlers` also registers handlers for `StarletteHTTPException` and
`RequestValidationError`, so a framework-raised exception this codebase never explicitly throws renders
identically to a hand-raised `AgenticError` rather than falling through to FastAPI/Starlette's own default
`{"detail": ...}` shape. Framework-raised exceptions are no longer a silent exception to the rule; only the
documented Host-header row below is. Nothing in any response body includes a stack trace, an internal
exception message, a library name, or raw SQL — the catch-all handler logs the real exception server-side
(`logger.exception`) and returns a fixed, generic `ProblemDetail` for anything it doesn't recognize.

**The Host-header row is a deliberate exception**, not an oversight: it's Starlette's own
`TrustedHostMiddleware`, unmodified, returning its own `PlainTextResponse`. Host-name matching (including
the `*.example.com` wildcard-subdomain and `www.` → non-`www.` redirect cases Starlette's implementation
already handles) is exactly the kind of security-sensitive string logic this project chooses to reuse from
a maintained, widely-used implementation rather than re-derive for the sake of one response shape being
consistent with the rest — see `middleware/__init__.py`'s composition comment for the same call.

### 6.1 Mandatory: no exception type may bypass Problem Details rendering

Every exception FastAPI/Starlette can raise on this API's behalf — including ones this codebase never
explicitly raises, such as an unmatched route (404), a disallowed method (405), or FastAPI's own
`RequestValidationError` (422) — must have a handler registered in `register_exception_handlers`
(`core/errors.py`) that renders `ProblemDetail`. The only sanctioned exception is the Host-header row
above (Starlette's own `TrustedHostMiddleware`, documented deliberately, §6 above). A future
FastAPI/Starlette upgrade that introduces a new built-in exception type, or a new tier-1 middleware that
rejects a request before routing, must extend this handler set rather than let the framework's default
response shape leak through onto the wire. `tests/unit/test_errors.py::
TestFrameworkExceptionsRenderAsProblemDetails` and `tests/integration/test_health.py`'s 405-on-`/v1/healthz/live`
test are the regression proof and must keep passing.

### 6.2 Required headers

| Header | Direction | Required? | Notes |
|---|---|---|---|
| `Authorization: Bearer <token>` | request | Required on every route that declares `Depends(get_tenant_id)` (every domain router today) | `gta_live_`/`gta_test_`-prefixed → API key; anything else → JWT. |
| `Idempotency-Key` | request | Optional | Only mutating (`POST`/`PUT`/`PATCH`/`DELETE`) routes using `route_class=IdempotentRoute` read it; absent entirely on any other route or method. |
| `X-Request-Id` | request | Optional | Echoed back if present and it matches `^[A-Za-z0-9_-]{1,128}$`; otherwise a fresh one is generated — never trusted verbatim. |
| `Content-Length` | request | Not required, but honored when present | Used as a fast-path rejection before any byte of an over-limit body is read; a chunked body with none is still bounded by streaming the actual byte count. |
| `X-Request-Id` | response | Always present | Whichever value was resolved above. |
| `Retry-After` | response | Present on 429/503 | Seconds, computed from the specific policy that rejected the request (see §6). |
| `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` | response | Always present | See `middleware/security_headers.py`; CSP differs under `/v1/docs` (~~Swagger UI + its OAuth2 redirect~~ **Scalar**, per ADR-000 §3 — the only interactive docs surface today; ReDoc is disabled, `redoc_url=None`). |
| `Strict-Transport-Security` | response | Present outside `local` | Never sent in `local`, where traffic is plain HTTP. |

### 6.3 Input sanitization — what this layer does and does not do

Business input validation (field types, lengths, enums, required-ness) is Pydantic v2's job, per-endpoint,
unchanged by this ADR — every request body already goes through a typed schema before a handler sees it,
and that's where sanitization for a specific field's meaning belongs, not a generic middleware. What *this*
layer sanitizes is the small set of values that cross a trust boundary before any endpoint-specific
validation runs: the client-supplied `X-Request-Id` (regex-bound, replaced rather than trusted, §4), the
raw request body as *bytes* (size-bounded before any parser sees it, §3's body-limit discussion), and the
`Idempotency-Key` cache key (built from a verified `tenant_id`, never a client-supplied tenant claim). No
new HTML-rendering surface exists in this diff, so no new HTML-escaping concern exists either — the API
returns only `application/json`/`application/problem+json`.

## 7. Production considerations

- **Fail-open, not fail-closed, for every Redis-backed protective check** (rate limiting, idempotency, JWT
  revocation) — a Valkey outage degrades protection rather than taking the API down with it. Same trade-off
  `core/health.py` already makes for readiness ("degrade, don't crash"). Each fail-open path logs a
  `warning` so the degradation is visible in the logs/metrics even though it's invisible to the caller.
- **`allowed_hosts` must be a real allow-list in `prod`** — enforced at startup, not a runtime check that
  can be silently skipped.
- **`jwt_audience` should be set once token issuance stamps an `aud` claim** — until then, audience
  checking is inert (no `aud` claim to check), which is correct for the current bootstrap state but not a
  configuration to carry into a multi-service future unexamined.
- **`request_timeout_seconds` must be revisited before the first streaming route ships** — see
  `middleware/timeout.py`'s docstring for the specific, not-yet-tested caveat about `BaseHTTPMiddleware`
  and `StreamingResponse`.
- **Rate-limit/idempotency defaults are process-wide, not per-tenant** — correct for the current one-size
  vertical slice; ADR-000 §2.10's DB-config-plane is the intended graduation path (§5 above).
- **Metrics use OpenTelemetry's Meter API** (`core/metrics.py`), not a vendor SDK — consistent with ADR-000
  §2.9 ("the application never imports a vendor SDK"), reusing the same OTLP pipeline `tracing.py` already
  configures. No new dependency: `opentelemetry-sdk` already ships the metrics API.
- **`core/security.py::load_jwks` does a blocking `os.stat`/`open().read()` on a JWKS cache miss, called
  synchronously from `async def _authenticate` — a deliberate, accepted trade-off, not an oversight missed
  in review.** Flagged explicitly during this PR's review and left as-is after weighing the fix: making it
  properly async would mean `_decode_jwt_claims`/`_verify_jwt` becoming `async def`, which cascades into a
  breaking change to `_verify_jwt`'s tested public contract (`tests/unit/test_security.py` calls it
  synchronously, 16 tests deep) for a cost that's negligible in practice — the expensive path (a full file
  read) only runs once per JWKS rotation, not per request (`_JWKS_CACHE` keyed by mtime), and the *always*-
  synchronous, *always*-per-request cost next to it — RSA signature verification itself, `pyjwt`'s own
  `jwt_decode` — is 1–3 orders of magnitude more expensive and equally unavoidable in any synchronous-crypto
  JWT library. Revisit only if profiling ever shows this specific path mattering, not preemptively.
  `GET /.well-known/jwks.json` (`core/jwks.py`) calls the same function, synchronously from its own
  `async def`, for the identical reason — same cache, same trade-off, no second discussion needed.

## 8. Known limitations / internal-shape dependencies to re-verify on a framework upgrade

Three things this design (and one adjacent script it shares a FastAPI-upgrade blast radius with) leans on
were verified by hand against the exact pinned `fastapi`/`starlette` versions in `pyproject.toml`, not
derived from documented, version-stable API contracts — re-check all three after any FastAPI/Starlette bump
(ADR-000 §10 already flags "FastAPI 0.x breaking changes" as a standing risk; these are the concrete spots
that risk would bite):

1. **The `add_middleware` reversal (§2)** — an implementation detail of `Starlette.add_middleware`/
   `build_middleware_stack`, not a documented contract. `tests/unit/test_middleware_ordering.py` catches a
   change here immediately.
2. **A route's `scope["route"].path` excludes any prefix from `include_router(prefix=...)`** on the
   installed FastAPI version (verified by hand: a route mounted under `/v1` still reports `.path` as e.g.
   `/healthz/live`, not `/v1/healthz/live`). `core/middleware/_route_label.py` works around this by
   substituting resolved path params back into the raw URL instead of reading `.path` at all — prefix-
   agnostic and not dependent on this internal shape — but the underlying oddity is worth knowing about if
   anything else ever needs a templated route path.
3. **`app.routes` does not hold flat `APIRoute` objects for anything registered via `include_router()`** on
   the installed FastAPI version — each becomes a private, lazily-resolved `fastapi.routing._IncludedRouter`
   instead (verified by hand: a naive `isinstance(route, APIRoute)` filter over `app.routes` silently
   matched zero real endpoints). Not this ADR's own code, but the ADR-000 §3 schema-diff gate
   (`scripts/check_openapi_schema.py`) depends on enumerating every route FastAPI actually serves, so it
   hits the exact same internal shape `_route_label.py` above works around for a different reason. The fix
   there uses `fastapi.routing.iter_route_contexts()` — the same helper `fastapi.openapi.utils.get_openapi()`
   itself calls internally to flatten routes for schema generation — rather than re-deriving a second,
   possibly-drifting traversal.

Also worth knowing, not a limitation so much as a documented gotcha that cost real debugging time while
building this: **FastAPI's own body-parsing step
(`fastapi/routing.py`'s request handler) wraps its `request.body()`/`request.json()` calls in a broad
`except Exception: raise HTTPException(400, "There was an error parsing the body")`**, with a carve-out
only for `HTTPException` itself. An earlier version of `RequestBodySizeLimitMiddleware` relied on a
downstream `receive()` call raising `PayloadTooLargeError` mid-stream; on any route with a body parameter,
FastAPI's own handler silently reclassified that into a generic 400, discarding the real 413. The shipped
version decides and responds entirely before calling further into the stack instead (bounded buffer up to
`max_bytes + 1`, then replay) specifically to avoid ever handing a rejection decision to code that might
recategorize it — see `middleware/body_limit.py`'s module docstring.

One more, specific to *testing* this stack rather than the stack itself: driving a genuinely slow, streamed
request body through `httpx.ASGITransport` while the server cuts the request off early (e.g. to prove
`RequestTimeoutMiddleware` bounds `RequestBodySizeLimitMiddleware`'s drain loop) is racy and non-
deterministic — verified by hand that the client, still mid-way through sending its body when the early
response arrives, produces a second `ClientDisconnect`/`RuntimeError` inside Starlette's own exception
machinery, unrelated to whatever's actually under test. `tests/integration/test_middleware_timeout.py::
test_body_limit_drain_loop_has_no_timeout_of_its_own` tests the same property deterministically instead, by
calling `RequestBodySizeLimitMiddleware._drain_up_to_limit` directly against a `receive` callable wrapped in
`asyncio.wait_for` — no HTTP/ASGI transport involved, so no race. Prefer this pattern over a real streamed
HTTP body for anything that needs to prove a middleware bounds an in-progress read.

## 9. Testing

Every scenario below has one or more tests exercising it against a real dependency (Postgres/Valkey via
`testcontainers`, not a mock — this repo's existing convention, ADR-000 §6) except where noted as pure/
in-memory:

- **Success paths** — valid JWT/API key, request under every limit, replayed idempotent response, CORS
  headers for a configured origin, a matching (including wildcard-subdomain) Host header.
- **Invalid input** — malformed `Authorization` header, oversized body (both `Content-Length`-declared and
  chunked/streamed with none), a spoofed/non-matching Host header (including on an otherwise-valid route,
  proving it's rejected before auth even runs).
- **Missing headers** — no `Authorization`, no `Idempotency-Key` (passthrough, not an error), no `Origin`
  (no CORS headers emitted at all — a `CORSMiddleware` configured with an empty allow-list is never even
  mounted, §2 item 7).
- **Malformed / expired / wrong-issuer / wrong-or-missing-audience / unknown-`kid`/tampered-signature /
  revoked / algorithm-confused (`alg: none`, HS256-with-the-RSA-public-key-as-secret) JWTs** —
  `tests/unit/test_security.py` (pure, no IO — the two algorithm-confusion tests hand-construct the forged
  token rather than going through `pyjwt.encode`, since PyJWT itself refuses to *create* an HS256 token from
  an asymmetric key, a separate encode-side guard that isn't what's under test) +
  `tests/integration/test_jwt_auth.py` (HTTP + real Valkey for revocation).
- **Concurrent requests** — `test_middleware_rate_limit.py`'s 10-concurrent-requests-against-capacity-3 and
  `test_middleware_idempotency.py`'s 5-concurrent-duplicates-one-key, both via `asyncio.gather`.
- **Timeouts** — `test_middleware_timeout.py`: a deliberately slow throwaway route against a tiny configured
  timeout, plus a deterministic direct test that `RequestBodySizeLimitMiddleware`'s drain loop is itself
  cancellable by an outer `asyncio.wait_for` (see §8's note on why this isn't driven through a real streamed
  HTTP request).
- **Rate limits** — burst-then-429, `Retry-After` header, independent per-tenant buckets, the
  `rate_limit_enabled=False` kill switch, fail-open on a broken Redis URL.
- **Edge cases** — empty/whitespace/oversized client-supplied `X-Request-Id` (sanitized, not echoed), CSP
  differing between the API surface and the interactive docs page, HSTS present outside `local` only,
  idempotency across different tenants never sharing a key namespace, a failed first attempt (whether via a
  raised exception or a directly-returned 5xx `Response` — two distinct code paths in
  `_run_idempotently`, both tested) not poisoning an idempotency key for the retry, a rejected request
  (bad Host header) still producing exactly one structured log line via captured stdout (`capsys`, not
  `caplog` — `configure_logging` replaces the root logger's handlers wholesale, which discards whatever
  handler `caplog` had already attached; verified by hand), a templated (not raw/high-cardinality) path in
  that log line for a route with path params, a `ZeroDivisionError`-shaped misconfiguration
  (`rate_limit_refill_per_second=0`) refused at `Settings` construction time rather than surfacing as an
  unhandled 500 on the first real request.

`tests/unit/test_middleware_ordering.py`, `tests/unit/test_authorization.py`, and the direct
`_drain_up_to_limit`/`_run_idempotently` calls noted above need no container at all; everything else under
`tests/integration/` follows this repo's existing `pytestmark = pytest.mark.integration` + `testcontainers`
convention. All of the above were additionally verified by hand against a real local Valkey instance during
development (not just written and hoped to pass) — the concurrency/atomicity assertions in particular are
exactly the kind of thing that only a real Redis, not a mock, can actually prove.

## 10. Follow-ups this ADR intentionally defers

- Wiring `require_scope` into any real router — blocked on scope issuance existing somewhere (JWT minting,
  API-key creation UI/endpoint), which is out of scope for a middleware ADR.
- Moving rate-limit policy and idempotency TTL to the DB-driven config plane (ADR-000 §2.10) — blocked on
  that plane existing; the seams (`RateLimitPolicy`, TTL-as-a-parameter) are already in place.
- An admin/revocation endpoint that calls `core/security.py::revoke_jwt` — the storage layer and function
  exist; nothing calls it yet.
- Re-verifying `RequestTimeoutMiddleware`'s interaction with `StreamingResponse` once `runs/` ships a real
  SSE route (§8).
- A `contract`/OpenAPI-fuzz test pass over these error shapes — deferred repo-wide per ADR-000 §6
  (`schemathesis` removed for an unrelated Starlette-CVE reason), not specific to this ADR.
