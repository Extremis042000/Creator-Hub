# Environment Variables

Full reference, both apps. Local: `backend/.env` /
`frontend/.env.local` (both gitignored — copy from the `.example`
file next to each). Production: set directly in Render's/Cloudflare's
own env var store, never committed.

## Backend (`backend/.env`, Render)

| Variable | Required? | Default | Purpose |
|---|---|---|---|
| `DATABASE_URL` | **Required, no default** | — | JDBC connection string (`jdbc:postgresql://...`) |
| `DATABASE_USERNAME` | **Required** | — | Neon DB username |
| `DATABASE_PASSWORD` | **Required** | — | Neon DB password |
| `JWT_SECRET` | **Required, fails fast** | — | Signs the app's own session JWTs. 32+ chars. Rotating it signs everyone out. |
| `DOWNLOAD_SIGNING_SECRET` | **Required, fails fast** | — | Signs digital-product download tokens. Deliberately separate from `JWT_SECRET`. |
| `GOOGLE_CLIENT_ID` | Optional | blank | Google OAuth Client ID. Blank = sign-in reports "not verified"; nothing else depends on it. |
| `ADMIN_EMAILS` | Optional | blank | Comma-separated bootstrap admin allowlist. Blank = no one has admin access. |
| `SECURE_FILES_DIR` | Optional | `secure-files` | Where purchased product files live on disk (never a public path). |
| `TEST_PURCHASES_ENABLED` | Optional | `true` | Kill switch for Phase 19's free test-mode purchases. **Set to `false` once real payments (PhonePe) are live.** |
| `PHONEPE_CLIENT_ID` / `PHONEPE_CLIENT_SECRET` | Optional | blank | Both required to activate real payments; either blank keeps checkout returning "not set up yet." |
| `PHONEPE_CLIENT_VERSION` | Optional | blank | PhonePe's own client-version identifier, issued alongside the client id/secret — required together with them (see above). |
| `PHONEPE_SANDBOX` | Optional | `true` | Set to `false` only when ready to accept real money. |
| `PHONEPE_WEBHOOK_USERNAME` / `PHONEPE_WEBHOOK_PASSWORD` | Optional | blank | Set together in the PhonePe Business Dashboard when configuring the webhook URL there — also required for real checkout to activate (see `PaymentProviderConfig`). |
| `SENTRY_DSN` | Optional | blank | Error tracking. Blank = Sentry SDK fully inert (verified: zero log output, zero behavior change). |
| `SENTRY_ENVIRONMENT` | Optional | `development` | Set to `production` on Render. |
| `SENTRY_TRACES_SAMPLE_RATE` | Optional | `0.1` | Lower (or `0`) if the free Sentry event quota gets tight. |
| `PORT` | Optional | `8080` | Render assigns this dynamically; local dev doesn't need to set it. |

## Frontend (`frontend/.env.local`, Cloudflare)

`NEXT_PUBLIC_*` vars are inlined at **build time** — changing one in
production requires a rebuild+redeploy (`npm run deploy`), not just an
env var edit.

| Variable | Required? | Default | Purpose |
|---|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Optional | `http://localhost:8080` | Backend base URL. Set to the Render URL for production builds. |
| `NEXT_PUBLIC_SITE_URL` | Optional | `http://localhost:3000` | Canonical URL, sitemap.xml, OG/Twitter meta. |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | Optional | unset | Google Analytics. Unset = analytics code no-ops entirely. |
| `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Optional | unset | Must match the backend's `GOOGLE_CLIENT_ID`. Unset = login page shows "not configured yet." |
| `NEXT_PUBLIC_ADSENSE_PUBLISHER_ID` | Optional | unset | AdSense. Unset = every ad slot renders nothing, zero Core Web Vitals impact. Also needs the `display-ads` feature flag on (admin panel) — two independent switches. |

## Deployment credentials (not app config — used to deploy, not by the app itself)

These aren't read by either app at runtime; they're what the deploy
tooling needs. Never commit them.

| Where used | What |
|---|---|
| GitHub Actions | One combined secret `CREATOR_HUB_SECRETS` (dotenv-style blob of everything in the backend table above) |
| Render API | A Render API key, for triggering deploys / managing env vars via `api.render.com` |
| Cloudflare / `wrangler` | An Account ID + API Token, for `wrangler deploy` |
| Git push | A GitHub Personal Access Token, passed inline in the push URL, never written to `.git/config` |

## General rule

Every optional var defaults to "feature fully inert" when unset — see
`ARCHITECTURE.md`'s dual-switch pattern. Nothing in this app should
ever *require* an optional var to boot or to serve its core free
functionality.
