package com.cassierq.api.access.dto;

import java.util.List;

/** @param restricted false untuk SUPERADMIN/KEPALA_TOKO (semua menu); @param menus menu yang boleh tampil. */
public record MyMenuAccessResponse(boolean restricted, List<String> menus) {
}
