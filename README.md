# EXTREMIS Creator Hub

Monorepo-style project with two independent apps:

```
extremis-creator-hub/
  docs/       Business blueprint, PRD, system design, UI/UX (start here)
  backend/    Java 21 + Spring Boot 4 REST API (Maven)
  frontend/   Next.js 16 + TypeScript + Tailwind CSS 4
```

Read `docs/01-business-blueprint.md` through `docs/09-phase3-ui-ux.md` in
order for the full plan. This README only covers running the code.

## Backend

Requires a JDK (21+; this machine has JDK 25, which works fine). No
separate Maven install needed — the Maven Wrapper is included.

```
cd backend
./mvnw.cmd spring-boot:run     # Windows
```

**Needs a database (Phase 5, live).** Copy `backend/.env.example` to
`backend/.env` and fill in real Neon (or other Postgres) credentials —
see that file for exactly how to map a Neon connection string into it.
`.env` is gitignored; nothing secret is committed.

```
cd backend
copy .env.example .env   # then edit .env with real values
.\run-local.ps1          # loads .env and starts the backend
```

(Plain `.\mvnw.cmd spring-boot:run` won't have the DB credentials set —
always use `run-local.ps1` locally.)

Boots on `http://localhost:8080`. Health check:
`http://localhost:8080/actuator/health` should return `{"status":"UP"}`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Frontend

**Requires Node.js (20+), which is not currently installed on this
machine.** Install it first — https://nodejs.org (LTS) or
`winget install OpenJS.NodeJS.LTS` — then:

```
cd frontend
copy .env.local.example .env.local
npm install
npm run dev
```

Boots on `http://localhost:3000`. The homepage's footer shows a small
"Backend: connected/unreachable" diagnostic (a temporary Phase 4
connectivity check, not a real feature) — it should say "connected"
whenever the backend is also running.

## Project status

See `docs/03-development-roadmap.md` for the full phase-by-phase plan
and current status. As of this commit: Project Init is done (both
apps boot; frontend reaches the backend's health endpoint). Database
migrations, the 5 tool endpoints, and everything else are not yet
implemented.
