package com.cassierq.api.settings;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.config.BusinessProperties;
import com.cassierq.api.domain.entity.StoreSetting;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.StoreSettingRepository;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.settings.dto.CheckoutSettingsResponse;
import com.cassierq.api.settings.dto.CheckoutSettingsResponse.PaymentMethodSetting;
import com.cassierq.api.settings.dto.UpdateCheckoutSettingsRequest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-store checkout settings kept in {@code store_settings}:
 * accepted payment methods, the preselected method and whether PPN applies.
 * A store that never saved them accepts cash only, defaults to cash and adds PPN.
 */
@Service
@RequiredArgsConstructor
public class CheckoutSettingsService {

    /** Payment methods the API supports, in display order (codes match CreateOrderRequest). */
    public static final Map<String, String> PAYMENT_METHODS = new LinkedHashMap<>();

    static {
        PAYMENT_METHODS.put("CASH", "Tunai");
        PAYMENT_METHODS.put("QRIS", "QRIS");
        PAYMENT_METHODS.put("DEBIT", "Kartu Debit");
        PAYMENT_METHODS.put("CREDIT_CARD", "Kartu Kredit");
        PAYMENT_METHODS.put("TRANSFER", "Transfer");
    }

    static final String KEY_PAYMENT_METHODS = "PAYMENT_METHODS_ENABLED";
    static final String KEY_DEFAULT_PAYMENT_METHOD = "PAYMENT_METHOD_DEFAULT";
    static final String KEY_TAX_ENABLED = "TAX_ENABLED";

    private static final String DEFAULT_METHOD = "CASH";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StoreSettingRepository storeSettingRepository;
    private final StoreRepository storeRepository;
    private final BusinessProperties businessProperties;

    /** Checkout settings of the caller's store (readable by every role, the POS needs them). */
    @Transactional(readOnly = true)
    public CheckoutSettingsResponse get(AppUserPrincipal principal) {
        return toResponse(requireStore(principal));
    }

    /** Saves the checkout settings (store owner only — enforced on the controller). */
    @Transactional
    public CheckoutSettingsResponse update(AppUserPrincipal principal, UpdateCheckoutSettingsRequest request) {
        UUID storeId = requireStore(principal);
        List<String> enabled = request.enabledPaymentMethods().stream()
                .map(code -> code.trim().toUpperCase())
                .distinct()
                .toList();
        for (String code : enabled) {
            if (!PAYMENT_METHODS.containsKey(code)) {
                throw new BadRequestException("Metode pembayaran tidak dikenal: " + code);
            }
        }
        String defaultMethod = request.defaultPaymentMethod().trim().toUpperCase();
        if (!enabled.contains(defaultMethod)) {
            throw new BadRequestException("Metode pembayaran default harus termasuk metode yang diaktifkan");
        }
        // Store in catalog order so every client lists them the same way.
        String value = String.join(",", PAYMENT_METHODS.keySet().stream().filter(enabled::contains).toList());

        save(storeId, KEY_PAYMENT_METHODS, value);
        save(storeId, KEY_DEFAULT_PAYMENT_METHOD, defaultMethod);
        save(storeId, KEY_TAX_ENABLED, String.valueOf(request.taxEnabled()));
        return toResponse(storeId);
    }

    /** Payment methods the store accepts; cash only when never configured. */
    @Transactional(readOnly = true)
    public List<String> enabledPaymentMethods(UUID storeId) {
        return read(storeId, KEY_PAYMENT_METHODS)
                .map(value -> Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(PAYMENT_METHODS::containsKey)
                        .toList())
                .filter(list -> !list.isEmpty())
                .orElse(List.of(DEFAULT_METHOD));
    }

    /** Whether PPN is added to sales in this store; enabled when never configured (previous behaviour). */
    @Transactional(readOnly = true)
    public boolean taxEnabled(UUID storeId) {
        return read(storeId, KEY_TAX_ENABLED).map(Boolean::parseBoolean).orElse(true);
    }

    private CheckoutSettingsResponse toResponse(UUID storeId) {
        List<String> enabled = enabledPaymentMethods(storeId);
        String defaultMethod = read(storeId, KEY_DEFAULT_PAYMENT_METHOD)
                .filter(enabled::contains)
                .orElse(enabled.contains(DEFAULT_METHOD) ? DEFAULT_METHOD : enabled.get(0));
        List<PaymentMethodSetting> methods = PAYMENT_METHODS.entrySet().stream()
                .map(e -> new PaymentMethodSetting(e.getKey(), e.getValue(), enabled.contains(e.getKey())))
                .toList();
        return new CheckoutSettingsResponse(
                methods,
                defaultMethod,
                taxEnabled(storeId),
                businessProperties.taxRate().multiply(HUNDRED).stripTrailingZeros());
    }

    private java.util.Optional<String> read(UUID storeId, String key) {
        return storeSettingRepository.findByStoreIdAndSettingKey(storeId, key)
                .map(StoreSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank());
    }

    private void save(UUID storeId, String key, String value) {
        StoreSetting setting = storeSettingRepository.findByStoreIdAndSettingKey(storeId, key)
                .orElseGet(() -> StoreSetting.builder()
                        .store(storeRepository.getReferenceById(storeId))
                        .settingKey(key)
                        .build());
        setting.setSettingValue(value);
        storeSettingRepository.save(setting);
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Pilih toko terlebih dahulu");
        }
        return storeId;
    }
}
