package com.cassierq.api.access.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** @param actions aksi yang diizinkan (mis. "products.create"); null = biarkan seperti sebelumnya. */
public record UpdateMenuAccessRequest(
        @NotNull(message = "Daftar menu wajib diisi") List<String> menus,
        List<String> actions) {
}
