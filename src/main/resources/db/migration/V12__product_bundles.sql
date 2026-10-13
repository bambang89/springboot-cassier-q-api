-- Paket bundling: produk berjenis BUNDLE yang isinya produk lain.
--
-- Additive saja: kolom baru punya default sehingga API versi lama (blue) yang
-- belum tahu kolom ini tetap bisa menyimpan produk (otomatis SINGLE).
-- Stok paket tidak disimpan — dihitung dari stok isinya; saat terjual, stok
-- tiap isi yang dipotong (lihat OrderService).

alter table products add product_type varchar(10) not null
    constraint df_products_product_type default 'SINGLE';
GO
alter table products add constraint ck_products_product_type check (product_type in ('SINGLE', 'BUNDLE'));
GO

create table product_bundle_items (
    id                   uniqueidentifier not null default newsequentialid() primary key,
    bundle_product_id    uniqueidentifier not null,
    component_product_id uniqueidentifier not null,
    unit_id              uniqueidentifier not null,
    quantity             decimal(12, 3) not null,
    created_at           datetime2 not null default sysutcdatetime(),
    updated_at           datetime2 not null default sysutcdatetime(),
    constraint fk_pbi_bundle foreign key (bundle_product_id) references products(id),
    constraint fk_pbi_component foreign key (component_product_id) references products(id),
    constraint fk_pbi_unit foreign key (unit_id) references units(id),
    constraint uq_pbi_bundle_component_unit unique (bundle_product_id, component_product_id, unit_id),
    constraint ck_pbi_quantity_positive check (quantity > 0),
    constraint ck_pbi_not_self check (bundle_product_id <> component_product_id)
);
create index idx_pbi_bundle on product_bundle_items(bundle_product_id);
create index idx_pbi_component on product_bundle_items(component_product_id);
GO
