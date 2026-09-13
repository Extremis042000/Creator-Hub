# Admin Guide

For the founder (or anyone granted admin access). Sign in with Google
first, then visit `/admin` — `surya.chowdhury0412@gmail.com` has
permanent admin access via `ADMIN_EMAILS`; anyone else needs to be
granted (see "Admins" below). Visiting `/admin` without admin access
shows a clear "not authorized" message, never a broken page.

## Tools

Toggle any of the 5 tools' category and name, and mark it
**Premium only**. Marking a tool premium immediately blocks
non-entitled users from using it — enforced by the API itself, not
just hidden in the UI. Combine with "Admins" → grant a specific
person premium access (see below), or leave a tool premium with no
one granted yet if you want to hold it back entirely.

## Feature flags

Add/edit a flag by key (e.g. `display-ads`), with **Enabled** and
**Rollout %**. Two current real uses:

- Any enabled flag whose key doesn't match a real tool automatically
  renders as a "🚀 Coming Soon" card on the homepage and `/tools` — a
  quick way to tease an upcoming tool without writing any code.
- `display-ads` — must be enabled (alongside
  `NEXT_PUBLIC_ADSENSE_PUBLISHER_ID` being set) for ads to actually
  render anywhere. Disable it any time to pull ads instantly, no
  redeploy needed.

**Rollout %** is stored but not currently wired to any percentage-
based rollout logic — it's a placeholder field for future use, not
live behavior yet.

## Digital products

Create/edit products: name, price, currency, category (auto-created
by slug if new), and a **file reference** — this must match a
filename actually present in the backend's secure files directory
(`SECURE_FILES_DIR`, not web-accessible), not an arbitrary URL. Toggle
**Active** to hide/show a product from `/store` without deleting it
(there is no delete — matches the same pattern as Tools/Affiliate
products, so historical orders always still resolve to a real product
row).

## Affiliate products

Create/edit gear/product listings shown on `/gear`: name, brand,
category, price info (free text, e.g. "$49.99"), the real affiliate
URL, merchant, region, and disclosure text. The real affiliate URL is
never exposed to visitors directly — every click always goes through
the server-logged redirect endpoint first. Toggle **Active** the same
way as digital products.

## Admins

Grant or revoke admin access by email. **The person must have signed
in at least once already** (an account must exist) before you can
grant them — there's no way to pre-authorize an email that's never
signed in. `ADMIN_EMAILS`-listed admins (the founder bootstrap
allowlist) show up here too but can't be revoked from this screen —
only by editing that env var and redeploying.

## Premium access

Same pattern as Admins: grant or revoke premium access by email, and
the target must have signed in at least once first. Granting premium
here is currently the *only* way anyone gets premium access — there
is no self-serve purchase flow for it yet (see `MONETIZATION.md` §6).

## What's not here yet

- No email/notification tooling — no admin screen for the
  `EmailEvent`/transactional-email pipeline described in the original
  system-design doc; it was never built.
- No analytics dashboard inside the admin panel — use Google
  Analytics directly (once `NEXT_PUBLIC_GA_MEASUREMENT_ID` is set) or
  query the database directly for anything more specific.
- No bulk import/export for any of the tables above — everything is
  one row at a time through the forms described here.
