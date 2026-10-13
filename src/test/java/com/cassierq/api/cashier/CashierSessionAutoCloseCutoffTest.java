package com.cassierq.api.cashier;

import static org.assertj.core.api.Assertions.assertThat;

import com.cassierq.api.config.BusinessProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CashierSessionAutoCloseCutoffTest {

    private final CashierSessionService service = new CashierSessionService(
            null, null, null, null,
            new BusinessProperties("Asia/Jakarta", new BigDecimal("0.11"), BigDecimal.TEN, LocalTime.of(23, 0)));

    @Test
    void beforeElevenPmUsesYesterdaysCutoff() {
        // 22:59 WIB on 13 Oct = 15:59 UTC → last cut-off was 12 Oct 23:00 WIB (16:00 UTC)
        assertThat(service.lastAutoCloseCutoff(Instant.parse("2026-10-13T15:59:00Z")))
                .isEqualTo(Instant.parse("2026-10-12T16:00:00Z"));
    }

    @Test
    void atAndAfterElevenPmUsesTodaysCutoff() {
        assertThat(service.lastAutoCloseCutoff(Instant.parse("2026-10-13T16:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-13T16:00:00Z"));
        // 01:00 WIB on 14 Oct still belongs to the 13 Oct 23:00 cut-off
        assertThat(service.lastAutoCloseCutoff(Instant.parse("2026-10-13T18:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-13T16:00:00Z"));
    }
}
