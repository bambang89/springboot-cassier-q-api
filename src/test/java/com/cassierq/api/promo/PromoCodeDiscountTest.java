package com.cassierq.api.promo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.domain.entity.PromoCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PromoCodeDiscountTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private static PromoCode promo(String type, String value) {
        return PromoCode.builder().code("HEMAT").name("Hemat").discountType(type)
                .discountValue(new BigDecimal(value)).active(true).build();
    }

    @Test
    void percentIsCappedByMaxDiscount() {
        PromoCode p = promo("PERCENT", "10");
        assertThat(PromoCodeService.discountFor(p, new BigDecimal("150000"), NOW)).isEqualByComparingTo("15000");
        p.setMaxDiscountAmount(new BigDecimal("10000"));
        assertThat(PromoCodeService.discountFor(p, new BigDecimal("150000"), NOW)).isEqualByComparingTo("10000");
    }

    @Test
    void amountNeverExceedsSubtotal() {
        assertThat(PromoCodeService.discountFor(promo("AMOUNT", "50000"), new BigDecimal("30000"), NOW))
                .isEqualByComparingTo("30000");
    }

    @Test
    void rejectsBelowMinimumExpiredExhaustedOrInactive() {
        PromoCode min = promo("AMOUNT", "5000");
        min.setMinPurchaseAmount(new BigDecimal("100000"));
        assertThatThrownBy(() -> PromoCodeService.discountFor(min, new BigDecimal("99000"), NOW))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Minimal belanja");

        PromoCode expired = promo("AMOUNT", "5000");
        expired.setEndsAt(NOW.minusSeconds(1));
        assertThatThrownBy(() -> PromoCodeService.discountFor(expired, BigDecimal.TEN, NOW))
                .hasMessageContaining("kedaluwarsa");

        PromoCode exhausted = promo("AMOUNT", "5000");
        exhausted.setUsageLimit(3);
        exhausted.setUsedCount(3);
        assertThatThrownBy(() -> PromoCodeService.discountFor(exhausted, BigDecimal.TEN, NOW))
                .hasMessageContaining("Kuota");

        PromoCode inactive = promo("AMOUNT", "5000");
        inactive.setActive(false);
        assertThatThrownBy(() -> PromoCodeService.discountFor(inactive, BigDecimal.TEN, NOW))
                .hasMessageContaining("tidak aktif");
    }
}
