# Deployment

Both apps are live, at $0 hosting cost. This documents where, why,
and how to redeploy each one.

## Where

| App | Host | URL |
|---|---|---|
| Frontend | Cloudflare Workers | https://extremis-creator-hub.surya-chowdhury0412.workers.dev |
| Backend | Render (free tier, Docker) | https://extremis-creator-hub-backend.onrender.com |
| Database | Neon (serverless Postgres, free tier) | — |
| Source | GitHub | https://github.com/Extremis042000/Creator-Hub |

No custom domain yet — the zero-cost `*.workers.dev` subdomain is
used instead. Optional later, per `decisions/05-founder-action-checklist.md`.

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

## Backend deploy (Render)

Auto-deploy from `main` is configured but has been unreliable in
practice — always confirm a deploy actually started after pushing, and
trigger one manually via the Render API if it didn't:

```powershell
$headers = @{ Authorization = "Bearer $env:RENDER_API_KEY" }
Invoke-RestMethod -Uri "https://api.render.com/v1/services/srv-daj5veu7bikc73arlm5g/deploys" `
  -Method Post -Headers $headers -ContentType "application/json" -Body '{}'
```

Poll `GET /v1/services/{id}/deploys/{deployId}` until `status` is
`live` (or `build_failed`/`update_failed`). A deploy through the free
tier's build queue commonly takes 3-5 minutes — `update_in_progress`
for a few minutes is normal, not stuck; cross-check against wall-clock
time before assuming a hang.

Render env vars are managed the same way — through the Render
dashboard or API, never committed. See `ENV_VARS.md` for the full list
and which ones are required to boot at all vs. optional/inert-until-set.

## Frontend deploy (Cloudflare Workers)

```powershell
cd frontend
npm run deploy   # = opennextjs-cloudflare build && opennextjs-cloudflare deploy
```

This runs `wrangler deploy` under the hood using the Cloudflare
account ID + API token configured for `wrangler` (see `ENV_VARS.md`).
`NEXT_PUBLIC_*` env vars are inlined into the build at build time —
setting them after the fact requires a rebuild+redeploy, not just an
env var change.

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

## CI (GitHub Actions, `.github/workflows/ci.yml`)

Runs on every push/PR to `main`: backend `./mvnw test` (against the
real Neon database — there's no test-specific database), frontend
`npx tsc --noEmit`. CI does **not** deploy either app — deployment is
always a separate, manual step (the commands above), run from
wherever `RENDER_API_KEY`/Cloudflare credentials are available.

All backend secrets in CI are one combined GitHub secret,
`CREATOR_HUB_SECRETS` (dotenv-style, one `KEY=value` per line), parsed
into individual masked env vars inside the workflow rather than kept
as separate named secrets.

## Redeploy checklist after a backend code change

1. `cd backend && .\run-local.ps1` locally first — confirm it boots
   and the relevant endpoint works.
2. `./mvnw test` — full suite, against the real Neon DB.
3. Commit, push to `main`.
4. Confirm CI is green (`GET /repos/{owner}/{repo}/actions/runs`).
5. Trigger a Render deploy (see above) — don't assume auto-deploy fired.
6. Poll until `live`, then hit `/actuator/health` and one real endpoint
   on the production URL to confirm the new code is actually serving.

## Redeploy checklist after a frontend code change

1. `cd frontend && npm run dev` locally first, exercise the change in
   a browser.
2. `npx tsc --noEmit`.
3. Commit, push to `main` (CI typechecks; doesn't deploy).
4. `npm run deploy` from a machine where Cloudflare credentials are
   configured.
5. Hit the production URL and confirm the change is live (Cloudflare's
   edge cache/propagation is fast, but always verify rather than assume).
