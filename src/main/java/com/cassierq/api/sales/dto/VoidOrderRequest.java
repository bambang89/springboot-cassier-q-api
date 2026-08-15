package com.cassierq.api.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VoidOrderRequest(
        @NotBlank(message = "Alasan pembatalan wajib diisi")
        @Size(max = 255)
        String reason) {
}
