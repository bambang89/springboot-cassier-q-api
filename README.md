# cassier-Q API (Spring Boot)

REST backend for the cassier-Q POS app — a Spring Boot client of the **same
SQL Server database** the sibling Go backend (`../../Cassier-Q`) already
owns and manages. This project does not own the schema: `stores`, `users`,
`roles`, `products`, `sales_transactions`, and 20+ other tables already
exist there, seeded with real store/employee/role/product data. We only
ever read that schema and additively migrate two tables of our own
(`refresh_tokens`, `password_reset_tokens`) for JWT auth — see "Auth model"
below.

**Migration status:** Auth (register/login/me/change password/forgot
password/reset password), Catalog (categories/products/prices/stock),
Cashier Sessions, Sales (orders), and Reports are ported onto the real
schema. **Not** ported: Purchase Orders, Stock Transfers, Stock Opname,
Suppliers, Discount Rules, Audit Logs, Store Settings — these weren't part
of this app's original 4-menu scope (Catalog/Sales/Report) and the old
self-owned-schema code for the parts that *were* is retired to
[`deferred-phase2/`](deferred-phase2/) (not compiled) purely for reference;
nothing there still applies now that the schema underneath changed this much.

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
  Also creates the mandatory 1:1 `Employee` row and provisions that store's
  `number_sequences` rows (`SALES_TRANSACTION`/`TRX`, `STOCK_OPNAME`/`SO`,
  `PURCHASE_ORDER`/`PO`, `STOCK_TRANSFER`/`ST`) — without those, sales can't
  generate a transaction number at all.
- `POST /api/v1/auth/login` — **username** + password → access token +
  refresh token. (Not email — `email` is nullable in the real schema;
  `username` is the real unique login identifier, e.g. `kepala.str001`.)
- `POST /api/v1/auth/refresh` — exchanges a refresh token for a new access
  token; the old refresh token is revoked (rotation), so a stolen-and-reused
  refresh token invalidates the whole chain the next time the legitimate
  client tries to use it.
- `POST /api/v1/auth/logout` — revokes a refresh token.
- `POST /api/v1/auth/logout-all` — kills **every** live session (all access
  tokens + all refresh tokens) for the caller, effective on the very next
  request anywhere, not just future ones.
- `POST /api/v1/auth/revoke/{userId}` — admin force-logout of another user
  (same effect as `logout-all`, on their account). `SUPERADMIN` role can
  target anyone; `KEPALA_TOKO` only someone whose `Employee.store` matches
  the caller's own store (400 otherwise).
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

**Access tokens are session-checked, not purely stateless.** Each JWT
(claims: `jti`, `sub`=userId, `employeeId`, `username`, `email`,
`superadmin`, `roles`=`"CODE:storeUuid,CODE2:,..."`) has a matching
`user_sessions` row created at issue time; `JwtAuthenticationFilter` — the
one place every single API request passes through — verifies the
signature/expiry **and** looks up that row on every request, rejecting it
if revoked or expired even though the JWT itself would still verify. That
DB round trip per request is a deliberate trade-off: it's what makes
`logout-all`/`revoke` take effect immediately, which is also why the access
token TTL could safely move from 15 minutes to **1 day**
(`JWT_ACCESS_TTL_MINUTES`) — a revoke no longer has to wait out the TTL.

Refresh tokens are separate: opaque random strings, only their SHA-256 hash
stored (`refresh_tokens.token_hash`), so a leaked DB row can't be replayed
as a live token. `logout-all`/`revoke` clear `user_sessions`,
`refresh_tokens`, **and** `devices` (below) for the target user.

**Devices:** `register`/`login` require a `deviceType` (`ANDROID`/`IOS`/
`WEB`); `refresh` accepts it optionally. Each (user, deviceType) upserts one
row in `devices` holding that platform's current access token — a second
`ANDROID` login updates the existing row, it doesn't add one. **The token is
stored as given, unhashed** — a deliberate product decision, unlike every
other token table in this project; treat a `devices` row as a live
credential (it's usable until the access token's own ≤1-day expiry, same as
stealing the JWT directly).

**Store scoping:** every store-scoped endpoint below acts on
`principal.getPrimaryStoreId()` — the first store-scoped role grant found
for the caller. Users with grants at more than one store aren't fully
supported yet (no store-selection endpoint/header); a pure SUPERADMIN with
no store-level grant gets a 400 on any store-scoped call.

## Catalog

