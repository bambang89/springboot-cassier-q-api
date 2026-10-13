-- Harga grosir bertingkat per produk per toko: makin banyak dibeli, harga per
-- satuan dasar makin murah. Jumlah dihitung dari total satuan dasar produk itu
-- di satu transaksi (mis. 1 dus isi 20 + 5 pcs = 25 pcs).
-- Additive saja: tabel baru, tidak mengubah tabel lama.

create table product_price_tiers (
    id           uniqueidentifier not null default newsequentialid() primary key,
    store_id     uniqueidentifier not null,
    product_id   uniqueidentifier not null,
    min_quantity decimal(12, 3) not null,
    unit_price   decimal(12, 2) not null,
    created_at   datetime2 not null default sysutcdatetime(),
    updated_at   datetime2 not null default sysutcdatetime(),
    constraint fk_ppt_store foreign key (store_id) references stores(id),
    constraint fk_ppt_product foreign key (product_id) references products(id),
    constraint uq_ppt_store_product_min unique (store_id, product_id, min_quantity),
    constraint ck_ppt_min_quantity check (min_quantity > 1),
    constraint ck_ppt_unit_price check (unit_price >= 0)
);
create index idx_ppt_store_product on product_price_tiers(store_id, product_id);
GO
