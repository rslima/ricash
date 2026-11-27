# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Ricash is a personal finance management application with a Spring Boot backend API and a React + TypeScript frontend. The system implements double-entry bookkeeping with ledgers, accounts, and transactions.

## Architecture

### Backend (api/)
- **Framework**: Spring Boot 4.2.0 with Java 25
- **Database**: PostgreSQL 18.1 with Flyway migrations
- **Security**: OAuth2 Resource Server with JWT authentication (Keycloak in dev, Auth0 in prod)
- **API Format**: JSON:API standard using spring-hateoas-jsonapi
- **Build Tool**: Maven with Maven Wrapper (./mvnw)
- **Runtime**: virtual threads (`spring.threads.virtual.enabled`) for request handling and `@Scheduled` tasks; prod logs one ECS JSON object per line (`application-prod.yml`)

### Frontend (frontend/)
- **Framework**: React 19 with TypeScript 5.9, TanStack Query for server state
- **Build Tool**: Vite 7
- **Dev Server**: Vite dev server with HMR; its proxy strips the `/api` prefix, so the frontend calls `/api/v1/...` while the backend serves `/v1/...`
- **Dates**: "today" is the user's local date (`lib/dates.ts` `todayISO`), never `toISOString().slice(0, 10)`, which is the UTC date and already tomorrow in a Brazilian evening. Vitest runs the whole suite under `TZ=America/Sao_Paulo` (`src/test/global-setup.ts`) to catch that class of bug
- **Form inputs**: currency codes go through `components/CurrencyInput` (uppercased, 3 letters, as the API requires); typed money amounts parse with `lib/utils` `parseMoneyInput` (empty → `null`, rounded to cents)
- **Cache invalidation**: mutations invalidate every resource they move, by ledger-scoped key prefix: transactions refresh accounts, envelopes and portfolio positions; account changes refresh transactions and reports (they embed account names/types). All query-key factories live in `frontend/src/api/queryKeys.ts`; hooks files import keys from there, never from each other, which keeps them free of import cycles

### Domain Model
The backend follows a domain-driven structure organized by business concepts:

