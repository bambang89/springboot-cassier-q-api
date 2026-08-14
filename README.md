# cassier-Q API (Spring Boot)

REST backend for the cassier-Q POS app — a Spring Boot client of the **same
SQL Server database** the sibling Go backend (`../../Cassier-Q`) already
owns and manages. This project does not own the schema: `stores`, `users`,
`roles`, `products`, `sales_transactions`, and 25+ other tables already
exist there, seeded with real store/employee/role data. We only ever read
that schema and additively migrate two tables of our own (`refresh_tokens`,
`password_reset_tokens`) for JWT auth — see "Auth model" below.

**Migration status:** only the **Auth** module (register/login/me/change
password/forgot password/reset password) has been ported onto the real
schema so far. Catalog (products/categories), Sales (orders), and Reports
are not — their old code (built against a different, self-owned schema)
has been moved out to [`deferred-phase2/`](deferred-phase2/) at the repo
root (not compiled) until they're remapped onto `products`,
`sales_transactions`, `sales_transaction_items`, `payments`, `inventories`,
etc. See "What's deliberately out of scope" below.

## Tech stack

| Concern        | Choice                                                              |
|-----------------|------------------------------------------------------------------------|
| Framework       | Spring Boot 4.1 (Spring Framework 7, Java 17)                        |
| Database        | SQL Server (Azure SQL Edge locally), **pre-existing external instance** — not managed by this project, no docker-compose auto-start |
| Schema          | Owned by the Go backend; Flyway (`src/main/resources/db/migration`) only baselines it and additively migrates our own auth tables. Hibernate is `ddl-auto: validate` only |
| ORM             | Spring Data JPA / Hibernate                                          |
| Auth            | Stateless JWT access tokens (HS384, 15 min) + opaque, hashed-at-rest refresh tokens (30 days, rotated on use); roles come from the real `roles`/`user_roles` RBAC tables |
| API docs        | springdoc-openapi + Swagger UI                                       |
| Validation      | Jakarta Bean Validation                                              |
| Build           | Maven (`./mvnw`)                                                     |

## Getting started

Prerequisites: JDK 17+, and a **running SQL Server instance** reachable at
the configured `DATABASE_URL` (locally: Azure SQL Edge in Docker, container
name `azuresqledge`, `127.0.0.1:1433`, database `cassierQ`) — this project
does **not** start that container for you; start/attach to it yourself
before running.

```bash
./mvnw spring-boot:run
```

On boot: Flyway baselines the existing schema as version 1 (no-op — it's
already there) → applies our own `V2__...` migration (creates
`refresh_tokens`/`password_reset_tokens` if missing) → app listens on
`:8080`.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Run the test suite:

```bash
./mvnw test
```

## Configuration

All configurable via env vars (see `application.yml` for defaults):

| Variable | Purpose | Local default |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | SQL Server connection | `jdbc:sqlserver://127.0.0.1:1433;databaseName=cassierQ;encrypt=true;trustServerCertificate=true` / `sa` / (see `application.yml`) — dev-only credentials, override for any real deployment |
| `JWT_SECRET` | HMAC signing key for access tokens (**must** be ≥32 bytes; generate with `openssl rand -base64 64`) | insecure dev default — override in any real deployment |
| `JWT_ACCESS_TTL_MINUTES` | Access token lifetime | 15 |
| `JWT_REFRESH_TTL_DAYS` | Refresh token lifetime | 30 |
| `PASSWORD_RESET_TTL_MINUTES` | Password reset token lifetime | 30 |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins allowed to call the API (mobile app dev servers, etc.) | localhost Expo dev ports |
| `PORT` | HTTP port | 8080 |

Run with `--spring.profiles.active=prod` (or `SPRING_PROFILES_ACTIVE=prod`)
in any real environment (`application-prod.yml`) — expects
`DATABASE_URL`/`JWT_SECRET` to be supplied externally.

## Auth model

The real schema's RBAC: a `User` (login: `username` + `password_hash`,
`is_superadmin` flag) maps 1:1 to an `Employee` (the person), and gets zero
or more `UserRole` grants — each a `Role` (`role_code` like `SUPERADMIN`,
`KEPALA_TOKO`, `PRODUCT`, `GUDANG`, `KASIR`), optionally scoped to a
`Store` (`store == null` means the grant applies everywhere, e.g.
SUPERADMIN). JWT authorities are `ROLE_<role_code>` per grant, plus
`ROLE_SUPERADMIN` when `is_superadmin` is set.