Products are a **global** catalog (`products` has no `store_id`); price
(`product_prices`) and stock (`inventories`) are **per-store**. Creating a
product also creates its base-unit conversion, its initial price, and a
zero-stock inventory row for the caller's store, all in one transaction.
Category/Product writes require `PRODUCT` or `SUPERADMIN` role.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/categories` | bearer | |
| POST | `/api/v1/categories` | bearer, PRODUCT/SUPERADMIN | |
| DELETE | `/api/v1/categories/{id}` | bearer, PRODUCT/SUPERADMIN | 409 if products still reference it |
| GET | `/api/v1/products` | bearer | paginated, `?search=`; price/stock scoped to caller's store |
| GET | `/api/v1/products/barcode/{barcode}` | bearer | used by the mobile app's scanner |
| POST | `/api/v1/products` | bearer, PRODUCT/SUPERADMIN | also creates price + zero stock for caller's store |
| PUT | `/api/v1/products/{id}` | bearer, PRODUCT/SUPERADMIN | a changed price closes the old `product_prices` row and opens a new one (history preserved) |
| DELETE | `/api/v1/products/{id}` | bearer, PRODUCT/SUPERADMIN | soft delete (`deleted_at` + status `INACTIVE`) — past sales still reference the row |
| POST | `/api/v1/products/{id}/restock` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | body: `unitId`, `quantity`, `notes?`. Manual stock-in only — no Purchase Order flow (see "What's deliberately out of scope"); creates the store's `inventories` row on first restock if the store never carried this product before. Always records a `STOCK_IN` stock_movements row. |

## Cashier Sessions

New concept, not in the old app — required because
`sales_transactions.cashier_session_id` is `NOT NULL` in the real schema. A
cashier opens a session (cash-drawer float) before ringing up any sale and
closes it at end of shift; the DB enforces at most one `OPEN` session per
cashier.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/cashier-sessions/open` | bearer | body: `openingCash` |
| POST | `/api/v1/cashier-sessions/{id}/close` | bearer | body: `actualCash`, `notes`; computes `expectedCash` = opening + cash sales in that session, `cashDifference` = actual − expected |
| GET | `/api/v1/cashier-sessions/current` | bearer | the caller's open session, 404 if none |

## Sales (Orders)

`POST /api/v1/orders` requires an open cashier session. For each line: the
product's current store price is used (never a client-supplied price), the
requested unit is converted to base units via `product_unit_conversions`,
stock is checked and decremented under a pessimistic row lock (concurrent
sales of the same product can't oversell it), and a `stock_movements` audit
row is written. `transaction_number` is generated from that store's
`number_sequences` row: `{prefix}-{storeCode}-{yyMMdd}-{seq:6}` (matches the
existing data's own format, e.g. `TRX-STR001-260810-000001`).

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/orders` | bearer | decrements stock + records payment in the same transaction |
| GET | `/api/v1/orders` | bearer | paginated, newest first |
| GET | `/api/v1/orders/{id}` | bearer | |
| POST | `/api/v1/orders/{id}/void` | bearer | body: `reason`; restocks items, PAID → VOID |

Only single, full-amount `CASH`/`CREDIT_CARD`/`DEBIT`/`TRANSFER`/`QRIS`
payment per order is supported — no split tender, no line-level discounts
(only an order-level `discountAmount`/`taxAmount`).

## Reports

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/reports/summary` | bearer | `?from=&to=` (ISO date, default last 7 days): order count, gross sales, top 5 best sellers — all for the caller's store, PAID orders only |

Full request/response shapes for every endpoint are in Swagger UI.

## What's deliberately out of scope

- **Purchase Orders, Stock Transfers, Stock Opname, Suppliers, Discount
  Rules, Audit Logs, Store Settings** — real tables exist for all of these,
  none are wired up. Stock only ever moves via `POST
  /api/v1/products/{id}/restock` (manual, no PO/supplier trail) or a sale
  (`/api/v1/orders`, decrements) / its void (restocks) — no receiving flow,
  no transfer-between-stores, no periodic stock count reconciliation.
- **Store-scoped query filtering beyond `getPrimaryStoreId()`** — no
  multi-store selection for a user with grants at more than one store.
- **Permission-level authorization** (`permissions`/`role_permissions`
  tables) — only role-code-level authorities (`ROLE_KASIR`, etc.) are
  wired up; fine-grained permission checks aren't used anywhere yet.
- **Account lockout** — `users.failed_login_count` exists in the schema but
  isn't incremented on failed login; `last_login_at` **is** updated on
  success.
- **Optimistic concurrency on `inventories`** — the table has a SQL Server
  `rowversion` column (`row_version`) for it; we don't map it, relying
  instead on a pessimistic row lock (`SELECT ... FOR UPDATE`-equivalent)
  held for the duration of the sale transaction. Simpler, and enough at
  this scale; revisit if lock contention becomes a real problem.
- No refresh-token cleanup job for expired/revoked rows (fine at small
  scale; add a scheduled sweep before it matters).
