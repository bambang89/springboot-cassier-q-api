package com.cassierq.api.common.storage;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * File upload disimpan di DB sebagai path relatif ({@code /uploads/...}),
 * bukan URL absolut, supaya tidak rusak saat domain API berganti (mis. dari
 * sslip.io ke domain sendiri). URL absolut baru dibentuk saat response,
 * dari host request (di belakang nginx lewat X-Forwarded-*, lihat
 * {@code server.forward-headers-strategy}).
 */
public final class PublicUrls {

    private PublicUrls() {
    }

    /** Path relatif → URL absolut untuk response. URL absolut lama (data sebelum perubahan ini) dikembalikan apa adanya. */
    public static String resolve(String stored) {
        if (stored == null || !stored.startsWith("/") || RequestContextHolder.getRequestAttributes() == null) {
            return stored;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(stored).toUriString();
    }

    /** URL dari client (biasanya hasil {@link #resolve} yang dikirim balik) → bentuk yang disimpan di DB. */
    public static String toStored(String incoming) {
        if (incoming == null || incoming.isBlank() || RequestContextHolder.getRequestAttributes() == null) {
            return incoming;
        }
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        return incoming.startsWith(base + "/uploads/") ? incoming.substring(base.length()) : incoming;
    }
}
