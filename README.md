# cassier-Q API (Spring Boot)

REST backend for the cassier-Q POS app, built independently from scratch
(own schema, own endpoint contract) — a Spring Boot counterpart to the
existing Go backend at `../../Cassier-Q`.

## Tech stack

| Concern        | Choice                                                              |
|-----------------|------------------------------------------------------------------------|
| Framework       | Spring Boot 4.1 (Spring Framework 7, Java 17)                        |
| Database        | PostgreSQL 16, via `spring-boot-docker-compose` (auto-starts `compose.yaml` locally) |
| Schema          | Flyway (`src/main/resources/db/migration`) — Hibernate is `ddl-auto: validate` only |
| ORM             | Spring Data JPA / Hibernate                                          |
| Auth            | Stateless JWT access tokens (HS384, 15 min) + opaque, hashed-at-rest refresh tokens (30 days, rotated on use) |
| API docs        | springdoc-openapi + Swagger UI                                       |
| Validation      | Jakarta Bean Validation                                              |
| Build           | Maven (`./mvnw`)                                                     |

## Getting started

Prerequisites: JDK 17+, Docker (for the bundled Postgres — no manual setup
needed, Spring Boot starts/stops `compose.yaml` automatically around the app
lifecycle).

```bash
./mvnw spring-boot:run
```

That's it for local dev — no `.env` file, no manual `docker compose up`.
On boot: Postgres container starts → Flyway migrates the schema → app
listens on `:8080`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Run the test suite (also spins up Postgres via `compose.yaml`):

```bash
./mvnw test
```

## Configuration

All configurable via env vars (see `application.yml` for defaults):

| Variable | Purpose | Local default |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Postgres connection | points at the bundled `compose.yaml` Postgres |
| `JWT_SECRET` | HMAC signing key for access tokens (**must** be ≥32 bytes; generate with `openssl rand -base64 64`) | insecure dev default — override in any real deployment |
| `JWT_ACCESS_TTL_MINUTES` | Access token lifetime | 15 |
| `JWT_REFRESH_TTL_DAYS` | Refresh token lifetime | 30 |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API (mobile app dev servers, etc.) | localhost Expo dev ports |
| `PORT` | HTTP port | 8080 |

Run with `--spring.profiles.active=prod` (or `SPRING_PROFILES_ACTIVE=prod`)
in any real environment — this disables the docker-compose auto-management
(`application-prod.yml`) and expects `DATABASE_URL`/`JWT_SECRET` to be
supplied externally.

## Auth model

- `POST /api/v1/auth/register` — creates a new **store** plus its **owner**
  user in one transaction (email/password only, like the mobile app's
  registration form).
- `POST /api/v1/auth/login` — email + password → access token + refresh token.
- `POST /api/v1/auth/refresh` — exchanges a refresh token for a new access
  token; the old refresh token is revoked (rotation), so a stolen-and-reused
  refresh token invalidates the whole chain the next time the legitimate
  client tries to use it.
- `POST /api/v1/auth/logout` — revokes a refresh token.
- `GET /api/v1/auth/me` — current user profile (requires `Authorization: Bearer <accessToken>`).

Access tokens are self-contained JWTs (claims: `sub`=userId, `storeId`,
`email`, `role`) validated without a DB round trip per request — see
`JwtAuthenticationFilter`. Refresh tokens are opaque random strings; only
their SHA-256 hash is stored (`refresh_tokens.token_hash`), so a leaked DB
row can't be replayed as a live token.

Every other endpoint is store-scoped: the JWT's `storeId` claim is used to
filter all queries, so one login can never see another store's data.
Product/category writes are further restricted to `ROLE_OWNER` via
`@PreAuthorize`.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | creates store + owner |
| POST | `/api/v1/auth/login` | public | |
| POST | `/api/v1/auth/refresh` | public | rotates refresh token |
| POST | `/api/v1/auth/logout` | public | revokes refresh token |
| GET | `/api/v1/auth/me` | bearer | |
| GET/POST | `/api/v1/categories` | bearer | |
| DELETE | `/api/v1/categories/{id}` | bearer | |
| GET | `/api/v1/products` | bearer | paginated, `?search=` |
| GET | `/api/v1/products/barcode/{barcode}` | bearer | used by the mobile app's scanner |
| POST/PUT/DELETE | `/api/v1/products/**` | bearer, owner only | |
| POST | `/api/v1/orders` | bearer | decrements stock in the same transaction |
| GET | `/api/v1/orders` | bearer | paginated history |
| GET | `/api/v1/orders/{id}` | bearer | |
| POST | `/api/v1/orders/{id}/void` | bearer | restocks items, PAID → CANCELLED |
| GET | `/api/v1/reports/summary` | bearer | `?from=&to=` (ISO date, default last 7 days): gross sales, order counts, top 5 best sellers |

Full request/response shapes are in Swagger UI.

## What's deliberately out of scope

Kept lean per the initial ask (DB connection, JWT, JPA, Swagger, and "what
else is needed" — not a full feature-complete backend):

- No employee/cashier account management endpoint yet (register always
  creates an `OWNER`; adding a `CASHIER` requires an owner-only "invite
  employee" endpoint, not yet built).
- No customer CRUD endpoints (the `customers` table/entity exists and
  orders can reference one, but there's no controller for it yet).
- No refresh-token cleanup job for expired/revoked rows (fine at small
  scale; add a scheduled sweep before it matters).
