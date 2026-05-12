# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ricash is a personal finance management application with a Spring Boot backend API and a React + TypeScript frontend. The system implements double-entry bookkeeping with ledgers, accounts, and transactions.

## Architecture

### Backend (api/)
- **Framework**: Spring Boot 4.0.6 with Java 25
- **Database**: PostgreSQL 18.1 with Flyway migrations
- **Security**: OAuth2 Resource Server with JWT authentication via Keycloak
- **API Format**: JSON:API standard using spring-hateoas-jsonapi
- **Build Tool**: Maven with Maven Wrapper (./mvnw)

### Frontend (frontend/)
- **Framework**: React 19 with TypeScript 5.9
- **Build Tool**: Vite 7
- **Dev Server**: Vite dev server with HMR

### Domain Model
The backend follows a domain-driven structure organized by business concepts:

- **users/**: User management with role-based access control
  - Users have roles and own ledgers
  - Authentication handled via Keycloak JWT tokens
  - Principal extracted from JWT claim `preferred_username`
  - Roles extracted from JWT claim `realm_access/roles`

- **ledgers/**: Core financial domain
  - Each user can have multiple ledgers
  - Ledgers contain accounts organized in a tree structure (via parent_account_id)
  - Accounts support different types (ASSET, LIABILITY, etc.) and currencies
  - Transactions use double-entry bookkeeping with transaction entries
  - All monetary amounts are numeric(20, 2) with explicit currency fields

- **configuration/**: Cross-cutting concerns
  - SecurityConfiguration: OAuth2 resource server setup
  - JsonApiConfig: JSON:API serialization customization

### Data Access Pattern
- Repository layer: JDBC-based repositories (e.g., UserJdbcRepository, LedgerJdbcRepository)
- Service layer: Business logic beans (e.g., UserServiceBean, LedgerServiceBean)
- Controller layer: REST endpoints returning JSON:API responses with HATEOAS links
- Resources: DTOs for API responses (e.g., UserResource, LedgerResource)

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

## Security & Authentication
- All endpoints except GET /index.html require authentication
- JWT tokens issued by Keycloak (issuer: http://localhost:9180/realms/Ricash)
- Stateless session management (no server-side sessions)
- Controllers extract user ID from JwtAuthenticationToken.getName()

## API Conventions
- Base path: `/api/v1/`
- Pagination: Query params `page[number]` and `page[size]` (default: page 0, size 20)
- Response format: JSON:API with HATEOAS links
- Error handling: Custom @ExceptionHandler methods return JsonApiErrors

## Key Technologies
- **MapStruct**: DTO mapping (configured with Lombok binding)
- **Lombok**: Boilerplate reduction (configured as annotation processor)
- **Testcontainers**: Integration tests with PostgreSQL containers
- **Vavr**: Functional programming utilities
- **ESLint**: Frontend linting with TypeScript support
