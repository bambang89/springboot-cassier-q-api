create table user_sessions (
    id          uniqueidentifier not null default newsequentialid() primary key,
    user_id     uniqueidentifier not null,
    jti         varchar(64) not null,
    issued_at   datetime2 not null,
    expires_at  datetime2 not null,
    revoked     bit not null default 0,
    created_at  datetime2 not null default sysutcdatetime(),
    updated_at  datetime2 not null default sysutcdatetime(),
    constraint fk_user_sessions_user foreign key (user_id) references users(id) on delete cascade,
    constraint uq_user_sessions_jti unique (jti)
);
create index idx_user_sessions_user on user_sessions(user_id);
