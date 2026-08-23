# Atlas

Atlas is a personal goal, roadmap, task, scheduling, execution, and self-reflection system. It converts what a person wants to accomplish into what they actually do today, keeps that plan realistic as real life interferes, and gives an honest picture of what actually happened over time.

Full product behavior is defined in [`docs/product/atlas-master-product-spec-v1.md`](docs/product/atlas-master-product-spec-v1.md) — that document is the source of truth and should not be contradicted by anything built here.

> **Status:** environment setup only. No product features are implemented yet. See `docs/engineering/18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md` for the phased build plan.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.x |
| Database | MySQL (Aiven), Flyway migrations |
| Frontend | React, TypeScript, Vite, Tailwind CSS v4 |
| CI | GitHub Actions |

Stack is fixed by the Master Product Specification §2 (Sprint 0 row) and `docs/engineering/13_DATABASE_SPECIFICATION.md` §5.

## Repository structure

```
atlas/
├── backend/    Spring Boot API (Java 17)
├── frontend/   React + TypeScript + Vite app
├── docs/       Authoritative project documentation
│   ├── product/       Master Product Specification (source of truth for behavior)
│   ├── engineering/    Technical design documents (domain model, scheduling, API, DB, security, etc.)
│   └── jira/           Project conventions — epics, backlog, sprint plan, workflow
└── .github/workflows/  CI pipeline
```

## Prerequisites

- **Java 17** (Temurin distribution recommended) + **Maven**
- **Node.js 20+** and npm
- A MySQL database to connect to (Aiven, or any local MySQL instance) — not required just to build/compile, only to actually run the backend against real data

## Getting started

### Backend

```bash
cd backend
cp ../.env.example ../.env   # then fill in DB_URL / DB_USERNAME / DB_PASSWORD
mvn spring-boot:run
```

The backend reads its configuration from environment variables (see `.env.example`). Visiting `http://localhost:8080/api/health` should return `{"status":"ok"}` once it's running.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Visit `http://localhost:5173`. In dev mode, requests to `/api/*` are proxied to the backend on port 8080.

## Branching convention

- `main` — always deployable
- `develop` — integration branch
- `feature/ATLAS-<key>-<short-slug>` — one branch per Jira story
- `fix/ATLAS-<key>-<short-slug>` — bug fixes

Full convention: `docs/jira/JIRA_WORKFLOW_AND_CONVENTIONS.md` §6.

## Documentation map

- **Product behavior:** `docs/product/atlas-master-product-spec-v1.md` (frozen source of truth)
- **Technical design:** `docs/engineering/` (domain model, scheduling engine, API spec, database spec, security, etc. — numbered files, each authoritative for its own area)
- **Project conventions:** `docs/jira/` (epics, backlog, sprint plan, Jira workflow setup)

## Next step

Environment setup (this step) is complete once CI is green on this scaffold. Implementation begins at `FOUND-001`'s successor stories, following the phased plan in `docs/engineering/18_IMPLEMENTATION_PLAN_AND_DEFINITION_OF_DONE.md`.
