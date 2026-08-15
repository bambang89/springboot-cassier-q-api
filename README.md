# cassier-Q API (Spring Boot)

REST backend for the cassier-Q POS app — a Spring Boot client of the **same
SQL Server database** the sibling Go backend (`../../Cassier-Q`) already
owns and manages. This project does not own the schema: `stores`, `users`,
`roles`, `products`, `sales_transactions`, and 20+ other tables already
exist there, seeded with real store/employee/role/product data. We only
ever read that schema and additively migrate two tables of our own
(`refresh_tokens`, `password_reset_tokens`) for JWT auth — see "Auth model"
below.

**Migration status:** Auth, Catalog (+ Units), Store Profile, Cashier
Sessions, Sales (+ receipts), Reports, Employees, Purchase Orders/Suppliers,
and Customers/Credit are ported onto the real schema (see their sections
below). **Not** ported: Stock Transfers, Stock Opname, Discount Rules,
Audit Logs — real tables exist for all of these, none are wired up yet.
The old self-owned-schema code from before this project pointed at the
real database is retired to [`deferred-phase2/`](deferred-phase2/) (not
compiled) purely for reference; nothing there still applies now that the
schema underneath changed this much.

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

**Every bearer-authenticated request must also send a matching
`X-Device-Id` header**, checked in that same central filter. Missing header
→ 401 `DEVICE_MISMATCH` ("Header X-Device-Id wajib diisi"); a header whose
value isn't on file for that user in `devices` → 401 `DEVICE_MISMATCH`
("Device ID tidak dikenali untuk akun ini"). This applies to *every*
bearer-authenticated endpoint across every module (Catalog, Sales, Cashier
Sessions, Reports, Inventory, and Auth's own `/me`, `/change-password`,
`/logout-all`, `/revoke`) — not just Auth. The public endpoints
(`register`/`login`/`refresh`/`logout`/`forgot-password`/`reset-password`)
are exempt since they run before there's an authenticated principal to
check a device against.

**Practical consequence:** a client that never sends `X-Device-Id` at login
(recording it in `devices`, see below) has nothing to match on every
subsequent call — they'll be locked out of everything except the public
auth endpoints. Always send `X-Device-Id` from login onward.

Refresh tokens are separate: opaque random strings, only their SHA-256 hash
stored (`refresh_tokens.token_hash`), so a leaked DB row can't be replayed
as a live token. `logout-all`/`revoke` clear `user_sessions`,
`refresh_tokens`, **and** `devices` (below) for the target user.

