package com.cassierq.api.domain.repository;

import com.cassierq.api.domain.entity.Payment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByTransactionId(UUID transactionId);

    // Uang tunai yang benar-benar tinggal di laci = uang diterima - kembalian.
    // payments.amount adalah uang yang DISERAHKAN pelanggan (mis. 100rb untuk
    // belanja 73rb), jadi kembaliannya harus dikurangkan. Transaksi void
    // (payment REFUNDED) tidak ikut dihitung.
    @Query("""
            select coalesce(sum(p.amount - p.transaction.changeAmount), 0) from Payment p
            where p.transaction.cashierSession.id = :sessionId
              and p.paymentMethod = 'CASH' and p.paymentStatus = 'SUCCESS'
              and p.transaction.transactionStatus = 'PAID'
            """)
    BigDecimal sumCashPaymentsForSession(UUID sessionId);
}
