# Architecture

This describes the system as actually built and deployed. For the
original planning-phase design (some of which changed during
implementation — e.g. no `AdminUser`/role entity was ever built, admin
access is an env allowlist + a grant table instead), see
`architecture/07-phase2-system-design.md`.

## Shape

```
                    ┌─────────────────────────┐
  Browser  ───────▶ │ Next.js 16 (App Router)  │  Cloudflare Workers
                    │ frontend/                │  (via @opennextjs/cloudflare)
                    └───────────┬──────────────┘
                                │ REST (JSON), CORS-restricted
                                ▼
                    ┌─────────────────────────┐
                    │ Spring Boot 4 API        │  Render (Docker, free tier)
                    │ backend/                 │
                    └───────────┬──────────────┘
                                │ JDBC (Hikari) + Flyway
                                ▼
                    ┌─────────────────────────┐
                    │ Neon serverless Postgres │
                    └─────────────────────────┘
```

The frontend never talks to Postgres directly — every read/write goes
through the Spring Boot API. Server components fetch from the backend
at request time (see `frontend/lib/api.ts`); there is no static
build-time data fetch for anything that can change (tools, feature
flags, products, deals).

## Backend package layout (`backend/src/main/java/com/extremis/hub/`)

- `domain/` — JPA entities, shared `Auditable` base (`createdAt`/`updatedAt`).
- `repository/` — Spring Data repositories, one per entity.
- `tools/{kd,sensitivity,bgmi,title,description}/` — one package per
  MVP tool: request/response DTOs, a stateless service, a
  `@RestController` under `/api/v1/tools/*`. All 5 are deterministic
  (no AI calls, no external API dependency at request time).
- `results/` — shareable result links (`GET /api/v1/results/{token}`),
  used by all 5 tools' "Get share link" action.
- `auth/` — Google ID token verification (`GoogleTokenVerifierService`)
  → issues the app's own HS256 JWT (`JwtService`) → `JwtAuthenticationFilter`
  reads it on every request. See `SECURITY.md` for the full model.
- `admin/` — everything under `/api/v1/admin/**`: tools, feature
  flags, products, affiliate products, admin grants, premium grants.
  Every endpoint calls `AdminService.requireAdmin(authentication)` first.
- `premium/` — `PremiumAccessService`, the server-side enforcement
  layer for `Tool.premiumOnly` (Phase 22).
- `affiliate/` — public affiliate listing + click-through redirect
  (`GET /api/v1/affiliate/{id}/redirect`), which is the only path that
  ever reveals the real merchant URL and always logs a click row first.
- `gamedeals/` — polls the free CheapShark API every 6 hours
  (`@Scheduled`), caches in-memory, serves `GET /api/v1/game-deals`.
  Content/engagement feature, not an affiliate-commission source.
- `payment/` — `PaymentProvider` interface + `CheckoutService` +
  `PaymentWebhookController`, provider-agnostic. `payment/cashfree/` is
  the one concrete implementation, registered as a bean only when both
  `CASHFREE_CLIENT_ID`/`CASHFREE_CLIENT_SECRET` are set.
- `digitalproducts/` (product catalog, test-mode purchase, signed
  download tokens), `featureflags/` (public flag list) — see
  `MONETIZATION.md` for how these fit together.
- `web/` — `GlobalExceptionHandler`, `ApiErrorResponse` (one
  consistent error shape for every 4xx/5xx), `RequestIdFilter`.
- `config/` — `SecurityConfig` (CORS + JWT filter chain), Jackson,
  OpenAPI/Swagger config.

Every domain feature follows the same shape: entity → repository →
service → controller, with admin CRUD (where applicable) living in
`admin/` rather than inside the feature's own package, so
`AdminService.requireAdmin()` is the one gate every admin action goes
through.

## Frontend layout (`frontend/`)

- `app/` — Next.js App Router pages. Tool pages are server components
  that fetch tool metadata (premium status) from the backend, then
  render a client `*Form` component wrapped in `PremiumToolGate`.
- `components/` — shared UI (`ui/`), per-feature components
  (`ToolCard`, `AdSlot`, `GoogleAnalytics`, `AdSenseScript`), and
  `admin/` (one panel per admin section, mirroring the backend's
  `admin/` package almost 1:1).
- `components/tools/` — one client form component per tool.
- `lib/api.ts` — the single place every backend call goes through;
  typed request/response shapes live here, not scattered across pages.
- `lib/auth.ts` — JWT storage (`localStorage`, not an httpOnly cookie
  — see `SECURITY.md` for why).
- `lib/ads.ts`, `lib/analytics.ts` — third-party integration no-op
  gating (see "Dual-switch pattern" below).

## Recurring pattern: dual-switch, safe-by-default integrations

Every third-party integration in this codebase (Google Analytics,
AdSense, Cashfree, Sentry) follows the same rule: **fully inert until
explicitly configured, never a hard failure when it isn't.** Concretely:
an env var holds a credential/DSN/ID; if it's blank, the feature does
nothing (no script tag rendered, no bean registered, no events sent).
Some also gate behind a `FeatureFlag` row too (e.g. display ads), so
the founder can pull a feature instantly from the admin panel without
a redeploy or env var change. This is why the site has always been
safe to deploy incrementally, phase by phase, without ever shipping a
half-built feature that could break in production.

## Hosting (see `DEPLOYMENT.md` for the full story)

- **Frontend → Cloudflare Workers**, not Vercel. Vercel's free
  Hobby tier's own fair-use terms explicitly ban payment processing
  and ad monetization — a direct conflict with this site's business
  model, discovered and verified against Vercel's own docs before any
  code was written for hosting.
- **Backend → Render free tier**, via Docker (Render has no native
  JVM runtime).
- **Database → Neon serverless Postgres** (already free-tier from
  Phase 5).

All three are commercial-use-friendly on their free tiers, verified
directly against each provider's own terms before choosing them.
