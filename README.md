# Ricash

Personal finance manager built on double-entry bookkeeping. Ricash tracks ledgers, hierarchical accounts, balanced multi-currency transactions, envelope budgets, exchange rates and investment portfolios, with a Spring Boot JSON:API backend and a React single-page app that also ships as a PWA and as native Android/iOS apps via Capacitor.

## Repository layout

| Path | What it is |
|------|------------|
| `api/` | Spring Boot 4.1 / Java 25 REST API (Maven, Flyway, PostgreSQL 18) |
| `frontend/` | React 19 + TypeScript SPA (Vite 7, TanStack Query, Tailwind 4, shadcn-style Radix UI, Recharts, i18next) |
| `frontend/android`, `frontend/ios` | Capacitor native shells around the same web build |
| `infra/` | Terraform for the production stack (DigitalOcean App Platform, managed Postgres, container registry, Auth0) |
| `docs/` | Longer-form docs, e.g. `frontend-walkthrough.md` |
| `.github/workflows/` | CI for backend and frontend, plus deploy jobs |
| `pom.xml` | Aggregator that builds `api` and `frontend` together (`./mvnw` at the root) |

## Features

- **Ledgers**: one user can own several ledgers; every nested resource is addressed by ledger slug.
- **Accounts**: tree-structured chart of accounts with types (asset, liability, income, expense, ...), per-account currency, and trigger-maintained balance and monthly rollups.
- **Transactions**: double-entry with multiple entries per transaction. Debits and credits must balance per original currency. Includes description autocomplete, templates and export.
- **Envelope budgeting**: envelopes mapped to accounts, monthly allocations, rollover with clamp-at-zero carry, budget summary and to-be-budgeted math.
- **Reports**: balance summary, monthly report, income and expense breakdowns, category drill-downs.
- **Exchange rates**: manual rates plus external providers (BCB PTAX for BRL, open.er-api.com otherwise) and a daily refresh sweep.
- **Instruments and portfolio**: holdings, historical prices, portfolio positions, on-demand and daily price fetch by ISIN from EODHD.
- **Auth**: OpenID Connect login in the frontend, JWT resource server in the backend. Keycloak in development, Auth0 in production.

## Prerequisites

- Java 25 (Temurin recommended)
- Docker with Compose (Postgres and Keycloak are started for you in dev)
- Node.js 22 or newer for working on the frontend directly (the Maven build downloads its own Node)

## Running locally

### 1. Backend

```bash
cd api
./mvnw spring-boot:run
```

Spring Boot Docker Compose support brings up the services declared in `api/compose.yaml`:

| Service | Address | Credentials |
|---------|---------|-------------|
| PostgreSQL 18.1 | `localhost:5432`, database `ricash` | `ricash` / `secret` |
| Keycloak 26.5 | `http://localhost:9180` | admin console `admin` / `admin` |

Flyway migrations in `api/src/main/resources/db/migration` run on startup. The API listens on `http://localhost:8080` under the `/v1` base path.

**First run only: create the Keycloak realm.** The Keycloak container starts with just the `master` realm and no import, so the API fails to boot with "Unable to resolve the Configuration with the provided Issuer" until a realm named `Ricash` exists. Create it once per container, either in the admin console at `http://localhost:9180` or through the admin REST API:

1. Create a realm called `Ricash`.
2. Add a public client with client id `ricash-frontend`, valid redirect URIs `http://localhost:5173/*` and web origins `http://localhost:5173`.
3. Add a user with a password, email verified, and no required actions.

The realm persists across `docker stop` and `docker start`, but not across removing the container.

Optional environment variables for the backend in dev:

| Variable | Purpose |
|----------|---------|
| `EODHD_API_TOKEN` | Enables instrument price fetching from EODHD. When blank, fetches are skipped with a warning. |

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite dev server proxies `/api/*` to the backend on port 8080 and strips the `/api` prefix, so the app calls `/api/v1/...` and the backend serves `/v1/...`. In production a reverse proxy does the same rewrite.

Frontend configuration lives in committed `.env` files, read by Vite at build time:

| Variable | Dev default |
|----------|-------------|
| `VITE_AUTH_AUTHORITY` | `http://localhost:9180/realms/Ricash` |
| `VITE_AUTH_CLIENT_ID` | `ricash-frontend` |
| `VITE_AUTH_AUDIENCE` | empty in dev, Auth0 API audience in prod |
| `VITE_API_BASE_URL` | `/api/v1` |

`.env.android` and `.env.ios` override the authority with the emulator host `10.0.2.2`. `.env.production` is filled from CI secrets.

### 3. Whole project through Maven

The root aggregator builds and tests both modules. The frontend module uses `frontend-maven-plugin` to install Node, run `npm ci`, build and run Vitest.

```bash
./mvnw clean verify
```

