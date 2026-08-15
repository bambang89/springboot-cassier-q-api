-- device_type is now optional (client-provided via header, may be absent),
-- and device_id (below) is a more precise per-device key than device_type
-- alone — so the old (user_id, device_type) uniqueness no longer holds
-- (SQL Server only allows one NULL per unique-key combo, which would break
-- as soon as two devices from the same user both omit device_type).
-- Upsert-by-key is now enforced in application code instead.
alter table devices drop constraint uq_devices_user_type;

alter table devices alter column device_type varchar(20) null;

-- New, all optional/default-null. SQL Server's ALTER TABLE always appends
-- columns at the end physically — there's no way to insert them "before
-- device_type" in on-disk column order; column order in results is
-- controlled by the entity's SELECT projection instead, not table layout.
alter table devices add device_id nvarchar(200) null;
alter table devices add device_os nvarchar(100) null;
alter table devices add app_version nvarchar(50) null;

create index idx_devices_device_id on devices(device_id);
