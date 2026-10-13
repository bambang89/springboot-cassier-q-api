package com.cassierq.api.store.dto;

import com.cassierq.api.domain.entity.Store;
import java.util.Map;
import java.util.UUID;

public record StoreProfileResponse(
        UUID id,
        String storeCode,
        String storeName,
        String address,
        String province,
        String city,
        String phone,
        String status,
        boolean headOffice,
        Map<String, String> settings,
        // Store photo/logo (settings LOGO_URL) as a full URL; null when not set.
        String logoUrl) {

    public static StoreProfileResponse from(Store store, Map<String, String> settings) {
        return new StoreProfileResponse(
                store.getId(),
                store.getStoreCode(),
                store.getStoreName(),
                store.getAddress(),
                store.getProvince(),
                store.getCity(),
                store.getPhone(),
                store.getStatus(),
                store.isHeadOffice(),
                settings,
                com.cassierq.api.common.storage.PublicUrls.resolve(settings.get("LOGO_URL")));
    }
}
