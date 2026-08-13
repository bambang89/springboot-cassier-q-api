create extension if not exists pgcrypto;

create table stores (
    id          uuid primary key default gen_random_uuid(),
    name        varchar(150) not null,
    address     varchar(255),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table users (
    id              uuid primary key default gen_random_uuid(),
    store_id        uuid not null references stores(id) on delete cascade,
    name            varchar(150) not null,
    email           varchar(255) not null unique,
    password_hash   varchar(255) not null,
    role            varchar(20) not null check (role in ('OWNER', 'CASHIER')),
    active          boolean not null default true,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_users_store on users(store_id);

create table categories (
    id          uuid primary key default gen_random_uuid(),
    store_id    uuid not null references stores(id) on delete cascade,
    name        varchar(100) not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_categories_store on categories(store_id);

create table products (
    id          uuid primary key default gen_random_uuid(),
    store_id    uuid not null references stores(id) on delete cascade,
    category_id uuid references categories(id) on delete set null,
    sku         varchar(64) not null,
    barcode     varchar(64),
    name        varchar(150) not null,
    price       numeric(12, 2) not null check (price >= 0),
    stock       integer not null default 0 check (stock >= 0),
    image_url   varchar(500),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint uq_products_store_sku unique (store_id, sku)
);
create index idx_products_store on products(store_id);
create index idx_products_barcode on products(store_id, barcode);

create table customers (
    id          uuid primary key default gen_random_uuid(),
    store_id    uuid not null references stores(id) on delete cascade,
    name        varchar(150) not null,
    phone       varchar(30),
    email       varchar(255),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_customers_store on customers(store_id);

create table orders (
    id              uuid primary key default gen_random_uuid(),
    store_id        uuid not null references stores(id) on delete cascade,
    cashier_id      uuid not null references users(id),
    customer_id     uuid references customers(id) on delete set null,
    status          varchar(20) not null check (status in ('PAID', 'CANCELLED', 'REFUNDED')),
    payment_method  varchar(20) not null check (payment_method in ('CASH', 'NON_CASH')),
    subtotal        numeric(12, 2) not null,
    discount        numeric(12, 2) not null default 0,
    total           numeric(12, 2) not null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_orders_store_created on orders(store_id, created_at desc);

create table order_items (
    id              uuid primary key default gen_random_uuid(),
    order_id        uuid not null references orders(id) on delete cascade,
    product_id      uuid not null references products(id),
    product_name    varchar(150) not null,
    unit_price      numeric(12, 2) not null,
    quantity        integer not null check (quantity > 0),
    line_total      numeric(12, 2) not null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_order_items_order on order_items(order_id);

create table refresh_tokens (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    token_hash  varchar(64) not null unique,
    expires_at  timestamptz not null,
    revoked     boolean not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_refresh_tokens_user on refresh_tokens(user_id);