- **users/**: Minimal user records (`User` is just an id). There is no user endpoint or UserService; users are provisioned lazily on first ledger creation (`LedgerServiceBean.create`). There is no role-based access control — roles were dropped in migration V10 and no method security exists.

- **ledgers/**: Core financial domain
  - Each user can have multiple ledgers; every nested resource is addressed by ledger slug
  - Ledgers contain accounts organized in a tree structure (via parent_account_id); envelopes form the same kind of tree (parent_envelope_id). A parent must belong to the same ledger and may not be the node itself or one of its descendants (service check; V15 adds a not-own-parent CHECK)
  - Accounts support different types (ASSET, LIABILITY, INCOME, EXPENSE, ...) and currencies
  - Transactions use double-entry bookkeeping with transaction entries; balance is enforced per original currency in `TransactionServiceBean`
  - Balances and monthly summaries are trigger-maintained rollup tables (`account_balance_summary` from V12, `monthly_account_summary`/`envelope_monthly_summary` from V13); read queries aggregate them with a recursive account-tree CTE (shared fragments in `ledgers/accounts/AccountTreeSql`). Account-tree CTEs use `UNION`, not `UNION ALL`, so stored cycles terminate instead of recursing forever
  - Subdomains: accounts/, transactions/, envelopes/ (budget envelopes + allocations), exchangerates/ (external providers BCB PTAX + open.er-api.com; a daily scheduled refresh at 18:00 America/Sao_Paulo re-fetches every distinct pair the provider has served before — MANUAL-only pairs stay user-managed, and a MANUAL rate for the current day is never overwritten by the sweep), instruments/ (holdings, prices, portfolio positions; external price fetch by ISIN via `EodhdPriceService` (EODHD API; token bound from the `EODHD_API_TOKEN` env var to `ricash.instrument-prices.api-token`, fetches are skipped with a warning while blank) — on-demand `POST .../instrument-prices/fetch` plus a daily scheduled refresh; the ISIN is resolved through EODHD's search endpoint and only listings whose search-row `Currency` exactly matches the instrument currency are accepted, so GBX/pence listings are rejected, never converted; prices come from the EOD endpoint's raw `close`, never `adjusted_close`)
  - All monetary amounts are numeric(20, 2) with explicit currency fields (instrument quantities numeric(20, 8)); request DTOs carry matching `@Digits` limits, because Postgres would otherwise round silently after the balance check ran on unrounded values

- **configuration/**: SecurityConfiguration (OAuth2 resource server + CORS), property records (`JwtClaimProperties`, `CorsProperties`, `ExchangeRateProviderProperties`, `InstrumentPriceProviderProperties`) bound via `@ConfigurationPropertiesScan`, and `SchedulingConfiguration` — the app's only `@EnableScheduling`, registering two daily refresh schedulers (instrument prices at 06:30, exchange rates at 18:00), each gated by its own property: `ricash.instrument-prices.refresh-enabled` and `ricash.exchange-rates.refresh-enabled` (default on; both forced off for all tests in `api/src/test/resources/application.properties`). HTTP client timeouts bind under `spring.http.clients.*` (Boot 4 renamed the prefix; the old `spring.http.client.*` is silently dead config).

- **web/**: Cross-cutting web helpers — `GlobalExceptionHandler` (the single error contract), `Pages` (page-request builder with the size cap), `PagedModels` (JSON:API pagination links), `AuthenticatedUser` (user id from the JWT)

### Conventions (follow these when adding code)
- **Manual bean wiring**: services and repositories are framework-free classes registered via `@Bean` factories in `ledgers/LedgerConfiguration` and `users/UserConfiguration`. Do NOT add `@Service`/`@Component`/`@Repository` stereotypes to domain classes.
- **Layering**: Controller → Service interface + `*ServiceBean` → Repository interface + `*JdbcRepository` (JdbcClient with named params, SQL as text blocks). Controllers never touch repositories.
- **Ownership**: every caller-facing service method takes `(userId, ledgerSlug, ...)` and resolves the ledger through `ledgers/LedgerAccess.requireLedger`, a single-row lookup returning a `LedgerRef` (id, slug, currency) that throws `LedgerNotFoundException` (404) for unknown or foreign slugs. Only callers that read the account forest use `requireLedgerWithAccounts`. Repository queries for nested resources are scoped by `ledgerId`.
- **Ids in request bodies**: every id a client sends in a body (parent account/envelope, envelope or instrument on a transaction entry, accounts mapped to an envelope) must be resolved through a ledger-scoped lookup before anything is written: `AccountRepository.findRefsByIds`, `EnvelopeRepository.findIdsIn`, `InstrumentRepository.findIdsIn`. Unknown or foreign ids → `IllegalArgumentException` (400). Foreign keys only prove a row exists, not who owns it. Child-row writes (e.g. transaction entries) are scoped through their parent's `ledger_id` in SQL too.
- **Transactions**: annotate every mutating `*ServiceBean` method with `@Transactional` (multi-statement writes plus the V12/V13 triggers make partial writes corrupting). The `refresh*`/`fetch*` provider sweeps are the deliberate exception: idempotent single-statement upserts that must not hold a transaction open around HTTP calls.
- **Errors**: never add per-controller `@ExceptionHandler`s. `web/GlobalExceptionHandler` maps `EntityNotFoundException` → 404, `ConflictException` and `DataIntegrityViolationException` → 409 (generic detail, SQL only in the log), `IllegalArgumentException` → 400, bean-validation failures → 400, anything else → 500. Spring MVC's own errors (malformed JSON, missing/mistyped params, 405, ...) go through `handleExceptionInternal`. Everything renders as a JSON:API error document. New not-found/conflict exceptions extend those bases.
- **Pagination**: list endpoints keep the `page[number]`/`page[size]` `@RequestParam` pair, build the pageable with `web/Pages.of(page, size)` (caps the size at 500), and build responses with `web/PagedModels.toPagedModel(page, p -> methodOn(...).list(..., p, pageable.getPageSize()))`. Pass every filter parameter through to the link invocation.
- **Time**: services take the `Clock` bean from `LedgerConfiguration`; never call `Instant.now()`/`LocalDate.now()` without it. Exchange rates use their own America/Sao_Paulo clock.
- **IDs**: UUIDv7 via `UuidCreator.getTimeOrderedEpoch()` everywhere.
- **Requests/DTOs**: top-level records with jakarta-validation annotations, one per file (e.g. `CreateAccountRequest`, `TransactionEntryRequest` shared by create/update). Currencies use `@CurrencyCode` (three uppercase letters); requests whose service uppercases the code itself (exchange rates, instruments) use `@Pattern(regexp = CurrencyCode.ANY_CASE, message = CurrencyCode.ANY_CASE_MESSAGE)`. Names are capped at 255 characters and descriptions at 1000.
- **Enforced by `ArchitectureTest`** (ArchUnit): no stereotypes on domain classes (generated `*MapperImpl` excepted), controllers never depend on repositories/`JdbcClient`, mutating `*ServiceBean` methods are `@Transactional`, `@ExceptionHandler` only in `web/`, and the exception base classes above.

## Development Commands

### Backend
```bash
# Navigate to API directory
cd api

# Run application (starts Spring Boot and Docker Compose services)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Build
./mvnw clean package

# Build Docker image (multi-stage Dockerfile; spring-boot:build-image fails on Docker 29.x)
docker build -t ricash-api .
```

### Frontend
```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint
npm run lint
```

### Docker Services
The `api/compose.yaml` defines required services:
- **PostgreSQL**: localhost:5432 (db: ricash, user: ricash, pass: secret)
- **Keycloak**: localhost:9180 (admin/admin, realm: Ricash)

Services start automatically with `spring-boot:run` via spring-boot-docker-compose support.

## Database Migrations
- Located in `api/src/main/resources/db/migration/`
- Flyway naming convention: V{version}__{description}.sql
- Migrations run automatically on application startup
- Never edit an applied migration (checksums); add a compensating one instead (see V14)

## Testing
- Frontend: Vitest strips types without checking them, so `frontend/tsconfig.test.json` (a `tsc -b` reference) type-checks the tests; `npm run build` and CI fail on test type errors as on app code
- Unit tests: Mockito for `*ServiceBean`s and mappers
- Integration tests: `@SpringBootTest` + Testcontainers (postgres pinned to 18.1 in `TestRicashApplication`); shared raw-SQL seeding via `testsupport/DbFixtures`
- Web tests: `@WebMvcTest` + `@Import({WebTestConfiguration.class, SecurityConfiguration.class})` + `@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)` (the JSON:API converter is not part of the slice by default); authenticate requests with `WebTestConfiguration.jwtFor(userId)`
- Atomicity tests (`TransactionJdbcRepositoryTest`, `EnvelopeAccountMappingJdbcRepositoryTest`) intentionally omit `@Transactional` on the test class so rollback behavior is observable. They force a mid-write failure the services cannot pre-check (numeric overflow, a duplicate unique key)
- Cross-tenant tests: one `*OwnershipTest` per domain (accounts, envelopes, instruments, transactions). Each covers foreign ids in the URL and in request bodies, and asserts the victim's rows and trigger-maintained summaries are unchanged

## Security & Authentication
- All endpoints except GET /index.html, GET /actuator/health and the GET /actuator/health/liveness|readiness probes require authentication. Liveness ignores the database; `infra/app.tf` still health-checks `/actuator/health`, which includes it
- Dev JWTs issued by Keycloak (issuer: http://localhost:9180/realms/Ricash); prod uses Auth0 with audience validation (`application-prod.yml`)
- Stateless session management (no server-side sessions)
- Offline/PWA: no API data outlives sign-out: the service worker precaches only the app shell and never caches API responses (Workbox keys by URL, not by the `Authorization` header). `frontend/src/lib/purge-api-cache.ts` deletes the `api-cache` older builds left on devices
- Controllers obtain the user id via `web/AuthenticatedUser.userId(principal)` (JWT principal claim `preferred_username`)
- Frontend sessions: oidc-client-ts renews tokens automatically on every platform (refresh-token grant on native, which requests `offline_access` via `VITE_AUTH_SCOPE` in `.env.android`/`.env.ios`; the silent-renew iframe on web). Any API 401 goes to `frontend/src/lib/session-recovery.ts`: one silent renew, else a local sign-out (cache cleared, sign-in card, one toast). `ApiError.detail` carries the backend's JSON:API `errors[].detail`, which `useErrorHandler` shows under the localized title for 400/409

## API Conventions
- Backend base path: `/v1/` (the frontend proxy exposes it as `/api/v1/`)
- Pagination: query params `page[number]` and `page[size]` (default size 20; instrument prices default 50, category drill-downs 200; capped at 500)
- Response format: JSON:API with HATEOAS links (self/first/last/next/prev on lists)
- Error format: JSON:API error documents from `GlobalExceptionHandler` (404 not-found, 409 conflicts and constraint violations, 400 bad input/validation, 500 unexpected), including Spring MVC's own errors

## Key Technologies
- **MapStruct**: DTO mapping (configured with Lombok binding)
- **Lombok**: Boilerplate reduction (configured as annotation processor)
- **Testcontainers**: Integration tests with PostgreSQL containers
- **ESLint**: Frontend linting with TypeScript support

## Known Follow-ups (out of scope so far, confirm before "fixing")
- `EnvelopeBalance` has no currency; envelope spent/to-be-budgeted math sums across currencies for multi-currency ledgers (API-shape change to fix)
- Several report endpoints serve plain JSON DTOs (BalanceSummary, MonthlyReport, BudgetSummary, envelope-account maps) under the JSON:API media type
- Account `status` cannot be changed after creation (`UpdateAccountRequest` has no status field) — possibly intentional
- Exchange rates are global rows with no owner: any authenticated user can delete any rate (fixing needs a schema change)
- Exchange-rate sweep enrollment is permanent: once a pair has an EXTERNAL_API row it is refreshed daily forever (opt-out = delete its EXTERNAL_API rows). Each directed pair costs one er-api call per day — no batching per base currency, and inverse duplicates (USD→BRL and BRL→USD) each count. Fine at personal pair counts against er-api's free 1500 req/month; batching would need an ExternalExchangeRateService API change
- On BCB non-business days the sweep stores the generic er-api spot rate for BRL pairs (no PTAX bulletin exists that day); PTAX is never backfilled retroactively
- Production deploys are deliberately manual. The CI `deploy` jobs are gated on `refs/heads/main`, a branch that does not exist, so they never run. Do not "fix" the gate
- Transaction-list pagination links render unset optional filters as a URI template (`...{&description,year,month}`, `meta.isTemplated: true`), pinned by `PaginationLinksGoldenTest`. Expanding them (`Link.expand()` in `PagedModels`) would be a deliberate contract change
- Users are keyed by the `preferred_username` claim, which OIDC says is mutable and not guaranteed unique; `sub` is the stable identifier. Switching means migrating `users.id` and the `ledgers.user_id` references
- The frontend account form does not uppercase the currency it sends (the transaction, exchange-rate and instrument forms do), so a lowercase code there gets a 400 from `@CurrencyCode`

## Envelope rollover semantics
Budget balances are computed from one activity query (`EnvelopeAllocationRepository.findMonthlyActivityBy*`) folded in `EnvelopeServiceBean`. The semantics are pinned by `EnvelopeRolloverGoldenTest` — clamp-at-zero carry, gap months break the chain, spend-only months keep it alive, 2020-01 floor. Do not change those numbers without deciding so deliberately.
