-- Additive nullable column on the shared `products` table (owned by the Go
-- backend) — deliberately requested. Doesn't affect their existing
-- inserts/selects (they just won't populate it), only widens the table.
alter table products add image_url nvarchar(500) null;
