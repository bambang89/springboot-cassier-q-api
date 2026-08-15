package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByStoreId(UUID storeId);

    Optional<Customer> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndCustomerCodeIgnoreCase(UUID storeId, String customerCode);
}
