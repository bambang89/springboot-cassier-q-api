package com.cassierq.api.access.dto;

import java.util.List;
import java.util.Map;

/**
 * @param menus       katalog semua menu
 * @param roles       role staf → menu yang boleh dilihat
 * @param actions     menu → aksi yang bisa diatur (kode + label); menu tanpa entri = hanya lihat
 * @param roleActions role staf → aksi yang diizinkan, mis. "products.create"
 */
public record MenuAccessResponse(
        List<String> menus,
        Map<String, List<String>> roles,
        Map<String, List<ActionDef>> actions,
        Map<String, List<String>> roleActions) {

    public record ActionDef(String code, String label) {
    }
}
