package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByStoreId(UUID storeId);

    Optional<Customer> findByIdAndStoreId(UUID id, UUID storeId);

    boolean existsByStoreIdAndCustomerCodeIgnoreCase(UUID storeId, String customerCode);

    // Serialisasi perubahan saldo satu pelanggan: dua penjualan kredit
    // bersamaan tidak boleh sama-sama lolos cek limit kredit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id and c.store.id = :storeId")
    Optional<Customer> findForUpdate(UUID id, UUID storeId);
}
