package com.cassierq.api.customer.dto;

import com.cassierq.api.domain.entity.Customer;
import java.math.BigDecimal;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        String name,
        String phone,
        String address,
        BigDecimal creditLimit,
        boolean active,
        BigDecimal balance) {

    public static CustomerResponse from(Customer customer, BigDecimal balance) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getCreditLimit(),
                customer.isActive(),
                balance);
    }
}
