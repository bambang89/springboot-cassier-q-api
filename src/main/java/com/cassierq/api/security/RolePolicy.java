package com.cassierq.api.security;

import java.util.List;
import java.util.Set;

/**
 * Hierarki role:
 * <ul>
 *   <li>SUPERADMIN — pemilik aplikasi; hanya dibuat langsung di database, tidak
 *       pernah bisa diberikan lewat API/app.</li>
 *   <li>KEPALA_TOKO — role tertinggi yang bisa daftar dari app (/auth/register);
 *       mengelola PRODUCT, GUDANG, dan KASIR di tokonya.</li>
 *   <li>PRODUCT, GUDANG, KASIR — staf; tampilan menunya bisa dibatasi KEPALA_TOKO.</li>
 * </ul>
 */
public final class RolePolicy {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String KEPALA_TOKO = "KEPALA_TOKO";

    /** Role staf, urut dari yang paling luas aksesnya. */
    public static final List<String> STAFF_ROLES = List.of("PRODUCT", "GUDANG", "KASIR");

    private RolePolicy() {
    }

    public static boolean isManager(AppUserPrincipal principal) {
        return principal.isSuperadminUser() || principal.hasRole(KEPALA_TOKO);
    }

    /** Role yang boleh diberikan caller ke karyawan. */
    public static Set<String> assignableBy(AppUserPrincipal caller) {
        if (caller.isSuperadminUser()) {
            return Set.of(KEPALA_TOKO, "PRODUCT", "GUDANG", "KASIR");
        }
        if (caller.hasRole(KEPALA_TOKO)) {
            return Set.copyOf(STAFF_ROLES);
        }
        return Set.of();
    }
}
