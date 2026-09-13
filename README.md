# EXTREMIS Creator Hub

Free gaming tools (KD calculator, sensitivity converters, YouTube
title/description generators) plus a digital-product store, affiliate
gear page, display ads, and premium entitlements — the monetization
layer for the EXTREMIS Plays gaming brand.

```
extremis-creator-hub/
  docs/       Business blueprint, PRD, system design, and this doc set
  backend/    Java 21 + Spring Boot 4 REST API (Maven)
  frontend/   Next.js 16 + TypeScript + Tailwind CSS 4
```

Start with `docs/README.md` for the full doc index. This README only
covers running the code locally.

## Live

- Frontend: https://extremis-creator-hub.surya-chowdhury0412.workers.dev
- Backend: https://extremis-creator-hub-backend.onrender.com
- API docs (Swagger UI): https://extremis-creator-hub-backend.onrender.com/swagger-ui.html

See `docs/DEPLOYMENT.md` for how these are hosted and how to redeploy.

## Backend

Requires a JDK 21+. No separate Maven install needed — the Maven
Wrapper is included.

```
cd backend
copy .env.example .env   # then fill in real values, see that file
.\run-local.ps1          # loads .env and starts the backend (Windows)
```

Don't call `mvnw spring-boot:run` directly — it won't have `.env`
loaded and will fail fast on a missing `JWT_SECRET`. If PowerShell
blocks the script (`running scripts is disabled`), either run
`powershell -ExecutionPolicy Bypass -File .\run-local.ps1` once, or fix
it permanently with `Set-ExecutionPolicy -Scope CurrentUser
RemoteSigned` (safe, standard for a dev machine, no admin needed).

Boots on `http://localhost:8080`. Health check:
`http://localhost:8080/actuator/health` should return `{"status":"UP"}`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

Full env var reference: `docs/ENV_VARS.md`.

## Frontend

Requires Node.js 20+.

```
cd frontend
copy .env.local.example .env.local
npm install
npm run dev
```

Boots on `http://localhost:3000`, calling the local backend at
`http://localhost:8080` by default.

## Project status

See `docs/decisions/03-development-roadmap.md` for the full phase-by-
phase plan and current status. All 5 MVP tools, authentication,
affiliate links, a digital-product store (test-mode purchases),
display ads, premium entitlements, and production hosting are live.
Real payment processing is wired but disabled pending the founder's
Cashfree KYC approval.
