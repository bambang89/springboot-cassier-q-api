create table customers (
    id            uniqueidentifier not null default newsequentialid() primary key,
    store_id      uniqueidentifier not null,
    customer_code varchar(40) not null,
    name          nvarchar(150) not null,
    phone         nvarchar(30) null,
    address       nvarchar(255) null,
    credit_limit  decimal(12, 2) null,
    is_active     bit not null default 1,
    created_at    datetime2 not null default sysutcdatetime(),
    updated_at    datetime2 not null default sysutcdatetime(),
    constraint fk_customers_store foreign key (store_id) references stores(id) on delete cascade,
    constraint uq_customers_store_code unique (store_id, customer_code)
);
create index idx_customers_store on customers(store_id);

create table customer_ledger_entries (
    id                   uniqueidentifier not null default newsequentialid() primary key,
    customer_id          uniqueidentifier not null,
    entry_type           varchar(20) not null,
    amount               decimal(12, 2) not null,
    sales_transaction_id uniqueidentifier null,
    notes                nvarchar(500) null,
    created_by           uniqueidentifier not null,
    created_at           datetime2 not null default sysutcdatetime(),
    constraint fk_cle_customer foreign key (customer_id) references customers(id) on delete cascade,
    constraint fk_cle_sales_transaction foreign key (sales_transaction_id) references sales_transactions(id),
    constraint fk_cle_created_by foreign key (created_by) references users(id),
    constraint ck_cle_type check (entry_type in ('DEBT', 'PAYMENT')),
    constraint ck_cle_amount_positive check (amount > 0)
);
create index idx_cle_customer on customer_ledger_entries(customer_id);
