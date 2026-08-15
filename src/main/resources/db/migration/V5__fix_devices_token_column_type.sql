-- V4 created `token` as nvarchar(max), but Hibernate maps a @Lob String
-- field to CLOB, which the SQL Server dialect expects as varchar(max) (not
-- nvarchar(max)) — schema validation failed on startup. Table was brand new
-- with no data, so a straight ALTER is safe.
alter table devices alter column token varchar(max) not null;