## Everyday commands

### Backend (`api/`)

```bash
./mvnw test                     # unit + integration tests (Testcontainers Postgres)
./mvnw clean package            # build the jar
./mvnw spring-boot:build-image  # buildpack image
docker build -t ricash-api .    # multi-stage Dockerfile alternative
```

### Frontend (`frontend/`)

```bash
npm run dev            # Vite dev server with HMR
npm run build          # type-check and production bundle into dist/
npm run preview        # serve the production bundle
npm run lint           # ESLint
npm run test           # Vitest in watch mode
npm run test:run       # Vitest single run
npm run test:coverage  # Vitest with V8 coverage
```

### Native apps

The Capacitor projects wrap the `dist/` build. After `npm run build`, sync and open the platform project with the Capacitor CLI:

```bash
npx cap sync
npx cap open android   # or ios
```

## API overview

All endpoints require a bearer JWT except `GET /index.html` and `GET /actuator/health`. Responses follow JSON:API with HATEOAS links. Errors are JSON:API error documents: 400 for invalid input, 404 for unknown or foreign resources, 409 for conflicts, 500 otherwise.

| Resource | Base path |
|----------|-----------|
| Ledgers | `/v1/ledgers`, `/v1/ledgers/{slug}` |
| Accounts | `/v1/ledgers/{ledgerSlug}/accounts` (+ `/balance-summary`) |
| Transactions | `/v1/ledgers/{ledgerSlug}/transactions` (+ `/descriptions`, `/templates`, `/export`, `/monthly-report`, `/monthly-expense-breakdown`, `/monthly-income-breakdown`, `/category-transactions`) |
| Envelopes | `/v1/ledgers/{ledgerSlug}/envelopes` (+ `/{id}/allocations`, `/{id}/balance`, `/{id}/accounts`) |
| Budget | `/v1/ledgers/{ledgerSlug}/budget`, `/v1/ledgers/{ledgerSlug}/envelope-mappings` |
| Instruments | `/v1/ledgers/{ledgerSlug}/instruments` (+ `/all`) |
| Instrument prices | `/v1/ledgers/{ledgerSlug}/instrument-prices` (+ `POST /fetch`) |
| Portfolio | `/v1/ledgers/{ledgerSlug}/portfolio` |
| Exchange rates | `/v1/exchange-rates` (global, not per ledger) |

List endpoints paginate with `page[number]` and `page[size]` (default size 20). Ownership is enforced by resolving the ledger slug against the authenticated user, so another user's ledger looks like a 404.

## Architecture notes

- **Backend layering**: Controller → Service interface + `*ServiceBean` → Repository interface + `*JdbcRepository` using `JdbcClient` with SQL text blocks. Beans are wired manually in `LedgerConfiguration` and `UserConfiguration` rather than with stereotypes.
- **Data integrity**: balances and monthly summaries are rollup tables maintained by database triggers, so every mutating service method runs in a transaction.
- **Users**: a user record is just an id from the JWT `preferred_username` claim, created lazily on first ledger creation. There are no roles.
- **Scheduling**: two daily jobs, instrument prices at 06:30 and exchange rates at 18:00 (America/Sao_Paulo), each toggled by `ricash.instrument-prices.refresh-enabled` and `ricash.exchange-rates.refresh-enabled`. Both are off in tests.
- **Frontend**: pages under `src/pages`, API clients under `src/api`, TanStack Query for server state, `react-oidc-context` for login, React Router 8, i18next for translations. `docs/frontend-walkthrough.md` is a guided tour of the code.
- **Testing**: Mockito unit tests for services and mappers, `@SpringBootTest` with Testcontainers for integration tests, `@WebMvcTest` slices for controllers. Frontend pages have Vitest + Testing Library specs next to them.

## Deployment

CI runs on pushes and pull requests to `develop` and `master`, scoped by path so backend and frontend changes trigger their own workflow. The backend workflow runs `./mvnw verify` and packages the jar. The frontend workflow lints, builds and runs Vitest.

The deploy jobs build a Docker image, push it to the DigitalOcean container registry and trigger an App Platform deployment. The infrastructure itself is described in `infra/` with Terraform: copy `terraform.tfvars.example`, fill in the DigitalOcean and Auth0 credentials, and apply.

Production configuration for the API comes from `application-prod.yml` and these environment variables:

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Managed Postgres connection |
| `AUTH0_ISSUER_URI`, `AUTH0_AUDIENCE` | JWT issuer and required audience |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins |
| `EODHD_API_TOKEN` | Instrument price provider token |

## Contributing

Work happens on `develop`. Read `CLAUDE.md` for the coding conventions the codebase follows (manual bean wiring, layering, ownership checks, error handling, pagination, UUIDv7 ids) and the list of known follow-ups. Never edit an applied Flyway migration; add a compensating one instead.
