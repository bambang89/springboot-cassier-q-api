-- Tracks the current access token per (user, device type) — one row per
-- platform a user is logged in from, upserted on every login/register (and
-- on refresh, when the client resends deviceType). NOT a revocation
-- mechanism by itself (see user_sessions for that); this is a device/token
-- record. Token is stored AS GIVEN, unhashed, per explicit product decision —
-- unlike refresh_tokens/password_reset_tokens, a leaked `devices` row is a
-- directly usable live access token until it naturally expires (<= 1 day).
create table devices (
    id           uniqueidentifier not null default newsequentialid() primary key,
    user_id      uniqueidentifier not null,
    employee_id  uniqueidentifier not null,
    device_type  varchar(20) not null,
    token        nvarchar(max) not null,
    created_at   datetime2 not null default sysutcdatetime(),
    updated_at   datetime2 not null default sysutcdatetime(),
    constraint fk_devices_user foreign key (user_id) references users(id) on delete cascade,
    constraint fk_devices_employee foreign key (employee_id) references employees(id) on delete cascade,
    constraint ck_devices_type check (device_type in ('ANDROID', 'IOS', 'WEB')),
    constraint uq_devices_user_type unique (user_id, device_type)
);
create index idx_devices_user on devices(user_id);
