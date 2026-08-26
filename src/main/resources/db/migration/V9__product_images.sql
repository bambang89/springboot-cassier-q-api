create table product_images (
    id          uniqueidentifier not null default newsequentialid() primary key,
    product_id  uniqueidentifier not null,
    image_url   nvarchar(500) not null,
    sort_order  int not null default 0,
    created_by  uniqueidentifier null,
    created_at  datetime2 not null default sysutcdatetime(),
    updated_at  datetime2 not null default sysutcdatetime(),
    constraint fk_product_images_product foreign key (product_id) references products(id) on delete cascade,
    constraint fk_product_images_created_by foreign key (created_by) references users(id)
);
create index idx_product_images_product on product_images(product_id);
