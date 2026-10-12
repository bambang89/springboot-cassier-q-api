-- Katalog per-toko (per bisnis) + perbaikan penjualan.
--
-- Semua perubahan di sini additive / nullable supaya versi API lama (blue)
-- yang masih jalan selama blue-green overlap tidak rusak: kolom baru boleh
-- null, tidak ada kolom yang di-drop. Baris yang sempat dibuat API lama
-- (owner_store_id masih null) di-backfill ulang oleh afterMigrate.sql.

-- 1. Hierarki toko: cabang menunjuk ke toko induk (pemilik katalog).
--    Toko tanpa parent = toko induk, katalognya sendiri.
alter table stores add parent_store_id uniqueidentifier null;
GO
alter table stores add constraint fk_stores_parent foreign key (parent_store_id) references stores(id);
create index idx_stores_parent on stores(parent_store_id);
GO

-- Data lama = satu jaringan toko dengan satu Head Office: semua cabang jadi
-- anak dari Head Office itu (hanya kalau Head Office-nya tepat satu).
if (select count(*) from stores where is_head_office = 1) = 1
    update stores
    set parent_store_id = (select id from stores where is_head_office = 1)
    where is_head_office = 0 and parent_store_id is null;
GO

-- 2. Pemilik katalog: products, product_categories, units, suppliers.
alter table products add owner_store_id uniqueidentifier null;
alter table product_categories add owner_store_id uniqueidentifier null;
alter table units add owner_store_id uniqueidentifier null;
alter table suppliers add owner_store_id uniqueidentifier null;
GO
alter table products add constraint fk_products_owner_store foreign key (owner_store_id) references stores(id);
alter table product_categories add constraint fk_product_categories_owner_store foreign key (owner_store_id) references stores(id);
alter table units add constraint fk_units_owner_store foreign key (owner_store_id) references stores(id);
alter table suppliers add constraint fk_suppliers_owner_store foreign key (owner_store_id) references stores(id);
GO

-- Katalog lama milik toko induk (Head Office) — dipakai bersama cabangnya.
declare @ho uniqueidentifier = (select top 1 id from stores where is_head_office = 1 and parent_store_id is null order by created_at);
if @ho is not null
begin
    update products set owner_store_id = @ho where owner_store_id is null;
    update product_categories set owner_store_id = @ho where owner_store_id is null;
    update units set owner_store_id = @ho where owner_store_id is null;
    update suppliers set owner_store_id = @ho where owner_store_id is null;
end
GO

-- 3. Kode unik sekarang per pemilik katalog, bukan global — dua bisnis
--    berbeda boleh sama-sama punya SKU "SKU-001".
if exists (select 1 from sys.key_constraints where name = 'UQ_products_sku')
    alter table products drop constraint UQ_products_sku;
else if exists (select 1 from sys.indexes where name = 'UQ_products_sku')
    drop index UQ_products_sku on products;

if exists (select 1 from sys.key_constraints where name = 'UQ_products_barcode_active')
    alter table products drop constraint UQ_products_barcode_active;
else if exists (select 1 from sys.indexes where name = 'UQ_products_barcode_active')
    drop index UQ_products_barcode_active on products;

if exists (select 1 from sys.key_constraints where name = 'UQ_product_categories_code')
    alter table product_categories drop constraint UQ_product_categories_code;
else if exists (select 1 from sys.indexes where name = 'UQ_product_categories_code')
    drop index UQ_product_categories_code on product_categories;

if exists (select 1 from sys.key_constraints where name = 'UQ_units_code')
    alter table units drop constraint UQ_units_code;
else if exists (select 1 from sys.indexes where name = 'UQ_units_code')
    drop index UQ_units_code on units;

if exists (select 1 from sys.key_constraints where name = 'UQ_suppliers_code')
    alter table suppliers drop constraint UQ_suppliers_code;
else if exists (select 1 from sys.indexes where name = 'UQ_suppliers_code')
    drop index UQ_suppliers_code on suppliers;
GO

create unique index uq_products_owner_sku on products(owner_store_id, sku);
create unique index uq_products_owner_barcode_active on products(owner_store_id, barcode)
    where barcode is not null and deleted_at is null;
create unique index uq_product_categories_owner_code on product_categories(owner_store_id, category_code);
create unique index uq_units_owner_code on units(owner_store_id, unit_code);
create unique index uq_suppliers_owner_code on suppliers(owner_store_id, supplier_code);
GO

-- 4. Void transaksi kredit membalik utangnya lewat entry 'VOID' (append-only,
--    bukan menghapus DEBT). balanceOf menghitung semua non-DEBT sebagai
--    pengurang, jadi API lama pun sudah menghitung saldo dengan benar.
alter table customer_ledger_entries drop constraint ck_cle_type;
alter table customer_ledger_entries add constraint ck_cle_type check (entry_type in ('DEBT', 'PAYMENT', 'VOID'));
GO

-- 5. Idempotency key: retry dari HP setelah timeout tidak membuat transaksi ganda.
alter table sales_transactions add idempotency_key nvarchar(100) null;
GO
create unique index uq_sales_transactions_idempotency on sales_transactions(store_id, idempotency_key)
    where idempotency_key is not null;
GO
