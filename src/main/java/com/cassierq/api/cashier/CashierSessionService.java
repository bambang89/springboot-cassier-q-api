package com.cassierq.api.cashier;

import com.cassierq.api.cashier.dto.CashierSessionResponse;
import com.cassierq.api.cashier.dto.CloseCashierSessionRequest;
import com.cassierq.api.cashier.dto.OpenCashierSessionRequest;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.config.BusinessProperties;
import com.cassierq.api.domain.entity.CashierSession;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.CashierSessionRepository;
import com.cassierq.api.domain.repository.PaymentRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashierSessionService {

    private final CashierSessionRepository cashierSessionRepository;
    private final PaymentRepository paymentRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final BusinessProperties businessProperties;

    @Transactional
    public CashierSessionResponse open(AppUserPrincipal principal, OpenCashierSessionRequest request) {
        UUID storeId = requireStore(principal);

        if (findActiveSession(principal.getUserId()).isPresent()) {
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

    @Transactional
    public CashierSessionResponse current(AppUserPrincipal principal) {
        CashierSession session = findActiveSession(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Tidak ada sesi kasir yang terbuka"));
        return CashierSessionResponse.from(session);
    }

    /**
     * The cashier's OPEN session, unless it has passed the daily auto-close time —
     * then it is closed right here and empty is returned. This keeps the cut-off exact
     * even between scheduler runs or after the server was down at that hour.
     */
    @Transactional
    public Optional<CashierSession> findActiveSession(UUID cashierId) {
        Optional<CashierSession> open = cashierSessionRepository.findByCashierIdAndStatus(cashierId, "OPEN");
        if (open.isPresent() && open.get().getOpenedAt().isBefore(lastAutoCloseCutoff(Instant.now()))) {
            autoClose(open.get());
            return Optional.empty();
        }
        return open;
    }

    /**
     * Closes every OPEN session that was opened before the most recent auto-close time
     * (default 23:00 store time). Called every minute by {@link CashierSessionAutoCloseJob}.
     *
     * @return number of sessions closed
     */
    @Transactional
    public int autoCloseExpiredSessions() {
        Instant cutoff = lastAutoCloseCutoff(Instant.now());
        int closed = 0;
        for (CashierSession session : cashierSessionRepository.findByStatusAndOpenedAtBefore("OPEN", cutoff)) {
            if (autoClose(session)) closed++;
        }
        if (closed > 0) {
            log.info("Auto-closed {} cashier session(s) opened before {}", closed, cutoff);
        }
        return closed;
    }

    /**
     * Closes one session at the auto-close cut-off. The drawer was not counted, so
     * actual cash and difference stay empty; expected cash is still recorded.
     *
     * @return false when another instance or a manual close got there first
     */
    private boolean autoClose(CashierSession session) {
        Instant closedAt = autoCloseInstantAfter(session.getOpenedAt());
        BigDecimal expectedCash = session.getOpeningCash()
                .add(paymentRepository.sumCashPaymentsForSession(session.getId()));
        String closeTime = businessProperties.cashierSessionAutoCloseOrDefault().format(DateTimeFormatter.ofPattern("HH:mm"));
        String notes = "Ditutup otomatis pukul " + closeTime + " (kas fisik belum dihitung)";
        return cashierSessionRepository.autoCloseIfOpen(session.getId(), closedAt, expectedCash, notes) > 0;
    }

    /** The most recent auto-close moment at or before {@code now}, in the store's time zone. */
    Instant lastAutoCloseCutoff(Instant now) {
        ZoneId zone = businessProperties.zoneId();
        LocalTime closeTime = businessProperties.cashierSessionAutoCloseOrDefault();
        ZonedDateTime today = now.atZone(zone).with(closeTime);
        return (today.toInstant().isAfter(now) ? today.minusDays(1) : today).toInstant();
    }

    /** The first auto-close moment after a session was opened — recorded as its close time. */
    private Instant autoCloseInstantAfter(Instant openedAt) {
        ZoneId zone = businessProperties.zoneId();
        ZonedDateTime sameDay = openedAt.atZone(zone).with(businessProperties.cashierSessionAutoCloseOrDefault());
        return (sameDay.toInstant().isAfter(openedAt) ? sameDay : sameDay.plusDays(1)).toInstant();
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
