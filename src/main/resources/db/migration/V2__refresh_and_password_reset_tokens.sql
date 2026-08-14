-- V1 is intentionally absent: this database's schema (stores, users, roles,
-- products, sales_transactions, ...) already exists, owned by the sibling
-- Go backend. Flyway is configured with baseline-on-migrate + baseline
-- version 1, so it treats that existing schema as the implicit V1 and never
-- tries to (re)create it. This migration only ADDS the two tables our JWT
-- auth needs that don't exist yet, following the same column conventions
-- (uniqueidentifier PK, newsequentialid(), datetime2, sysutcdatetime(), bit)
-- as the rest of the schema.

create table refresh_tokens (
    id          uniqueidentifier not null default newsequentialid() primary key,
    user_id     uniqueidentifier not null,
    token_hash  varchar(64) not null,
    expires_at  datetime2 not null,
    revoked     bit not null default 0,
    created_at  datetime2 not null default sysutcdatetime(),
    updated_at  datetime2 not null default sysutcdatetime(),
    constraint fk_refresh_tokens_user foreign key (user_id) references users(id) on delete cascade,
    constraint uq_refresh_tokens_token_hash unique (token_hash)
);
create index idx_refresh_tokens_user on refresh_tokens(user_id);

create table password_reset_tokens (
    id          uniqueidentifier not null default newsequentialid() primary key,
    user_id     uniqueidentifier not null,
    token_hash  varchar(64) not null,
    expires_at  datetime2 not null,
    used        bit not null default 0,
    created_at  datetime2 not null default sysutcdatetime(),
    updated_at  datetime2 not null default sysutcdatetime(),
    constraint fk_password_reset_tokens_user foreign key (user_id) references users(id) on delete cascade,
    constraint uq_password_reset_tokens_token_hash unique (token_hash)
);
create index idx_password_reset_tokens_user on password_reset_tokens(user_id);
