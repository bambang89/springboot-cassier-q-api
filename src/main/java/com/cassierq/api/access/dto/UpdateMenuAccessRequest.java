package com.cassierq.api.access.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateMenuAccessRequest(@NotNull(message = "Daftar menu wajib diisi") List<String> menus) {
}
