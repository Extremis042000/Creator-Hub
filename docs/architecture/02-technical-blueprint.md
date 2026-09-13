# EXTREMIS Creator Hub — Technical Blueprint v1

## 1. Tech Stack (revised per founder approval — Spring Boot backend)

| Layer | Choice | Why |
|---|---|---|
| Frontend | Next.js (App Router) + TypeScript | SSR/SSG for SEO, huge ecosystem, free-tier friendly hosting |
| Styling | Tailwind CSS | Fast to build a distinct dark gaming aesthetic without a heavy design system |
| Backend | Java 21 + Spring Boot 4 (updated at Phase 4 project init — 4.1.1 is current as of build time, Spring Boot 3 was the Phase 0-2 planning assumption) | Founder-requested primary backend: owns all business logic, APIs, DB access, auth, orders, products, subscriptions, and future payment integrations |
| API style | REST (JSON) | Simple, well-understood contract between Next.js and Spring Boot |
| Database | PostgreSQL | Relational integrity for orders/entitlements/subscriptions; free-tier providers exist (Neon, Supabase, Railway) |
| ORM | Spring Data JPA / Hibernate | Standard Spring persistence layer, repository pattern |
| Migrations | Flyway | Versioned, auditable SQL migrations, runs on Spring Boot startup or CI |
| Security | Spring Security | Endpoint-level authorization, session/JWT validation |
| Authentication | Google OAuth (verified server-side) + JWT issued by Spring Boot to the Next.js frontend | Official OAuth flow only; backend never touches the founder's or a user's raw Google password |
| API docs | OpenAPI / Swagger (springdoc-openapi) | Auto-generated, browsable contract for every endpoint, kept in sync with code |
| Hosting (frontend) | Vercel (free tier) | Zero-cost Next.js-native hosting |
| Hosting (backend) | Free-tier Java host (e.g. Render or Railway free tier) | Spring Boot needs a long-running JVM process, not a serverless function — chosen free tier must support that |
| Email | Provider-agnostic via env vars (e.g. Resend/Postmark free tier), triggered from Spring Boot services | Swappable, no vendor lock-in, no hardcoded secrets |
| Payments | Provider abstraction (see §6), implemented as a Spring service interface | Real provider chosen later based on founder eligibility/KYC |
| Analytics | Google Analytics 4 (founder's own account), fired from the Next.js frontend | Owned by founder via official GA property creation |

## 2. Architecture Overview

```
                     ┌─────────────────────────┐
   Visitor ────────▶ │   Next.js Frontend      │
                     │   (Vercel)              │
                     │  - SSG tool/content pgs │
                     │  - SSR dashboard/admin  │
                     └───────────┬─────────────┘
                                 │  REST API calls (JSON over HTTPS)
                                 ▼
                     ┌─────────────────────────┐
                     │  Java Spring Boot        │
                     │  Backend                 │
                     │  - Controller layer      │
                     │  - Service layer         │
                     │  - Spring Security /     │
                     │    JWT issuance          │
                     │  - OpenAPI/Swagger docs  │
                     └───────────┬─────────────┘
                                 │  Spring Data JPA / Hibernate
                                 ▼
                     ┌─────────────────────────┐
                     │  PostgreSQL              │
                     │  (Flyway-migrated)       │
                     └─────────────────────────┘

           Backend also integrates outward to:
           ┌──────────────┐  ┌──────────────┐  ┌───────────────┐
           │ Google OAuth │  │ Payment      │  │ Email service │
           │ (token verify)│  │ Provider     │  │ (transactional)│
           │              │  │ (webhook-    │  │                │
           │              │  │  verified)   │  │                │
           └──────────────┘  └──────────────┘  └───────────────┘
```

Every one of the 5 MVP tools follows the same request path:

```
Frontend (Next.js)
   ↓  POST /api/v1/tools/<tool-slug>
Spring Boot Controller
   ↓
Service Layer (business/calculation logic)
   ↓
(Optional) Repository / PostgreSQL — only if the result is saved for
sharing; anonymous one-off calculations never touch the database
   ↓
Response DTO
   ↓
Frontend renders result
```

All 5 MVP tools are deterministic (no AI inference call, no per-use
cost) and run their logic entirely inside the Spring Boot service
layer — the frontend only collects input and renders output.

## 3. Database Design

Core entities (Prisma models), matching the conceptual model in the
spec:

```
User (id, email, googleId?, createdAt, updatedAt)
  1—1 Profile (displayName, avatarUrl, ...)
  1—N ToolUsage (toolId, inputSummary, createdAt)
  1—N Orders
  0/1 Subscription

Tool (id, slug, name, category, isPremiumOnly, createdAt)
ToolUsage (id, userId?, toolId, inputData JSON, createdAt)
GeneratedResult (id, toolId, inputData, outputData, ownerUserId?,
                 shareToken, expiresAt, createdAt)
  -- anonymous-friendly: ownerUserId nullable, shareToken for link-sharing

ProductCategory (id, name, slug)
Product (id, categoryId, name, priceCents, currency, fileRef,
         isActive, createdAt)
Order (id, userId, status, totalCents, currency, createdAt)
OrderItem (id, orderId, productId, priceCents, quantity)
Payment (id, orderId, provider, providerRef, status, verifiedAt)

Subscription (id, userId, plan, status, currentPeriodEnd, provider,
              providerRef)
UserEntitlement (id, userId, entitlementKey, source, grantedAt,
                 expiresAt?)

AffiliateProduct (id, name, brand, category, priceInfo, affiliateUrl,
                   merchant, region, disclosureText, isActive)
AffiliateClick (id, affiliateProductId, userId?, sessionRef, createdAt)

FeatureFlag (id, key, isEnabled, rolloutPercent, updatedAt)
EmailEvent (id, userId?, type, providerMessageId, status, createdAt)
AnalyticsEvent (id, userId?, sessionRef, eventName, propsJSON,
                createdAt)
AdminUser (id, userId, role, createdAt)
```

Conventions: UUID PKs, `createdAt`/`updatedAt` on every table,
`deletedAt` (soft delete) on `User`, `Product`, `AffiliateProduct`.
Indexes on all FK columns and on `Tool.slug`, `Product.slug`,
`GeneratedResult.shareToken`. Modeled as Spring Data JPA `@Entity`
classes with Flyway-managed SQL migrations (`db/migration/V1__init.sql`,
etc.) — full entity classes + migration files are a Phase 2 deliverable
once this blueprint is approved.

**Minimal PII principle:** `GeneratedResult` and `ToolUsage` never
require a signed-in user; anonymous usage stores no personal data at
all, only a share token for link-based sharing, with an expiry.

## 4. API Design (Spring Boot REST controllers, representative — full contract is a Phase 1 PRD deliverable)

All endpoints versioned under `/api/v1`, documented live via
springdoc-openapi at `/swagger-ui.html`.

```
POST /api/v1/tools/valorant-sensitivity-converter   -> compute + optional save
POST /api/v1/tools/bgmi-sensitivity-helper
POST /api/v1/tools/kd-calculator
POST /api/v1/tools/gaming-title-generator
POST /api/v1/tools/gaming-description-generator
GET  /api/v1/results/{shareToken}                   -> fetch shared result

GET  /api/v1/products                                -> list active products
POST /api/v1/checkout/sessions                        -> create checkout session
POST /api/v1/webhooks/payment                          -> provider webhook (signature-verified)

GET  /api/v1/affiliate/{id}/redirect                   -> logs click, 302 to affiliate URL

GET  /api/v1/admin/**                                  -> admin-only, Spring Security role-gated
```

Every controller delegates to a service class (business logic) and
returns a typed response DTO — controllers never contain business
logic themselves. All mutating endpoints: `@Valid` request DTOs
(Bean Validation / Jakarta Validation annotations), a
`@ControllerAdvice` global exception handler producing a consistent
error DTO, rate limiting on public tool endpoints (e.g. Bucket4j) to
prevent abuse, and CORS configured to allow only the Next.js
frontend's origin.

## 5. Authentication Flow

```
Anonymous visitor → uses any free tool → no login required
                  → optionally signs in via Google OAuth
                  → Spring Boot verifies the Google ID token server-side
                  → Spring Security issues a signed JWT (or session) back
                    to the Next.js frontend
                  → ToolUsage/GeneratedResult can now be linked to userId
Premium unlock    → requires a valid JWT + active Subscription/Entitlement,
                    checked by Spring Security on protected endpoints
```

Google OAuth uses the standard OAuth2/OIDC flow — the Next.js frontend
initiates sign-in, Google returns an ID token, and Spring Boot
(Spring Security's OAuth2/JWT support) verifies that token server-side
before issuing its own session credential. The backend never sees or
stores the founder's or a user's Google password.

## 6. Payment & Digital Delivery Flow

```
Client checkout → PaymentProvider.createSession()
                → redirect to provider-hosted checkout
                → provider sends signed webhook on completion
                → server verifies webhook signature
                → Order.status = 'paid', Payment row recorded
                → UserEntitlement granted
                → time-limited signed download URL generated
                → confirmation + download-access email sent
```

`PaymentProvider` is a Java interface (`createSession`,
`verifyWebhook`, `getStatus`) with a concrete Spring `@Service`
implementation per provider, injected via Spring configuration so the
real provider (Razorpay for India availability + subscriptions, or
Stripe if eligible) is swapped via config, not code changes. **No live
payment path is enabled until the founder has completed KYC with a
chosen provider** — this is explicitly a founder-must-do step (see
Founder Action Checklist).

## 7. SEO Architecture

- Static generation (`generateStaticParams`/ISR) for every tool and
  content page.
- Per-page: unique `<title>`, meta description, canonical URL, Open
  Graph + Twitter card metadata, JSON-LD (`SoftwareApplication` or
  `FAQPage` where applicable).
- Global `sitemap.xml` (auto-generated from the tools/content
  registry) and `robots.txt`.
- Every tool page ships with original supporting content: intro,
  how-to-use, FAQ, related tools — never a bare calculator with no
  content, to avoid thin-content penalties and ad-program rejection.

## 8. Security

- All secrets via environment variables (`.env.local` for Next.js,
  Spring `application.yml` + env var overrides for the backend, never
  committed, never hardcoded).
- Input validation via Jakarta Bean Validation (`@Valid`, `@NotNull`,
  `@Min`, etc.) on every request DTO.
- Rate limiting on public tool endpoints (e.g. Bucket4j in Spring
  Boot; upgradeable later).
- Payment webhooks: signature verification mandatory in the Spring
  Boot webhook controller, no trusting client-reported payment
  success.
- Auth: Spring Security-managed JWT/session validation; secure,
  httpOnly, sameSite cookies where sessions are used; CORS locked to
  the frontend origin; CSRF protection on state-changing form
  submissions.
- SQL injection: mitigated structurally via Spring Data JPA/Hibernate
  parameterized queries — no raw string-concatenated SQL.
- Admin routes: `@PreAuthorize` role checks (Spring Security) on every
  admin controller method, no security-by-obscurity.

## 9. Hosting & Free-Tier Plan

| Concern | Free-tier choice | Known limit | Upgrade path |
|---|---|---|---|
| Frontend hosting | Vercel Hobby | Bandwidth/build-minute caps, no commercial-use guarantee at scale | Vercel Pro |
| Backend hosting | Render or Railway free tier (Spring Boot needs a persistent JVM, not a serverless function) | Free-tier apps may sleep on inactivity / have limited monthly hours | Paid tier of same provider |
| Database | Neon or Supabase free Postgres | Storage/connection limits (typically ~0.5GB, limited concurrent connections) | Paid tier of same provider |
| Email | Resend/Postmark free tier | Monthly send-volume cap | Paid tier |
| Domain | Platform subdomain initially (e.g. `*.vercel.app`) | No custom branding until a domain is purchased | Founder buys a domain when ready (small one-time/annual cost — the one place this isn't strictly $0) |

This is explicitly **not** "free forever" — documented limits above,
revisited once real traffic approaches them.
