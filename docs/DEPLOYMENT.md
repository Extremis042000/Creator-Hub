# Deployment

Both apps are live, at $0 hosting cost. This documents where, why,
and how to redeploy each one.

## Where

| App | Host | Custom domain | Underlying free-tier URL |
|---|---|---|---|
| Frontend | Cloudflare Workers | https://creator-hub.co.in (`www` 301s here) | https://extremis-creator-hub.surya-chowdhury0412.workers.dev |
| Backend | Render (free tier, Docker) | https://api.creator-hub.co.in | https://extremis-creator-hub-backend.onrender.com |
| Database | Neon (serverless Postgres, free tier) | — | — |
| Source | GitHub | — | https://github.com/Extremis042000/Creator-Hub |

`creator-hub.co.in` (founder-purchased via GoDaddy, DNS moved to
Cloudflare) is the canonical, public-facing domain as of 2026-09-26 —
see `decisions/08-custom-domain-cutover.md` for the full cutover.
The `*.workers.dev`/`*.onrender.com` URLs are the underlying
infrastructure addresses; both still answer directly and are never
disabled, since Cloudflare custom domains and Render custom domains
are additive, not a replacement for the host's own free URL. Nothing
in the app hardcodes either — both flow through the same env vars
described below (`NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_SITE_URL` on
the frontend, `extremis.cors.allowed-origins` on the backend), so a
future domain change is a config edit, not a code change.

## Why these hosts

**Not Vercel**, despite the Next.js frontend — Vercel's Hobby (free)
plan's own fair-use guidelines explicitly ban "any method of
requesting or processing payment," "advertising the sale of a
product," affiliate linking as a primary purpose, and third-party ad
scripts like AdSense. That's a direct conflict with this site's whole
business model, confirmed by reading Vercel's own docs before writing
any deploy config — not an assumption.

**Cloudflare Workers** (via the `@opennextjs/cloudflare` adapter)
instead: free, 100k requests/day, commercial use and ad monetization
explicitly allowed, no card required.

**Render** for the backend: free tier, commercial use allowed, but no
native JVM runtime — hence the Dockerfile. Known free-tier caveats:
cold start after ~15 min idle (~1 min to wake), ephemeral disk (files
written at runtime don't survive a redeploy/restart — irrelevant today
since the one test digital-product file is baked into the Docker
image, but will matter before real purchasable files are added; plan
is Cloudflare R2 or similar at that point).

**Oracle Cloud "Always Free"** was tried first for a fully
self-hosted VM and abandoned after 5 failed signup attempts (a known,
generic Oracle fraud-detection false-positive, not something fixable
from this end) — pivoted to Render+Cloudflare instead.

## Deploys are automated (as of 2026-09-26)

Every push to `main` that passes tests deploys **both** apps
automatically via GitHub Actions (`.github/workflows/ci.yml`) — see
the CI section below for exactly how. This replaced a long-standing
manual step: Render's own `autoDeploy: "yes"` setting has existed on
the service since it was created, but checking the actual deploy
history, it had **never once** actually fired — every prior deploy
across every phase was a manual API call or dashboard click. The CI
workflow now triggers deploys explicitly rather than relying on that.

**A real gotcha hit while wiring this up, worth knowing if a secret
ever needs re-setting by hand:** piping a value into `gh secret set`
from PowerShell (`Get-Content -Raw | gh secret set NAME`) silently
prepends a UTF-8 BOM to the secret — PowerShell's text-pipeline
encoding, not a `gh` or GitHub bug. A URL secret with a leading BOM
makes `curl` fail with exit code 3 ("URL malformed"), with zero useful
output since the failure happens before any request is sent. Fix:
write a plain ASCII file and redirect it into `gh`'s stdin via `cmd`
(`cmd /c "gh secret set NAME < file.txt"`), which bypasses PowerShell's
re-encoding entirely. Verify a suspect secret's exact byte shape with
`printf '%s' "${{ secrets.X }}" | od -c | head -3` in a throwaway
workflow step — it's safe because it never prints the value itself.

