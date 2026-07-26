# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Ricash is a personal finance management application with a Spring Boot backend API and a React + TypeScript frontend. The system implements double-entry bookkeeping with ledgers, accounts, and transactions.

## Architecture

### Backend (api/)
- **Framework**: Spring Boot 4.1.0 with Java 25
- **Database**: PostgreSQL 18.1 with Flyway migrations
- **Security**: OAuth2 Resource Server with JWT authentication (Keycloak in dev, Auth0 in prod)
- **API Format**: JSON:API standard using spring-hateoas-jsonapi
- **Build Tool**: Maven with Maven Wrapper (./mvnw)

### Frontend (frontend/)
- **Framework**: React 19 with TypeScript 5.9, TanStack Query for server state
- **Build Tool**: Vite 7
- **Dev Server**: Vite dev server with HMR; its proxy strips the `/api` prefix, so the frontend calls `/api/v1/...` while the backend serves `/v1/...`

### Domain Model
The backend follows a domain-driven structure organized by business concepts:

- **users/**: Minimal user records (`User` is just an id). There is no user endpoint or UserService; users are provisioned lazily on first ledger creation (`LedgerServiceBean.create`). There is no role-based access control — roles were dropped in migration V10 and no method security exists.

- **ledgers/**: Core financial domain
  - Each user can have multiple ledgers; every nested resource is addressed by ledger slug
  - Ledgers contain accounts organized in a tree structure (via parent_account_id)
  - Accounts support different types (ASSET, LIABILITY, INCOME, EXPENSE, ...) and currencies
  - Transactions use double-entry bookkeeping with transaction entries; balance is enforced per original currency in `TransactionServiceBean`
  - Balances and monthly summaries are trigger-maintained rollup tables (`account_balance_summary` from V12, `monthly_account_summary`/`envelope_monthly_summary` from V13); read queries aggregate them with a recursive account-tree CTE (shared fragments in `ledgers/accounts/AccountTreeSql`)
  - Subdomains: accounts/, transactions/, envelopes/ (budget envelopes + allocations), exchangerates/ (with external providers), instruments/ (holdings, prices, portfolio positions; external price fetch by ISIN via `YahooFinancePriceService` — on-demand `POST .../instrument-prices/fetch` plus a daily scheduled refresh; only listings whose chart `meta.currency` exactly matches the instrument currency are accepted, so GBp/pence listings are rejected, never converted)
  - All monetary amounts are numeric(20, 2) with explicit currency fields

- **configuration/**: SecurityConfiguration (OAuth2 resource server + CORS), property records (`JwtClaimProperties`, `CorsProperties`, `ExchangeRateProviderProperties`, `InstrumentPriceProviderProperties`) bound via `@ConfigurationPropertiesScan`, and `SchedulingConfiguration` — the app's only `@EnableScheduling`, gated by `ricash.instrument-prices.refresh-enabled` (default on; forced off for all tests in `api/src/test/resources/application.properties`). HTTP client timeouts bind under `spring.http.clients.*` (Boot 4 renamed the prefix; the old `spring.http.client.*` is silently dead config).

- **web/**: Cross-cutting web helpers — `GlobalExceptionHandler` (the single error contract), `PagedModels` (JSON:API pagination links), `AuthenticatedUser` (user id from the JWT)

### Conventions (follow these when adding code)
- **Manual bean wiring**: services and repositories are framework-free classes registered via `@Bean` factories in `ledgers/LedgerConfiguration` and `users/UserConfiguration`. Do NOT add `@Service`/`@Component`/`@Repository` stereotypes to domain classes.
- **Layering**: Controller → Service interface + `*ServiceBean` → Repository interface + `*JdbcRepository` (JdbcClient with named params, SQL as text blocks). Controllers never touch repositories.
- **Ownership**: every caller-facing service method takes `(userId, ledgerSlug, ...)` and resolves the ledger through `ledgers/LedgerAccess.requireLedger`, which throws `LedgerNotFoundException` (404) for unknown or foreign slugs. Repository queries for nested resources are scoped by `ledgerId`.
- **Transactions**: annotate every mutating `*ServiceBean` method with `@Transactional` (multi-statement writes plus the V12/V13 triggers make partial writes corrupting).
- **Errors**: never add per-controller `@ExceptionHandler`s. `web/GlobalExceptionHandler` maps `EntityNotFoundException` → 404, `ConflictException` → 409, `IllegalArgumentException` → 400, bean-validation failures → 400, anything else → 500, all as JSON:API error documents. New not-found/conflict exceptions extend those bases.
- **Pagination**: list endpoints keep the `page[number]`/`page[size]` `@RequestParam` pair and build responses with `web/PagedModels.toPagedModel(page, p -> methodOn(...).list(..., p, size))`.
- **IDs**: UUIDv7 via `UuidCreator.getTimeOrderedEpoch()` everywhere.
- **Requests/DTOs**: top-level records with jakarta-validation annotations, one per file (e.g. `CreateAccountRequest`, `TransactionEntryRequest` shared by create/update).

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

# Build Docker image
./mvnw spring-boot:build-image
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
- Unit tests: Mockito for `*ServiceBean`s and mappers
- Integration tests: `@SpringBootTest` + Testcontainers (postgres pinned to 18.1 in `TestRicashApplication`); shared raw-SQL seeding via `testsupport/DbFixtures`
- Web tests: `@WebMvcTest` + `@Import({WebTestConfiguration.class, SecurityConfiguration.class})` + `@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)` (the JSON:API converter is not part of the slice by default); authenticate requests with `WebTestConfiguration.jwtFor(userId)`
- Atomicity/ownership tests intentionally omit `@Transactional` on the test class so rollback behavior is observable

## Security & Authentication
- All endpoints except GET /index.html and GET /actuator/health require authentication
- Dev JWTs issued by Keycloak (issuer: http://localhost:9180/realms/Ricash); prod uses Auth0 with audience validation (`application-prod.yml`)
- Stateless session management (no server-side sessions)
- Controllers obtain the user id via `web/AuthenticatedUser.userId(principal)` (JWT principal claim `preferred_username`)

## API Conventions
- Backend base path: `/v1/` (the frontend proxy exposes it as `/api/v1/`)
- Pagination: query params `page[number]` and `page[size]` (default size 20; instrument prices default 50, category drill-downs 200)
- Response format: JSON:API with HATEOAS links (self/first/last/next/prev on lists)
- Error format: JSON:API error documents from `GlobalExceptionHandler` (404 not-found, 409 conflicts, 400 bad input/validation, 500 unexpected)

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

## Envelope rollover semantics
Budget balances are computed from one activity query (`EnvelopeAllocationRepository.findMonthlyActivityBy*`) folded in `EnvelopeServiceBean`. The semantics are pinned by `EnvelopeRolloverGoldenTest` — clamp-at-zero carry, gap months break the chain, spend-only months keep it alive, 2020-01 floor. Do not change those numbers without deciding so deliberately.
