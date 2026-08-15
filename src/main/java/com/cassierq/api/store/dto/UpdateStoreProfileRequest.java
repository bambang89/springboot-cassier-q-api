package com.cassierq.api.store.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Partial update — every field is optional; only the ones sent are changed.
 * {@code settings} is upserted key-by-key (existing keys not mentioned are
 * left alone); free-form, e.g. {@code LOGO_URL}, {@code DESCRIPTION},
 * {@code RECEIPT_FOOTER}, {@code EMAIL}, {@code TAX_ID}.
 */
public record UpdateStoreProfileRequest(
        @Size(max = 150)
        String storeName,

        @Size(max = 255)
        String address,

        @Size(max = 100)
        String province,

        @Size(max = 100)
        String city,

        @Size(max = 30)
        String phone,

        Map<String, String> settings) {
}