Manual commands remain useful for local testing or if CI is down:

**Backend (Render):**
```powershell
$headers = @{ Authorization = "Bearer $env:RENDER_API_KEY" }
Invoke-RestMethod -Uri "https://api.render.com/v1/services/srv-daj5veu7bikc73arlm5g/deploys" `
  -Method Post -Headers $headers -ContentType "application/json" -Body '{}'
```
Poll `GET /v1/services/{id}/deploys/{deployId}` until `status` is
`live` (or `build_failed`/`update_failed`). A deploy through the free
tier's build queue commonly takes 3-5 minutes.

Render env vars are managed through the Render dashboard or API, never
committed. See `ENV_VARS.md` for the full list.

**Frontend (Cloudflare Workers):**
```powershell
cd frontend
npm run deploy   # = opennextjs-cloudflare build && opennextjs-cloudflare deploy
```
Uses the Cloudflare account ID + API token configured for `wrangler`
(see `ENV_VARS.md`). `NEXT_PUBLIC_*` env vars are inlined into the
build at build time — setting them after the fact requires a
rebuild+redeploy, not just an env var change.

**Critical: `frontend/.env.production.local` must exist with real
production values before running `npm run deploy`.** `.env.local`
correctly holds `http://localhost:8080` for local dev, but Next.js
loads `.env.local` for every build mode including production unless a
more specific file overrides it. On 2026-09-14 two production deploys
were run without this file, so the live site silently baked in
`http://localhost:8080` as its backend URL — breaking login and every
backend-dependent feature for every real visitor (SSR *and*
client-side), while `git push`/CI stayed green throughout since
nothing about this is visible to `tsc` or the test suite. Root cause:
`next build`'s env precedence is `.env.production.local` >
`.env.local` > `.env`, and no `.env.production.local` existed yet.

Fix, and the permanent guard against a repeat: `.env.production.local`
(gitignored, matches `.env*.local` — recreate it if missing, e.g. on a
fresh clone or a new machine) with:
```
NEXT_PUBLIC_API_BASE_URL=https://api.creator-hub.co.in
NEXT_PUBLIC_SITE_URL=https://creator-hub.co.in
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<same value as backend's GOOGLE_CLIENT_ID>
```
(Pre-2026-09-26, before the custom domain cutover, these pointed at
the `*.workers.dev`/`*.onrender.com` URLs instead — see
`decisions/08-custom-domain-cutover.md`. Either still works today
since both remain live, but the custom domain is canonical.)
With this file present, `next build`'s own "Environments:" log line
(printed at the start of every build) will list
`.env.production.local, .env.local` — if it only lists `.env.local`,
the file is missing and the next deploy will repeat this incident.
**Always check that log line before trusting a deploy.**

**Known limitation — Windows only:** `opennextjs-cloudflare build`
does not fully work on Windows for every dependency tree. It has
succeeded reliably for this project's own dependencies, but adding
`@sentry/nextjs` broke it (see `03-development-roadmap.md` Phase 24
for the full writeup) — importing it anywhere makes Next's build
tracer pull in a dependency that needs a symlink Windows blocks
without Developer Mode enabled. If a future dependency hits the same
wall, the fix is either enabling Windows Developer Mode (Settings →
Privacy & security → For developers) or moving this build step to a
Linux CI runner (GitHub Actions), which has no such restriction.

Before trusting a build, check it actually completed — Windows/OneDrive
file locks (a running `next dev` server, or OneDrive syncing) have
caused `EPERM` errors clearing `.open-next/` mid-build; stop any local
dev server first if a build fails this way.

To check the real upload size against Cloudflare's free-tier 3 MiB
gzip Worker script limit before deploying something that adds weight:

```powershell
npx wrangler deploy --dry-run --outdir .wrangler-dryrun
```