- `POST /api/v1/auth/register` — creates a new **store** plus a
  **KEPALA_TOKO** (store head) account in one transaction — the closest
  equivalent, in this RBAC model, of the old single-role "owner" concept.
  Also creates the mandatory 1:1 `Employee` row (`employee_code` = the
  chosen username, since none is collected separately here).
- `POST /api/v1/auth/login` — **username** + password → access token +
  refresh token. (Not email — `email` is nullable in the real schema;
  `username` is the real unique login identifier, e.g. `kepala.str001`.)
- `POST /api/v1/auth/refresh` — exchanges a refresh token for a new access
  token; the old refresh token is revoked (rotation), so a stolen-and-reused
  refresh token invalidates the whole chain the next time the legitimate
  client tries to use it.
- `POST /api/v1/auth/logout` — revokes a refresh token.
- `GET /api/v1/auth/me` — current user profile + role grants (requires
  `Authorization: Bearer <accessToken>`).
- `POST /api/v1/auth/change-password` — authenticated user changes their own
  password (must supply the current one). Revokes all of that user's refresh
  tokens, so other devices are signed out.
- `POST /api/v1/auth/forgot-password` — starts the reset flow: issues a
  one-time token (30 min TTL, configurable via `PASSWORD_RESET_TTL_MINUTES`)
  if the **username** is registered. Always responds the same way
  regardless, so it can't be used to enumerate accounts. **No real email
  provider is wired in** — `LoggingPasswordResetMailSender` just logs the
  token; swap in a real `PasswordResetMailSender` (SES/SendGrid/Postmark/...)
  before relying on this outside local dev.
- `POST /api/v1/auth/reset-password` — exchanges that token for a new
  password. One-time use; also revokes all of that user's refresh tokens.

Access tokens are self-contained JWTs (claims: `sub`=userId, `username`,
`email`, `superadmin`, `roles`=`"CODE:storeUuid,CODE2:,..."`) validated
without a DB round trip per request — see `JwtAuthenticationFilter`.
Refresh tokens are opaque random strings; only their SHA-256 hash is stored
(`refresh_tokens.token_hash`), so a leaked DB row can't be replayed as a
live token.

Store-scoping enforcement (filtering queries by a role grant's `storeId`)
isn't implemented yet — there's nothing to scope until Catalog/Sales come
back in phase 2.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | creates store + KEPALA_TOKO account |
| POST | `/api/v1/auth/login` | public | username + password |
| POST | `/api/v1/auth/refresh` | public | rotates refresh token |
| POST | `/api/v1/auth/logout` | public | revokes refresh token |
| GET | `/api/v1/auth/me` | bearer | |
| POST | `/api/v1/auth/change-password` | bearer | requires current password |
| POST | `/api/v1/auth/forgot-password` | public | issues reset token (logged, not emailed — see Auth model) |
| POST | `/api/v1/auth/reset-password` | public | one-time token → new password |

Catalog/Sales/Report endpoints (`/api/v1/categories`, `/api/v1/products`,
`/api/v1/orders`, `/api/v1/reports/**`) are **not currently mounted** — see
"Migration status" above.

Full request/response shapes are in Swagger UI.

## What's deliberately out of scope

- **Catalog, Sales, Report modules** — moved to `deferred-phase2/` (not
  compiled). They were built against this project's own old schema
  (`products`, `orders`, `customers`, ...), which no longer exists now that
  we point at the real database. Porting them means remapping onto
  `products`/`product_categories`/`product_prices`, `sales_transactions`/
  `sales_transaction_items`/`payments`/`cashier_sessions`,
  `inventories`/`stock_movements`, etc. — a separate, larger piece of work.
- **Store-scoped query filtering** — not implemented; nothing to scope
  until phase 2.
- **Permission-level authorization** (`permissions`/`role_permissions`
  tables) — only role-code-level authorities (`ROLE_KASIR`, etc.) are
  wired up; fine-grained permission checks aren't used anywhere yet.
- **Account lockout** — `users.failed_login_count` exists in the schema but
  isn't incremented on failed login; `last_login_at` **is** updated on
  success.
- No refresh-token cleanup job for expired/revoked rows (fine at small
  scale; add a scheduled sweep before it matters).
