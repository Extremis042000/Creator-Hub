# Custom Domain Cutover — Ideation & Phases

Status: **proposed, not started.** Founder has purchased `creator-hub.co.in`
via GoDaddy (2026-09-22) — this was flagged as the one non-$0 item on the
founder action checklist (`decisions/05-founder-action-checklist.md`), and is
now done. This doc plans wiring it up to replace the `*.workers.dev` /
`*.onrender.com` URLs as the site's public address.

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

1. **Add the zone in Cloudflare.** Log into the Cloudflare dashboard already
   used for this project → "Add a Site" → enter `creator-hub.co.in` → free
   plan → Cloudflare gives two nameservers (e.g. `xxx.ns.cloudflare.com`).
2. **Change nameservers at GoDaddy.** Log into GoDaddy → this domain's DNS/
   nameserver settings → switch from GoDaddy's default nameservers to the
   two Cloudflare gave in step 1 → save.
3. **Wait for the zone to go "Active"** in the Cloudflare dashboard
   (Cloudflare emails when this happens; usually well under an hour for
   .co.in via GoDaddy, though it can occasionally take up to 24-48h).
4. **Add the new domain to Google OAuth's Authorized JavaScript origins**
   (Google Cloud Console → the existing OAuth client used for sign-in) —
   add `https://creator-hub.co.in`. Sign-in will fail with an origin-mismatch
   error on the new domain until this is done; see
   `GOOGLE_SERVICES_SETUP.md` §1 for exactly where this setting lives.

Everything else below — DNS records for the subdomains, binding the Worker
and the Render service to their domains, env var/CORS updates, redeploys,
and live verification — is buildable without further founder involvement,
using the Cloudflare API token and Render API key already in use this
session (their current scopes cover Worker deploys and Render deploys; if
either turns out to lack a permission needed for a specific step below —
e.g. DNS record management — that will be called out at the time rather
than assumed).

## 5. Phases

| Phase | Task | Depends on | Complexity | Completion criteria |
|---|---|---|---|---|
| 30 | DNS cutover to Cloudflare | Domain purchased (done) | Low | Cloudflare shows the zone "Active"; the domain's nameservers resolve to Cloudflare's. Founder-gated (§4 steps 1-3). |
| 31 | Frontend custom domain (Cloudflare Workers) | Phase 30 | Low | `wrangler.jsonc` gets a `routes` entry binding `creator-hub.co.in/*` (and `www...../*`) with `custom_domain: true`; a Cloudflare redirect rule sends `www` → bare domain; `https://creator-hub.co.in` serves the live site over a valid Cloudflare-issued cert. |
| 32 | Backend custom domain (Render) | Phase 30 | Low-Medium | `api.creator-hub.co.in` added as a Render custom domain, CNAME'd through Cloudflare (proxied), zone SSL/TLS mode set to Full (strict) so the proxy-to-origin hop stays encrypted and cert-valid; `https://api.creator-hub.co.in/actuator/health` returns `{"status":"UP"}` with no cert warning. |
| 33 | Application cutover (env vars, CORS, redeploy, live verification) | Phases 31-32, founder's OAuth origin update (§4 step 4) | Medium | `frontend/.env.production.local` updated (`NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_SITE_URL` → the new domains) and rebuilt+redeployed; backend `extremis.cors.allowed-origins` gains the new origin(s) (old `.workers.dev` origin kept as a transitional fallback, not removed yet) and redeployed; sign-in, Hot Game Deals SSR data, the AI tools, and the admin panel all verified working on `https://creator-hub.co.in` with real data — the same "a 200 isn't enough" discipline from the 2026-09-14 incident writeup in `DEPLOYMENT.md`. |
| 34 *(cleanup, optional)* | SEO/doc polish + old-URL decision | Phase 33 | Low | README/`DEPLOYMENT.md`/`ARCHITECTURE.md` primary-URL references updated to the new domain (old URLs kept noted as the underlying infra addresses, not deleted from history); sitemap.xml/robots.txt/canonical/OG tags confirmed (not just assumed) to already reflect `NEXT_PUBLIC_SITE_URL` in the deployed HTML; founder decides whether to also re-verify the new domain in Google Search Console and resubmit the sitemap (optional, doesn't block anything). No redirect from the old `.workers.dev` URL is planned — it costs nothing to leave it live, and the site's search-engine footprint under that URL is negligible given how young the site is, so a redirect would add risk for no real benefit. |

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

## 7. What's needed from the founder before Phase 30 can start

Nothing beyond what's already true: the domain is purchased, and the
founder has (or can get) access to both the Cloudflare dashboard and the
GoDaddy account for this domain. Concretely, to kick this off:

1. Confirm the target shape in §2 (bare domain canonical, `www` redirects,
   `api.` subdomain for the backend) — or say what to change.
2. Do §4 steps 1-2 (add the Cloudflare zone, switch GoDaddy's nameservers)
   whenever convenient, and say so here — everything from Phase 31 onward
   can proceed once the zone is active.
3. When Phase 33 is reached, do §4 step 4 (Google OAuth origin) before
   asking for that phase's live verification, since sign-in on the new
   domain depends on it.
