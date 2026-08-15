package com.cassierq.api.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReceivePurchaseOrderRequest(
        @NotEmpty(message = "Item yang diterima wajib diisi")
        @Valid
        List<ReceiveItemRequest> items) {
}
