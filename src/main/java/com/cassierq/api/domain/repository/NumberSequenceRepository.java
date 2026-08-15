package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.NumberSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, UUID> {

    // Locked so two concurrent sales at the same store can't both read (and
    // then both write) the same next transaction_number.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from NumberSequence s where s.store.id = :storeId and s.sequenceType = :sequenceType")
    Optional<NumberSequence> findForUpdate(UUID storeId, String sequenceType);
}