## CI/CD (GitHub Actions, `.github/workflows/ci.yml`)

Four jobs. `backend` (`./mvnw test`, against the real Neon database —
there's no test-specific database) and `frontend` (`npx tsc --noEmit`)
run on every push/PR to `main`. On an actual push to `main` (never a
PR) that passes its tests, two more jobs deploy automatically:

- **`deploy`** (backend → Render): POSTs the Render deploy hook
  (`RENDER_DEPLOY_HOOK_URL` secret), then polls that specific deploy's
  own status (`RENDER_API_KEY` secret, `RENDER_SERVICE_ID` repo
  variable) every 15s until it reaches a terminal state, up to 10
  minutes — confirmed end-to-end at ~4 minutes in practice.
- **`deploy-frontend`** (frontend → Cloudflare Workers): writes the
  same `NEXT_PUBLIC_*` values `frontend/.env.production.local` holds
  (not secret — they're baked into the client bundle and shipped to
  every visitor regardless of where they're stored), then runs the
  same `npm run deploy` as the manual path, using
  `CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID` secrets.

Both post a Google Chat message either way (success or failure) via
an incoming webhook (`GOOGLE_CHAT_WEBHOOK_URL` secret) — status icon,
short commit SHA + subject line, and (backend) a link to the deploy on
Render's dashboard. As of 2026-09-26 that secret isn't set yet, so the
notify step runs and no-ops rather than failing the job (`|| true`).

**On hold: WhatsApp notifications via CallMeBot.** The founder's
original choice, deferred because the CallMeBot API key's delivery
(sent by messaging their bot number on WhatsApp) got delayed —
Google Chat's webhook needed no such wait and was already a real
option, so it became the active mechanism instead. Not abandoned: if
the CallMeBot key arrives, adding it is a second notify call in the
same two steps (`CALLMEBOT_PHONE`/`CALLMEBOT_APIKEY` secrets, a
`curl -G https://api.callmebot.com/whatsapp.php` call) alongside the
existing Google Chat one, not a replacement for it.

All backend app secrets are one combined GitHub secret,
`CREATOR_HUB_SECRETS` (dotenv-style, one `KEY=value` per line), parsed
into individual masked env vars inside the `backend` job rather than
kept as separate named secrets. The CI/CD infra secrets above
(Render/Cloudflare/Google Chat) are separate, individually named
secrets/variables — a different concern (deploying the app) from what
the app needs to run (`CREATOR_HUB_SECRETS`).

## Redeploy checklist after a backend code change

1. `cd backend && .\run-local.ps1` locally first — confirm it boots
   and the relevant endpoint works.
2. `./mvnw test` — full suite, against the real Neon DB.
3. Commit, push to `main` — CI now deploys automatically once tests
   pass (see CI/CD above). Watch it with `gh run watch` rather than
   assuming it succeeded.
4. **Still verify live with a real request, not just a green run.** A
   passing GitHub Actions job proves the deploy hook fired and Render
   reported `live` — it doesn't prove the new code path actually
   behaves as intended for a real caller. Hit `/actuator/health` and
   one real endpoint that exercises the change on the production URL.

## Redeploy checklist after a frontend code change

1. `cd frontend && npm run dev` locally first, exercise the change in
   a browser.
2. `npx tsc --noEmit`.
3. Commit, push to `main` — CI now builds and deploys automatically
   once `tsc` passes (see CI/CD above).
4. **Still verify live**, and critically, confirm it's showing **real
   backend data**, not just a 200 status. A static page returning 200
   proves nothing about backend connectivity (this is exactly how the
   2026-09-14 incident above went undetected for two deploys — CI was
   green both times). Check a page that does an SSR backend fetch
   (e.g. the homepage's "Hot Game Deals" section, or a tool's premium
   badge) actually shows real fetched content, and spot-check one
   client bundle chunk for the real backend hostname.
