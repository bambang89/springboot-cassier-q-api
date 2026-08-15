package com.cassierq.api.cashier;

import com.cassierq.api.cashier.dto.CashierSessionResponse;
import com.cassierq.api.cashier.dto.CloseCashierSessionRequest;
import com.cassierq.api.cashier.dto.OpenCashierSessionRequest;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.CashierSession;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.CashierSessionRepository;
import com.cassierq.api.domain.repository.PaymentRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierSessionService {

    private final CashierSessionRepository cashierSessionRepository;
    private final PaymentRepository paymentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public CashierSessionResponse open(AppUserPrincipal principal, OpenCashierSessionRequest request) {
        UUID storeId = requireStore(principal);

        if (cashierSessionRepository.findByCashierIdAndStatus(principal.getUserId(), "OPEN").isPresent()) {
            throw new ConflictException("Anda masih punya sesi kasir yang terbuka");
        }

        Store store = storeRepository.getReferenceById(storeId);
        User cashier = userRepository.getReferenceById(principal.getUserId());

        CashierSession session = cashierSessionRepository.save(CashierSession.builder()
                .store(store)
                .cashier(cashier)
                .openedAt(Instant.now())
                .openingCash(request.openingCash())
                .status("OPEN")
                .build());

        return CashierSessionResponse.from(session);
    }

    @Transactional
    public CashierSessionResponse close(AppUserPrincipal principal, UUID sessionId, CloseCashierSessionRequest request) {
        CashierSession session = cashierSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesi kasir tidak ditemukan"));

        if (!session.getCashier().getId().equals(principal.getUserId())) {
            throw new BadRequestException("Sesi kasir ini bukan milik Anda");
        }
        if (!"OPEN".equals(session.getStatus())) {
            throw new BadRequestException("Sesi kasir sudah ditutup");
        }

        java.math.BigDecimal cashSales = paymentRepository.sumCashPaymentsForSession(sessionId);
        java.math.BigDecimal expectedCash = session.getOpeningCash().add(cashSales);

        session.setStatus("CLOSED");
        session.setClosedAt(Instant.now());
        session.setExpectedCash(expectedCash);
        session.setActualCash(request.actualCash());
        session.setCashDifference(request.actualCash().subtract(expectedCash));
        session.setNotes(request.notes());
        cashierSessionRepository.save(session);

        return CashierSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public CashierSessionResponse current(AppUserPrincipal principal) {
        CashierSession session = cashierSessionRepository.findByCashierIdAndStatus(principal.getUserId(), "OPEN")
                .orElseThrow(() -> new ResourceNotFoundException("Tidak ada sesi kasir yang terbuka"));
        return CashierSessionResponse.from(session);
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
