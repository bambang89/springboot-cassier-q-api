package com.cassierq.api.settings.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateCheckoutSettingsRequest(
        @NotEmpty(message = "Pilih minimal satu metode pembayaran")
        List<String> enabledPaymentMethods,

        @NotNull(message = "Metode pembayaran default wajib dipilih")
        String defaultPaymentMethod,

        @NotNull(message = "Pengaturan PPN wajib diisi")
        Boolean taxEnabled) {
}