**Devices:** four **headers**, all optional, read on `register`/`login`/
`refresh` (never body fields): `X-Device-Id`, `X-Device-OS`,
`X-App-Version`, `X-Device-Type` (must be `ANDROID`/`IOS`/`WEB` when sent —
400 otherwise). Each upserts one `devices` row — keyed by `X-Device-Id` when
present (the precise per-physical-device key), falling back to
`X-Device-Type` otherwise (coarser: a second device of the same platform
without an id overwrites the first). A header omitted on a given call
leaves that column as whatever was recorded before (partial update — it
doesn't get wiped to null). **The token is stored as given, unhashed** — a
deliberate product decision, unlike every other token table in this
project; treat a `devices` row as a live credential (it's usable until the
access token's own ≤1-day expiry, same as stealing the JWT directly). See
above for how `X-Device-Id` also gates every *other* authenticated
endpoint.

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
Category/Product/Unit writes require `PRODUCT` or `SUPERADMIN` role.
`GET /products?search=` matches name, SKU, barcode, or brand — one search
box for however the cashier types it in (including scan-pasting a barcode).

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET / POST / PUT / DELETE | `/api/v1/units` | bearer / PRODUCT,SUPERADMIN | global (pcs, dus, kg, ...); DELETE is a hard delete — 409 if still referenced |
| GET | `/api/v1/categories` | bearer | |
| POST / PUT | `/api/v1/categories` | bearer, PRODUCT/SUPERADMIN | PUT rejects a category being its own parent |
| DELETE | `/api/v1/categories/{id}` | bearer, PRODUCT/SUPERADMIN | 409 if products still reference it |
| GET | `/api/v1/products` | bearer | paginated, `?search=`; price/stock scoped to caller's store |
| GET | `/api/v1/products/barcode/{barcode}` | bearer | used by the mobile app's scanner |
| GET | `/api/v1/products/{id}/convert` | bearer | `?unitId=&quantity=` — converts to base units via `product_unit_conversions` (e.g. "3 DUS" → `quantityBaseUnit: 72`); 400 if that unit isn't registered for the product. Read-only preview of the same conversion Sales/Purchase Orders apply internally. |
| POST | `/api/v1/products` | bearer, PRODUCT/SUPERADMIN | body includes optional `imageUrl`; also creates price + zero stock for caller's store |
| PUT | `/api/v1/products/{id}` | bearer, PRODUCT/SUPERADMIN | a changed price closes the old `product_prices` row and opens a new one (history preserved) |
| DELETE | `/api/v1/products/{id}` | bearer, PRODUCT/SUPERADMIN | soft delete (`deleted_at` + status `INACTIVE`) — past sales still reference the row |
| POST | `/api/v1/products/{id}/restock` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | body: `unitId`, `quantity`, `notes?`. Manual stock-in — creates the store's `inventories` row on first restock if the store never carried this product before. Always records a `STOCK_IN` stock_movements row. See Purchase Orders below for the formal, supplier-tracked way to add stock. |

`products.image_url` (`nvarchar(500)`, nullable) is a column **we added**
to the shared `products` table (`V8__products_image_url.sql`) — the one
deliberate exception to only ever adding new tables, not new columns, to
schema we don't own. Additive and nullable, so it doesn't affect the Go
backend's existing reads/writes.

## Store Profile

`stores` (name/address/phone/...) is shared, real, already existed.
`store_settings` (free-form per-store key/value config, already seeded
with `TAX_RATE_PERCENT` for every real store) existed too but was
completely unused until now — we reuse it for the extra profile fields
`stores` doesn't have a column for (logo, description, receipt footer,
...) instead of altering `stores` itself.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/store` | bearer | caller's store: `stores` fields + all `store_settings` as a `settings` map |
| PUT | `/api/v1/store` | bearer, SUPERADMIN/KEPALA_TOKO | partial update — only fields sent change; `settings` is upserted key-by-key (keys not mentioned are left alone). Keys are free-form, e.g. `LOGO_URL`, `DESCRIPTION`, `RECEIPT_FOOTER`, `EMAIL`, `TAX_ID` |

## Employees

`POST /api/v1/auth/register` always creates a **new store** — it's not how
you add a second staff member to an existing one. This is that: adds an
`Employee` + `User` + one `UserRole` grant to the **caller's own store** in
one transaction, provisioning nothing else (no new store, no
`number_sequences`).

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/roles` | bearer, SUPERADMIN/KEPALA_TOKO | catalog of assignable role codes (excludes `SUPERADMIN` — can't self-service-grant it) |
| POST | `/api/v1/employees` | bearer, SUPERADMIN/KEPALA_TOKO | body: `name`, `username`, `email?`, `phone?`, `password`, `roleCode` (`KEPALA_TOKO`/`PRODUCT`/`GUDANG`/`KASIR`). Sets `must_change_password = true` — the owner picked this password, not the employee (not yet enforced at login, see "What's deliberately out of scope") |
| GET | `/api/v1/employees` | bearer, SUPERADMIN/KEPALA_TOKO | everyone at the caller's store, including the caller |
| GET | `/api/v1/employees/{id}` | bearer, SUPERADMIN/KEPALA_TOKO | |
| PUT | `/api/v1/employees/{id}` | bearer, SUPERADMIN/KEPALA_TOKO | edits name/email/phone/`roleCode` — not username/password (see `/auth/change-password`). Changing `roleCode` deletes the old `UserRole` grant at this store and inserts the new one |
| POST | `/api/v1/employees/{id}/deactivate` | bearer, SUPERADMIN/KEPALA_TOKO | `Employee.active` + `User.active` → false, and immediately kills their sessions/refresh tokens/devices (same as `/auth/revoke`) — they can't finish out their current 1-day access token. Can't deactivate yourself (400). |
| POST | `/api/v1/employees/{id}/reactivate` | bearer, SUPERADMIN/KEPALA_TOKO | |

## Purchase Orders & Suppliers

Formal, supplier-tracked stock-in — an alternative to the manual `/restock`
above. `suppliers` is a global catalog (like products); `purchase_orders`/
`purchase_order_items` are store-scoped. `po_number` uses the same
generator as `transaction_number` (see Sales below), against the
`PURCHASE_ORDER`/`PO` `number_sequences` row.

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET / POST / PUT / DELETE | `/api/v1/suppliers` | bearer / SUPERADMIN,KEPALA_TOKO,GUDANG | global catalog; DELETE soft-deactivates |
| POST | `/api/v1/purchase-orders` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | creates with status `ORDERED` directly (no separate DRAFT→ORDERED step) |
| GET | `/api/v1/purchase-orders` / `/{id}` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | paginated / detail |
| POST | `/api/v1/purchase-orders/{id}/receive` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | body: list of `{purchaseOrderItemId, receivedQuantity}` — partial receiving supported (repeat calls); increments `inventories` + records a `PURCHASE` stock_movements row per item; status becomes `PARTIALLY_RECEIVED` or `RECEIVED` once every line is fully in. Rejects over-receiving past what was ordered. |
| POST | `/api/v1/purchase-orders/{id}/cancel` | bearer, SUPERADMIN/KEPALA_TOKO/GUDANG | only while `DRAFT`/`ORDERED` — once anything's been received, it can't be cancelled |

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
row is written. `transaction_number` is generated by the shared
`NumberSequenceService` (also used by Purchase Orders) from that store's
`number_sequences` row: `{prefix}-{storeCode}-{yyMMdd}-{seq:6}` (matches the
existing data's own format, e.g. `TRX-STR001-260810-000001`).

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/orders` | bearer | decrements stock + records payment in the same transaction |
| GET | `/api/v1/orders` | bearer | paginated, newest first |
| GET | `/api/v1/orders/{id}` | bearer | |
| GET | `/api/v1/orders/{id}/receipt` | bearer | struk/nota: store name/address/phone, cashier, items, totals, payment method, change — plus `customerName`/`debtAmount` when it was a credit sale. Structured data, not tied to any printer format (ESC/POS, PDF, ...) — the client renders it |
| POST | `/api/v1/orders/{id}/void` | bearer | body: `reason`; restocks items, PAID → VOID |

Only single-method `CASH`/`CREDIT_CARD`/`DEBIT`/`TRANSFER`/`QRIS` payment
per order — no split tender, no line-level discounts (only an order-level
`discountAmount`/`taxAmount`).

**Credit sales:** `CreateOrderRequest` takes an optional `customerId`. Omit
it and behavior is exactly as before (`paymentAmount` must cover the full
total, or 400). Set it and pay less than the total, and the shortfall is
recorded as a `DEBT` against that customer (see Customers below) instead of
being rejected — checked against their `creditLimit` first, and the entire
sale (stock deduction included) rolls back if it would be exceeded. The
response's `customerId`/`debtAmount` reflect this only right after creation
— `GET`/list don't reconstruct them (check the customer's ledger instead).

## Reports

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/reports/summary` | bearer | `?from=&to=` (ISO date, default last 7 days): order count, gross sales, top 5 best sellers — all for the caller's store, PAID orders only |

## Customers & Credit (hutang/piutang)

**Entirely new — the real schema has no customer concept at all**, no
`customers` table anywhere among its 30. `customers` and
`customer_ledger_entries` are ours (additive), store-scoped. Balance isn't
a stored running total — it's `sum(DEBT) − sum(PAYMENT)` computed on read
(`CustomerLedgerEntryRepository.balanceOf`), so it can't drift out of sync.
A ledger entry, once written, is never edited.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/customers` | bearer | body: `customerCode`, `name`, `phone?`, `address?`, `creditLimit?` |
| GET | `/api/v1/customers` / `/{id}` | bearer | includes computed `balance` |
| PUT | `/api/v1/customers/{id}` | bearer, SUPERADMIN/KEPALA_TOKO | only role that can change `creditLimit` |
| GET | `/api/v1/customers/{id}/ledger` | bearer | every DEBT/PAYMENT entry, newest first, with the linked `sales_transaction_id`/`transactionNumber` when it came from a credit sale |
| POST | `/api/v1/customers/{id}/payments` | bearer | records a `PAYMENT` entry (customer paying down their tab) — not tied to a sale |

`customer_ledger_entries.sales_transaction_id` FKs into the real
`sales_transactions` table (their PK, read-only reference — doesn't alter
their schema, but does mean a linked row can't be hard-deleted out from
under us; acceptable since that table is soft-stated via
`transaction_status`, never hard-deleted in practice).

Full request/response shapes for every endpoint are in Swagger UI.

## What's deliberately out of scope

- **Stock Transfers, Stock Opname, Discount Rules, Audit Logs** — real
  tables exist for all of these, none are wired up: no
  transfer-between-stores, no periodic physical stock count reconciliation,
  no per-item/promo discount rules (only order-level `discountAmount`), no
  audit trail of who changed what.
- **`must_change_password` isn't enforced** — set to `true` when
  `/api/v1/employees` creates an account, but login doesn't currently check
  or act on it.
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
  held for the duration of the sale/receiving transaction. Simpler, and
  enough at this scale; revisit if lock contention becomes a real problem.
- No refresh-token/session cleanup job for expired/revoked rows (fine at
  small scale; add a scheduled sweep before it matters).
