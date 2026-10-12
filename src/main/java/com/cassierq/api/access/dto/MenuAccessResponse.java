package com.cassierq.api.access.dto;

import java.util.List;
import java.util.Map;

/** @param menus katalog semua menu; @param roles role staf → menu yang boleh dilihat. */
public record MenuAccessResponse(List<String> menus, Map<String, List<String>> roles) {
}
