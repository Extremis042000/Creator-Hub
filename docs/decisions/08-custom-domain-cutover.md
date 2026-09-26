# Custom Domain Cutover — Ideation & Phases

Status: **Phases 30-33 done and live-verified; Phase 34 (optional cleanup)
not started.** Founder purchased `creator-hub.co.in` via GoDaddy
(2026-09-22) — the one non-$0 item on the founder action checklist
(`decisions/05-founder-action-checklist.md`) — then completed the Cloudflare
zone move, the GoDaddy nameserver switch, and the Render custom domain
binding independently. This doc's remaining open item is `www.creator-hub.co.in`
(§5, Phase 31) — blocked on a pre-existing DNS record, not yet on this
assistant's Cloudflare API token permissions.

## 1. Where this starts from

Today, per `DEPLOYMENT.md`:

| App | Host | URL |
|---|---|---|
| Frontend | Cloudflare Workers | `https://extremis-creator-hub.surya-chowdhury0412.workers.dev` |
| Backend | Render | `https://extremis-creator-hub-backend.onrender.com` |

Neither URL is hardcoded in application logic — both flow through env vars
(`NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_SITE_URL` on the frontend;
`extremis.cors.allowed-origins` in the backend's `application.yml`), which is
exactly what makes this cutover a config change plus DNS work, not a code
rewrite.

## 2. Target shape

| Subdomain | Points to | Replaces |
|---|---|---|
| `creator-hub.co.in` (bare/apex) | Cloudflare Worker (frontend) | `extremis-creator-hub.surya-chowdhury0412.workers.dev` |
| `www.creator-hub.co.in` | 301 redirect → bare domain | — |
| `api.creator-hub.co.in` | Render (backend) | `extremis-creator-hub-backend.onrender.com` |

**Why the bare domain is canonical, not `www`:** shorter, matches how the
`.workers.dev` URL had no prefix, and is the more common modern convention.
`www` is kept *resolvable* (redirected, not left dangling — some visitors
and old bookmarks will type it) but never the canonical URL used in
metadata/sitemaps/OG tags.

**Why a separate `api.` subdomain, not e.g. reusing the bare domain with a
path prefix:** the frontend (Cloudflare Worker) and backend (Render
container) are two independent deployments on two hosts — a single domain
can't route to both without a reverse proxy in front of everything, which is
new infrastructure this project doesn't need. A subdomain per service is the
standard pattern and costs nothing extra.

## 3. The one architectural decision: DNS moves to Cloudflare

GoDaddy is only where the domain was *bought* — nameservers (who actually
answers DNS queries for it) are a separate, changeable setting. Two options:

1. **Keep DNS at GoDaddy**, add CNAME/A records there pointing at Cloudflare
   and Render's given targets.
2. **Move DNS to Cloudflare** (add `creator-hub.co.in` as a zone in the same
   Cloudflare account already used for Workers, then change the domain's
   nameservers at GoDaddy to Cloudflare's).

**Decision: option 2.** Cloudflare Workers' "Custom Domains" feature — the
clean way to attach a real domain to a Worker with automatic, free SSL and
zero extra infrastructure — requires the zone to *be on* Cloudflare; it
can't bind to a domain whose DNS lives elsewhere. Since this project already
runs entirely on the free Cloudflare + Render + Neon stack, moving DNS to
Cloudflare too is the natural, zero-cost extension of the same platform
choice already made in Phase 23 — not a new vendor relationship.

This is the one step with real (if small) risk: while nameservers propagate
(usually minutes, occasionally up to 24-48h), DNS resolution for the domain
is in flux. Mitigation: the `.workers.dev` and `.onrender.com` URLs keep
working throughout and after — nothing about adding a custom domain to a
Worker removes its default route, so there's no cutover moment where the
site is only reachable at the new domain. The switch is additive until
everything is verified, and can be walked back by reverting nameservers if
truly necessary (it won't be — worst case is DNS propagation taking longer
than expected, not breakage).

## 4. What needs the founder specifically (cannot be done by this assistant)

1. ✅ **Add the zone in Cloudflare** — done. Confirmed live via the Cloudflare
   API: zone `creator-hub.co.in`, status `active`.
2. ✅ **Change nameservers at GoDaddy** — done. Zone's nameservers are
   `kara.ns.cloudflare.com`/`wells.ns.cloudflare.com`; original registrar
   correctly recorded as GoDaddy.
3. ✅ **Zone is "Active"** — confirmed.
4. ✅ **Render custom domain** (`api.creator-hub.co.in`) — also done by the
   founder, ahead of being asked; confirmed `verificationStatus: verified`
   and live via `/actuator/health`.
5. **Still open — remove the pre-existing `www` DNS record.** Cloudflare
   rejected binding `www.creator-hub.co.in` to the Worker (error 100117):
   a DNS record for `www` already exists in the zone (likely an artifact
   of GoDaddy's default parking-page setup, carried over when the zone was
   imported). This assistant's Cloudflare API token has zone-settings and
   Worker-deploy permissions but not DNS-record permissions, so it can't
   remove this itself. **To unblock:** in the Cloudflare dashboard → this
   zone → DNS → Records, delete whatever record exists for `www` → say so
   here and the Worker binding for `www.creator-hub.co.in` can be added
   immediately after. Low priority — the bare domain is the canonical URL
   either way, and `www` isn't linked from anywhere in the app yet.
6. **Google OAuth Authorized JavaScript origins** — not independently
   confirmed. Add `https://creator-hub.co.in` in Google Cloud Console (the
   existing OAuth client used for sign-in) if not already done — see
   `GOOGLE_SERVICES_SETUP.md` §1. Live-checking `/login` on the new domain
   showed the Google sign-in button rendering normally with no
   origin-mismatch console error, which suggests this may already be
   configured — but that wasn't confirmed by completing an actual sign-in
   (this assistant doesn't do that). Worth a real sign-in test on your end
   to be certain.

## 5. Phases

| Phase | Task | Depends on | Complexity | Status |
|---|---|---|---|---|
| 30 | DNS cutover to Cloudflare | Domain purchased (done) | Low | ✅ done, confirmed live via the Cloudflare API. |
| 31 | Frontend custom domain (Cloudflare Workers) | Phase 30 | Low | ✅ bare domain done and live-verified (real cert, real SSR data). `www` still blocked on a pre-existing DNS record — see §4 item 5. |
| 32 | Backend custom domain (Render) | Phase 30 | Low-Medium | ✅ done by the founder, confirmed live (`verificationStatus: verified`, real `/actuator/health` response over a valid cert). |
| 33 | Application cutover (env vars, CORS, redeploy, live verification) | Phases 31-32 | Medium | ✅ done and live-verified: new bundle contains the new backend hostname (3 occurrences, 0 of the old one); backend redeployed with the new CORS origin (130 tests green); `https://creator-hub.co.in` serves real backend-fetched content; `/login` renders cleanly with no origin-mismatch error. Full sign-in not independently completed — see §4 item 6. |
| 34 *(cleanup, optional)* | SEO/doc polish + old-URL decision | Phase 33 | Low | — not started. README/`DEPLOYMENT.md`/`ARCHITECTURE.md` primary-URL references still point at the old URLs; sitemap.xml/robots.txt/canonical/OG tags not yet re-checked against the new domain in the deployed HTML. No redirect from the old `.workers.dev` URL is planned — it costs nothing to leave it live, and the site's search-engine footprint under that URL is negligible given how young the site is. |

## 6. Loose ends worth naming now, not discovering later

- **PhonePe onboarding (Phase 20, still pending real credentials):** whichever
  live website URL eventually goes into PhonePe's merchant onboarding form
  should be the new custom domain, not a `.workers.dev` one — a one-line note
  for whenever that onboarding resumes, not an action now.
- **CORS origins are a static YAML list today**, not env-var-driven — adding
  a new one is a one-line code change + redeploy, not just a Render env var
  edit like most other config in this project. Fine for an infrequent change
  like this; worth revisiting only if the origin list starts changing often.
- **`.co.in` renewal:** GoDaddy will auto-remind at renewal time (typically
  annual for this TLD) — outside anything this codebase tracks, purely a
  founder calendar/billing matter.

## 7. What's left

Phases 30-33 are done and live-verified. Two small open items remain,
both in §4:

1. Delete the pre-existing `www` DNS record in the Cloudflare dashboard so
   `www.creator-hub.co.in` can be bound to the Worker too (low priority).
2. Do a real sign-in test on `https://creator-hub.co.in`, and if it fails
   with an origin-mismatch error, add the domain to Google OAuth's
   Authorized JavaScript origins (`GOOGLE_SERVICES_SETUP.md` §1).

Phase 34 (SEO/doc polish) is optional cleanup, not blocking anything —
pick it up whenever convenient.
