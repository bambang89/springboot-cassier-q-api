package com.cassierq.api.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Aturan bisnis toko.
 *
 * @param timezone                   zona waktu "hari" toko — tanggal di nomor transaksi & batas hari laporan
 * @param taxRate                    tarif PPN; tax_amount dari client harus 0 atau sesuai tarif ini
 * @param cashierMaxDiscountPercent  batas diskon per transaksi untuk role selain SUPERADMIN/KEPALA_TOKO
 */
@ConfigurationProperties(prefix = "app.business")
public record BusinessProperties(String timezone, BigDecimal taxRate, BigDecimal cashierMaxDiscountPercent) {

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
