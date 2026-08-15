package com.cassierq.api.common;

import com.cassierq.api.domain.entity.NumberSequence;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.repository.NumberSequenceRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates document numbers ({@code TRX-...}, {@code PO-...}, ...) from the
 * pre-seeded {@code number_sequences} row for a store, in the format the
 * existing data already uses: {@code {prefix}-{storeCode}-{yyMMdd}-{seq:6}}
 * (e.g. {@code TRX-STR001-260810-000001}). Shared by every module that
 * issues a document number — see {@code OrderService}, {@code
 * PurchaseOrderService} — so the locking/formatting logic lives in one place.
 */
@Service
@RequiredArgsConstructor
public class NumberSequenceService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);

    private final NumberSequenceRepository numberSequenceRepository;

    /** Caller must already be inside a transaction — this relies on the pessimistic lock held for its duration. */
    @Transactional
    public String next(Store store, String sequenceType) {
        NumberSequence sequence = numberSequenceRepository.findForUpdate(store.getId(), sequenceType)
                .orElseThrow(() -> new IllegalStateException(
                        "number_sequences belum di-seed untuk store " + store.getId() + " / " + sequenceType));
        long next = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(next);
        sequence.setUpdatedAt(Instant.now());
        numberSequenceRepository.save(sequence);

        String datePart = DATE_FORMAT.format(Instant.now());
        return "%s-%s-%s-%06d".formatted(sequence.getPrefix(), store.getStoreCode(), datePart, next);
    }
}
