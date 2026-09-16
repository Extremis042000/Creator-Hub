# Database

Postgres on Neon (serverless, free tier), schema owned entirely by
Flyway (`spring.jpa.hibernate.ddl-auto: validate` — Hibernate only
checks the schema matches, it never generates DDL). Migrations live in
`backend/src/main/resources/db/migration/` (plain SQL) and
`backend/src/main/java/com/extremis/hub/migration/` (one Java
migration, `V2`, because it needs app-generated UUIDs rather than a
database function).

IDs are UUIDs generated on the Hibernate side (`@UuidGenerator`), not
by a Postgres extension — keeps the schema portable across any
Postgres provider without a `CREATE EXTENSION` step.

## Migrations, in order

| Migration | Tables | Phase |
|---|---|---|
| `V1__init.sql` | `app_user`, `profile`, `tool`, `tool_usage`, `generated_result`, `sensitivity_game_profile`, `bgmi_sensitivity_profile` | 4-13 (MVP core) |
| `V2__seed_tools.java` | seeds the 5 `tool` rows | 4-13 |
| `V3__seed_sensitivity_profiles.sql` | seeds `sensitivity_game_profile` (4 of 5 games activated) | 10 |
| `V4__seed_bgmi_profiles.sql` | seeds `bgmi_sensitivity_profile` (all 10 rows, honest per-row confidence) | 11 |
| `V5__admin_basics.sql` | `feature_flag` | 17 |
| `V6__admin_grants.sql` | `admin_grant` | 17 |
| `V7__affiliate.sql` | `affiliate_product`, `affiliate_click` | 18 |
| `V8__digital_products.sql` | `product_category`, `product`, `orders`, `order_item`, `payment`, `user_entitlement` | 19-22 |

`orders` is plural — avoids quoting the reserved SQL keyword `order`
everywhere; no semantic difference from a table named `order`.

There is no `admin_user`/role table and no `subscription` table — both
were designed in the original planning doc (`architecture/
07-phase2-system-design.md` §1.5) but never built; admin access uses
an env-var allowlist (`ADMIN_EMAILS`) plus the `admin_grant` table
instead (see `SECURITY.md`), and there is no recurring-subscription
product yet, only one-off digital products and the flat "premium"
entitlement.

## Core tables

**`app_user`** — one row per real, verified identity (Google sign-in).
No row is ever created for anonymous tool use. `email` is
`NOT NULL UNIQUE`; `google_subject_id` is nullable+unique (safe in
Postgres — multiple NULLs are allowed under a unique constraint).
`deleted_at` is a soft delete (account deletion reactivates cleanly on
next sign-in rather than leaving a stale deleted account permanently
locked out).

**`tool`** — the 5 MVP tools. `tool_type` is the enum identifier used
in code/DTOs (`KD_CALCULATOR`, ...); `slug` is the URL-facing string
(`kd-calculator`). `premium_only` gates access — enforced server-side
by `PremiumAccessService` (Phase 22), not just a frontend lock icon.

**`generated_result`** — a saved, shareable tool output
(`share_token`, Base62). `user_id` nullable (anonymous share links are
allowed); `expires_at` defaults to 30 days out for anonymous results.

**`sensitivity_game_profile`** / **`bgmi_sensitivity_profile`** — the
evidence-sourced conversion constants and sensitivity ranges behind 2
of the 5 tools. Every row carries `source_reference` and
`last_verified_at`; `active` gates whether a row is actually served —
see `research/08-data-verification-report.md` for the full sourcing
trail. A row can exist, be fully researched, and still stay
`active = false` (Overwatch 2) if the evidence didn't clear the bar.

**`feature_flag`** — `key` (primary key, e.g. `"display-ads"`),
`enabled`, `rollout_percent`. Admin-editable; the public
`GET /api/v1/feature-flags` endpoint returns only enabled keys, used
both for "🚀 Coming Soon" tool cards and for gating display ads.

**`admin_grant`** — `user_id` (primary key) + `granted_by_user_id`.
Lets an existing admin grant admin access to another signed-in user at
runtime. `ADMIN_EMAILS` (env var) is the separate, permanent bootstrap
allowlist — it can't be revoked through this table, only by editing
the env var.

**`affiliate_product`** / **`affiliate_click`** — admin-managed
affiliate listings; every click is logged server-side
(`affiliate_click`) before redirecting to the real merchant URL, so
click counts never rely on the frontend firing its own analytics
event correctly.

**`product_category`** / **`product`** — the digital-product catalog.
`product.file_ref` never points to a public path — see `SECURITY.md`
for how downloads are actually served.

**`orders`** / **`order_item`** / **`payment`** — one `orders` row per
purchase attempt (`status`: `PENDING`/`PAID`/`FAILED`/`REFUNDED`), one
`payment` row per order (`provider` is `"test"` for Phase 19's free
test-mode purchases, or `"phonepe"` once real payments are enabled).

**`user_entitlement`** — the generic entitlement table, reused across
features by `entitlement_key`: `"premium-tools"` for Phase 22's
site-wide premium access, or a specific product's id for a digital
product purchase. `source` is `"test-purchase"`, `"admin-grant"`, or
(once live) `"phonepe"`. `UNIQUE (user_id, entitlement_key)` — a user
holds at most one grant per key; granting is idempotent.

## Direct access

Read-only ad-hoc queries during development use `psql` directly
against Neon (connection string in `backend/.env`, `DATABASE_URL_UNPOOLED`
for anything that needs a non-pooled connection). There is no seed/
fixture script beyond the Flyway migrations themselves — every table's
current content is either founder-entered (via the admin panel) or a
migration-seeded, sourced row.
