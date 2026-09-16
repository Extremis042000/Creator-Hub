# Monetization

Current real status of every revenue stream. For the original
strategy/sequencing rationale, see `decisions/01-business-blueprint.md`;
for phase-by-phase build history, see `decisions/03-development-roadmap.md`.

## 1. Affiliate links (`/gear`) — live, no real product yet

Admin-managed affiliate products at `/api/v1/admin/affiliate-products`;
public listing at `/gear` with a standard FTC-style disclosure banner.
Every click routes through `GET /api/v1/affiliate/{id}/redirect`,
which logs a click row **then** 302s to the real merchant URL — click
counts are always server-verified, never trusting the frontend to
fire its own event correctly.

**Not yet real:** no actual affiliate-program product has been added.
Adding one requires the founder's own enrollment in a specific program
(e.g. Amazon Associates) first — there's no way to "auto-fetch" a real
personal affiliate link from open data. Once enrolled, add the real
product via the admin panel above.

## 2. "Hot Game Deals" (`/deals`, homepage teaser) — live, engagement only

**This is not an affiliate-commission source.** It's a content/SEO/
engagement feature: `GameDealService` polls the free, public,
no-key CheapShark API every 6 hours, caches in-memory, and every
purchase link points at the real, official Steam store page — no
commission is earned on these clicks. Built as an honest substitute
for a real affiliate feed while #1 above waits on program enrollment.

## 3. Digital products (`/store`) — live, test-mode purchases only

Admin-managed product catalog (`/api/v1/admin/products`). Any
signed-in user can currently "buy" a product for $0
(`POST /api/v1/products/{id}/test-purchase`) — this exists to prove
the full purchase → entitlement → secure download chain works
end-to-end before a real payment provider exists. Purchased products
appear under "My digital products" on `/dashboard`, re-downloadable
any time via a short-lived signed URL.

**Kill switch:** `TEST_PURCHASES_ENABLED` (backend env var, default
`true`) — **must** be flipped to `false` the moment real payments
(below) go live, so free test purchases never coexist with real ones.

**Not yet real:** only one placeholder product exists ("TEST — Sample
Overlay Pack," $0) — real sellable content (templates, overlays,
presets) is a creative asset the founder produces; the platform only
builds the infrastructure to sell and deliver it.

## 4. Real payments (PhonePe) — built, blocked on founder onboarding

`PaymentProvider` interface + `CheckoutService` +
`PaymentWebhookController`, fully built and unit-tested against
PhonePe's real, documented Standard Checkout v2 API (OAuth token,
order creation, webhook signature scheme). Registers as inert
(`POST /api/v1/checkout/sessions` returns 422 "not set up yet") until
`PHONEPE_CLIENT_ID`/`PHONEPE_CLIENT_SECRET`/`PHONEPE_CLIENT_VERSION`/
`PHONEPE_WEBHOOK_USERNAME`/`PHONEPE_WEBHOOK_PASSWORD` are all set (see
`ENV_VARS.md`). Chosen for its UPI-first pricing: ₹0 setup/AMC, 0%
transaction fee on UPI (its dominant use case in India), ~2% on cards.

**Simpler than the earlier Cashfree design in two real ways:** no
customer phone number is needed at order creation (PhonePe's Create
Payment API doesn't ask for one), and PhonePe returns a direct
`redirectUrl` to its own hosted checkout page — the browser navigates
there straight away, no client-side JS SDK step required.

**One real constraint:** PhonePe Standard Checkout is INR-only —
`CheckoutService`/`PhonePePaymentProvider` reject a non-INR order with
a clear error rather than silently sending a wrong amount. `Product`
rows priced for real sale need `currency = "INR"`.

**Blocked on:** the founder completing PhonePe's merchant onboarding —
an identity/business attestation only the account holder can make,
not something automatable. The webhook URL, username, and password
also need setting once in the PhonePe Business Dashboard
(`https://<production backend URL>/api/v1/webhooks/payment`) —
PhonePe echoes the username/password back (hashed) on every webhook
delivery so the signature can be verified.

## 5. Display ads (Google AdSense) — built, blocked on AdSense approval

`<AdSlot>` on all 5 tool pages + the tools directory;
`<AdSenseScript>` loads once, site-wide. **Two independent switches,
both required:** `NEXT_PUBLIC_ADSENSE_PUBLISHER_ID` (env var, set once
AdSense approves) **and** the `display-ads` feature flag (admin
panel — lets the founder pull ads instantly without a redeploy, e.g.
for a policy issue). With either off, zero `adsbygoogle` DOM
footprint — confirmed zero Core Web Vitals impact.

**Blocked on:** the founder applying for and being approved by Google
AdSense (needs real content/traffic history first — do not apply
prematurely, per the founder action checklist). `data-ad-slot` values
are currently placeholders — replace with real per-placement AdSense
ad unit IDs once created.

## 6. Premium entitlements — live, no pricing/checkout tie-in yet

`Tool.premiumOnly` is enforced server-side
(`PremiumAccessService`) — any of the 5 tools can be marked premium
from the admin panel, and premium access is granted per-user, also
from the admin panel (`/api/v1/admin/premium-grants`), independent of
any purchase. There is currently no self-serve way for a visitor to
*buy* premium — it's an admin-grant-only mechanism today. The natural
next step, not yet built, is tying a premium grant to either a
one-off `product` purchase or a recurring `Subscription` (the latter
was designed in the original system-design doc but never built — see
`DATABASE.md`).

## Founder-only steps, summarized

Everything marked "blocked on" above needs a founder action —
consolidated in `decisions/05-founder-action-checklist.md`. The build
process for all of them is the same: build everything technically
possible first, document exactly what the founder needs to do, stop
at that authorization/approval step, resume the moment real
credentials are supplied.
