package com.cassierq.api.settings.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Checkout settings of a store.
 *
 * @param paymentMethods       every payment method with its label and whether the store accepts it
 * @param defaultPaymentMethod method preselected at checkout (always one of the enabled ones)
 * @param taxEnabled           whether PPN is added to every sale
 * @param taxRatePercent       PPN rate in percent (e.g. 11)
 */
public record CheckoutSettingsResponse(
        List<PaymentMethodSetting> paymentMethods,
        String defaultPaymentMethod,
        boolean taxEnabled,
        BigDecimal taxRatePercent) {

    public record PaymentMethodSetting(String code, String label, boolean enabled) {
    }
}
