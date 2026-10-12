-- Flyway callback: jalan setiap kali migrate (setiap API start), idempotent.
--
-- Selama blue-green overlap (atau saat API lama masih jalan setelah V10
-- diterapkan), API lama membuat baris katalog tanpa owner_store_id. Baris itu
-- tidak akan terlihat oleh API baru, jadi di sini dipasangkan ke pemiliknya:
-- produk ke toko induk pembuatnya, sisanya ke Head Office.
if col_length('products', 'owner_store_id') is not null
begin
    exec('
        update p
        set owner_store_id = coalesce(s.parent_store_id, s.id)
        from products p
        join users u on u.id = p.created_by
        join employees e on e.id = u.employee_id
        join stores s on s.id = e.store_id
        where p.owner_store_id is null;

        declare @ho uniqueidentifier = (select top 1 id from stores
            where is_head_office = 1 and parent_store_id is null order by created_at);
        if @ho is not null
        begin
            update products set owner_store_id = @ho where owner_store_id is null;
            update product_categories set owner_store_id = @ho where owner_store_id is null;
            update units set owner_store_id = @ho where owner_store_id is null;
            update suppliers set owner_store_id = @ho where owner_store_id is null;
        end
    ');
end
