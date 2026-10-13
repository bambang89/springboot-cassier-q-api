package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.PromoCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    List<PromoCode> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    Optional<PromoCode> findByIdAndStoreId(UUID id, UUID storeId);

    Optional<PromoCode> findByStoreIdAndCode(UUID storeId, String code);

    boolean existsByStoreIdAndCode(UUID storeId, String code);

    /** Row-locks the code while a sale redeems it, so concurrent checkouts cannot exceed the usage limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where p.store.id = :storeId and p.code = :code")
    Optional<PromoCode> findForUpdate(@Param("storeId") UUID storeId, @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where p.id = :id")
    Optional<PromoCode> findByIdForUpdate(@Param("id") UUID id);
}
