package com.cassierq.api.security;

import java.util.UUID;

/**
 * One `user_roles` grant: a role code, optionally scoped to a store.
 * {@code storeId == null} means the grant applies everywhere (e.g. a
 * SUPERADMIN's role isn't tied to any single store).
 */
public record RoleGrant(String roleCode, UUID storeId) {
}
