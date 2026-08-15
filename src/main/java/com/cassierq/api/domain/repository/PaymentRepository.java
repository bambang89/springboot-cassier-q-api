package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByTransactionId(UUID transactionId);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.transaction.cashierSession.id = :sessionId
              and p.paymentMethod = 'CASH' and p.paymentStatus = 'SUCCESS'
            """)
    BigDecimal sumCashPaymentsForSession(UUID sessionId);
}
