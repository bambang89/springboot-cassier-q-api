package com.cassierq.api.purchasing.dto;

import com.cassierq.api.domain.entity.Supplier;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String supplierCode,
        String supplierName,
        String contactPerson,
        String phone,
        String email,
        String address,
        boolean active) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive());
    }
}
