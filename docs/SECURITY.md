# Security

## Authentication

Google Sign-In only — no password ever handled or stored by this app.

1. Frontend gets a Google ID token via Google's own sign-in flow.
2. `POST /api/v1/auth/google` — `GoogleTokenVerifierService` verifies
   the token's signature/issuer/audience/expiry against Google's own
   library (`google-api-client`), not a hand-rolled JWKS check.
3. Backend upserts an `app_user` row keyed by `google_subject_id`.
4. Backend issues its **own** HS256 JWT (`JwtService`, secret =
   `JWT_SECRET`, 7-day expiry) — a separate concern from Google's
   token, used for every subsequent API call.
5. `JwtAuthenticationFilter` validates that JWT on every request;
   `Authentication.getPrincipal()` is the user's UUID.

**Stored client-side in `localStorage`, not an httpOnly cookie** — a
deliberate MVP simplicity choice made when no endpoint yet handled
payment or highly sensitive data. This has not been revisited since
real payments landed (Phase 20) because real payments are still
gated on the founder's Cashfree KYC and not yet live — worth
revisiting before that goes live for real.

Rotating `JWT_SECRET` invalidates every existing session (forces
everyone to sign in again) — expected, not a bug.

## Admin access

Two mechanisms, deliberately separate:

- **`ADMIN_EMAILS`** (env var, comma-separated) — the permanent
  "founder" bootstrap allowlist. Can only be changed by editing the
  env var and redeploying; not revocable at runtime.
- **`admin_grant` table** — lets an existing admin grant admin access
  to another already-signed-in user at runtime (via the admin panel),
  without an env var/redeploy round trip. A target user must have
  signed in at least once (an `app_user` row must already exist)
  before they can be granted admin.

Every `/api/v1/admin/**` endpoint calls
`AdminService.requireAdmin(authentication)` as its first line — no
admin capability is enforced only in the frontend.

There is no role hierarchy (no "super admin" vs. "content admin") —
one flat admin capability. `PremiumAccessService`'s premium-access
grants are a completely separate mechanism from admin access (a user
can be premium without being admin, and vice versa).

## Premium tool access (Phase 22)

`Tool.premiumOnly` is enforced **server-side** by
`PremiumAccessService.requireAccessIfPremium()` inside every one of
the 5 tool controllers — calling a premium tool's API directly without
a valid entitlement returns a real 401 (not signed in) or 403 (signed
in, not entitled), not just a frontend lock icon. The frontend
(`ToolCard`, `PremiumToolGate`) fails safe: it defaults to
locked/blank until it has actually confirmed entitlement client-side,
never briefly shows an unlocked form to a non-entitled visitor.

## Digital product downloads

`Product.fileRef` never points to a publicly reachable path. A
download requires:

1. An entitlement check (`user_entitlement` row for that product or
   `"premium-tools"`).
2. A short-lived (5 min), HMAC-signed token (`DownloadTokenService`,
   secret = `DOWNLOAD_SIGNING_SECRET` — deliberately a **different**
   secret from `JWT_SECRET`, since a leaked download link is a
   different blast radius than a leaked session).
3. `DownloadController` verifies the token's signature, expiry, and
   path containment (no traversal outside the secure files directory)
   before streaming the file.

A tampered or expired token returns 404, not a more specific error
that would help an attacker distinguish "wrong signature" from
"expired" from "wrong path."

## Payments

`PaymentProvider` is an interface; no bean is registered at all until
both `CASHFREE_CLIENT_ID`/`CASHFREE_CLIENT_SECRET` are set (a
deliberate plain-null check in `PaymentProviderConfig`, not
`@ConditionalOnProperty`, which would wrongly treat an empty-string
env var as "present"). Webhook signature verification
(`x-webhook-signature`/`x-webhook-timestamp`, HMAC-SHA256 against
Cashfree's own documented scheme) happens before any order state
changes; a duplicate webhook delivery is a silent no-op (idempotent),
not a duplicate `payment` row.

Phase 19's free "test purchase" path (any signed-in user can claim a
digital product for $0) is a separate, explicitly labeled kill-
switched mechanism (`TEST_PURCHASES_ENABLED`, default `true`) — it
must be set to `false` once real payments go live, so the two never
coexist.

## Secrets handling

- `backend/.env` and `frontend/.env.local` are gitignored; never
  committed. `.env.example`/`.env.local.example` document what's
  needed without real values.
- Production secrets live only in Render's env var store and
  Cloudflare's `wrangler`/dashboard config — never in git.
- CI secrets are one combined GitHub Actions secret
  (`CREATOR_HUB_SECRETS`), parsed and individually masked
  (`::add-mask::`) before use, so a multi-line secret blob never
  appears unredacted in a CI log even though it's stored as one blob.
- A real Google OAuth `client_secret_*.json` file was found sitting
  unprotected at the repo root before the first commit — added to
  `.gitignore` and confirmed excluded (`git status --short`) before
  ever running `git add`. Worth remembering: always check for stray
  credential files before a first `git init`/commit on an existing
  working directory.

## PII / privacy defaults

- `send-default-pii: false` for Sentry (backend) — no request headers
  or user IP sent to Sentry by default.
- No analytics/ads/error-tracking script ever loads unless its own
  env var is explicitly set (see `ARCHITECTURE.md`'s dual-switch
  pattern) — a visitor with none of those configured gets zero
  third-party requests from this site.
- Anonymous tool use creates no `app_user` row at all — there is no
  synthetic/placeholder user record "holding a slot" for anonymous
  visitors.

## CORS

`SecurityConfig`'s CORS configuration explicitly allowlists origins
(`extremis.cors.allowed-origins` — currently `localhost:3000` and the
production Workers URL) and methods (GET/POST/PATCH/PUT/DELETE — PATCH
was missing initially and had to be added after a real bug report).
No wildcard origin.
