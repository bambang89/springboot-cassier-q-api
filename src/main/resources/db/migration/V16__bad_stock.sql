-- Bad Stock (BS): barang rusak / kedaluwarsa / hilang / cacat / retur pembeli.
-- Stok jual berkurang saat BS disetujui (atau langsung bila persetujuan dimatikan
-- / dicatat Kepala Toko); mutasinya tercatat di stock_movements (BAD_STOCK).
-- Additive saja: tabel baru.

create table bad_stock_records (
    id               uniqueidentifier not null default newsequentialid() primary key,
    store_id         uniqueidentifier not null,
    product_id       uniqueidentifier not null,
    unit_id          uniqueidentifier not null,
    quantity         decimal(12, 3) not null,
    quantity_base    decimal(12, 3) not null,
    reason           varchar(20) not null,
    notes            nvarchar(500) null,
    photo_url        nvarchar(500) null,
    status           varchar(20) not null,
    cost_value       decimal(14, 2) null,
    stock_deducted   bit not null default 0,
    supplier_id      uniqueidentifier null,
    reported_by      uniqueidentifier not null,
    approved_by      uniqueidentifier null,
    approved_at      datetime2 null,
    resolved_by      uniqueidentifier null,
    resolved_at      datetime2 null,
    resolution_note  nvarchar(500) null,
    created_at       datetime2 not null default sysutcdatetime(),
    updated_at       datetime2 not null default sysutcdatetime(),
    constraint fk_bs_store foreign key (store_id) references stores(id),
    constraint fk_bs_product foreign key (product_id) references products(id),
    constraint fk_bs_unit foreign key (unit_id) references units(id),
    constraint fk_bs_supplier foreign key (supplier_id) references suppliers(id),
    constraint fk_bs_reported_by foreign key (reported_by) references users(id),
    constraint fk_bs_approved_by foreign key (approved_by) references users(id),
    constraint fk_bs_resolved_by foreign key (resolved_by) references users(id),
    constraint ck_bs_quantity check (quantity > 0 and quantity_base > 0),
    constraint ck_bs_reason check (reason in ('DAMAGED', 'EXPIRED', 'LOST', 'DEFECT', 'CUSTOMER_RETURN')),
    constraint ck_bs_status check (status in ('PENDING', 'QUARANTINED', 'RETURNED', 'DISPOSED', 'CANCELLED'))
);
create index idx_bs_store_status on bad_stock_records(store_id, status);
create index idx_bs_store_created on bad_stock_records(store_id, created_at);
GO
