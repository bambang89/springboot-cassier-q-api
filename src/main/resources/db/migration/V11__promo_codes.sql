-- Kode promo per toko + jejak pemakaiannya di transaksi.
--
-- Additive saja (tabel baru + kolom nullable) supaya API versi lama yang masih
-- jalan selama blue-green overlap tidak terganggu. Pengaturan metode
-- pembayaran & PPN memakai tabel store_settings yang sudah ada (tanpa skema baru).

create table promo_codes (
    id                  uniqueidentifier not null default newsequentialid() primary key,
    store_id            uniqueidentifier not null,
    code                varchar(30) not null,
    name                nvarchar(120) not null,
    discount_type       varchar(10) not null,
    discount_value      decimal(12, 2) not null,
    max_discount_amount decimal(12, 2) null,
    min_purchase_amount decimal(12, 2) null,
    starts_at           datetime2 null,
    ends_at             datetime2 null,
    usage_limit         int null,
    used_count          int not null default 0,
    is_active           bit not null default 1,
    created_at          datetime2 not null default sysutcdatetime(),
    updated_at          datetime2 not null default sysutcdatetime(),
    constraint fk_promo_codes_store foreign key (store_id) references stores(id) on delete cascade,
    constraint uq_promo_codes_store_code unique (store_id, code),
    constraint ck_promo_codes_type check (discount_type in ('PERCENT', 'AMOUNT')),
    constraint ck_promo_codes_value_positive check (discount_value > 0),
    constraint ck_promo_codes_used_count check (used_count >= 0)
);
create index idx_promo_codes_store on promo_codes(store_id);
GO

alter table sales_transactions add promo_code_id uniqueidentifier null;
alter table sales_transactions add promo_code varchar(30) null;
alter table sales_transactions add promo_discount_amount decimal(12, 2) null;
GO
alter table sales_transactions add constraint fk_sales_transactions_promo_code
    foreign key (promo_code_id) references promo_codes(id);
create index idx_sales_transactions_promo_code on sales_transactions(promo_code_id);
GO
