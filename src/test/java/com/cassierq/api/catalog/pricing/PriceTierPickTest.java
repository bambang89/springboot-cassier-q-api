package com.cassierq.api.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.cassierq.api.catalog.dto.PriceTierDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceTierPickTest {

    // Nabati: normal 1.800; ≥10 → 1.700; ≥20 → 1.650
    private final List<PriceTierDto> nabati = List.of(
            new PriceTierDto(new BigDecimal("10"), new BigDecimal("1700")),
            new PriceTierDto(new BigDecimal("20"), new BigDecimal("1650")));
    private final BigDecimal normal = new BigDecimal("1800");

    private BigDecimal priceFor(String qty) {
        return PriceTierService.pick(nabati, new BigDecimal(qty), normal);
    }

    @Test
    void picksTheHighestReachedStep() {
        assertThat(priceFor("1")).isEqualByComparingTo("1800");
        assertThat(priceFor("9")).isEqualByComparingTo("1800");
        assertThat(priceFor("10")).isEqualByComparingTo("1700");
        assertThat(priceFor("19")).isEqualByComparingTo("1700");
        assertThat(priceFor("20")).isEqualByComparingTo("1650");
        // 1 dus isi 20 + 5 pcs = 25 base units
        assertThat(priceFor("25")).isEqualByComparingTo("1650");
    }

    @Test
    void noStepsMeansNormalPrice() {
        assertThat(PriceTierService.pick(List.of(), new BigDecimal("100"), normal)).isEqualByComparingTo("1800");
    }
}
