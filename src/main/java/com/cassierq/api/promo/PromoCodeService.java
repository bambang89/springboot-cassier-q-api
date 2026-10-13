package com.cassierq.api.promo;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.PromoCode;
import com.cassierq.api.domain.repository.PromoCodeRepository;
import com.cassierq.api.domain.repository.SalesTransactionRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.promo.dto.PromoCodeRequest;
import com.cassierq.api.promo.dto.PromoCodeResponse;
import com.cassierq.api.promo.dto.PromoQuoteResponse;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Promo codes per store: owner CRUD, POS validation and redemption inside a sale. */
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromoCodeRepository promoCodeRepository;
    private final StoreRepository storeRepository;
    private final SalesTransactionRepository transactionRepository;

    /** All promo codes of the caller's store, newest first. */
    @Transactional(readOnly = true)
    public List<PromoCodeResponse> list(AppUserPrincipal principal) {
        return promoCodeRepository.findByStoreIdOrderByCreatedAtDesc(requireStore(principal)).stream()
                .map(PromoCodeResponse::from)
                .toList();
    }

    /** Creates a promo code; codes are stored uppercase and unique per store. */
    @Transactional
    public PromoCodeResponse create(AppUserPrincipal principal, PromoCodeRequest request) {
        UUID storeId = requireStore(principal);
        String code = normalize(request.code());
        if (promoCodeRepository.existsByStoreIdAndCode(storeId, code)) {
            throw new ConflictException("Kode promo " + code + " sudah dipakai");
        }
        PromoCode promo = PromoCode.builder().store(storeRepository.getReferenceById(storeId)).build();
        apply(promo, request, code);
        return PromoCodeResponse.from(promoCodeRepository.save(promo));
    }

    /** Updates a promo code; the usage counter is kept. */
    @Transactional
    public PromoCodeResponse update(AppUserPrincipal principal, UUID id, PromoCodeRequest request) {
        UUID storeId = requireStore(principal);
        PromoCode promo = require(storeId, id);
        String code = normalize(request.code());
        if (!promo.getCode().equals(code) && promoCodeRepository.existsByStoreIdAndCode(storeId, code)) {
            throw new ConflictException("Kode promo " + code + " sudah dipakai");
        }
        apply(promo, request, code);
        return PromoCodeResponse.from(promoCodeRepository.save(promo));
    }

    /** Deletes an unused promo code; one that was already redeemed is only deactivated (sales keep the reference). */
    @Transactional
    public void delete(AppUserPrincipal principal, UUID id) {
        PromoCode promo = require(requireStore(principal), id);
        if (transactionRepository.existsByPromoCodeId(id)) {
            promo.setActive(false);
            promoCodeRepository.save(promo);
        } else {
            promoCodeRepository.delete(promo);
        }
    }

    /** POS check: is the code usable for this subtotal, and how much does it take off. */
    @Transactional(readOnly = true)
    public PromoQuoteResponse quote(AppUserPrincipal principal, String rawCode, BigDecimal subtotal) {
        PromoCode promo = promoCodeRepository.findByStoreIdAndCode(requireStore(principal), normalize(rawCode))
                .orElseThrow(() -> new BadRequestException("Kode promo tidak ditemukan"));
        BigDecimal discount = discountFor(promo, subtotal, Instant.now());
        return new PromoQuoteResponse(promo.getCode(), promo.getName(), describe(promo), discount);
    }

    /**
     * Redeems a code inside a sale: re-validates it under a row lock and counts the use.
     * Must run in the caller's transaction so a failed sale rolls the count back.
     */
    @Transactional
    public Redemption redeem(UUID storeId, String rawCode, BigDecimal subtotal) {
        PromoCode promo = promoCodeRepository.findForUpdate(storeId, normalize(rawCode))
                .orElseThrow(() -> new BadRequestException("Kode promo tidak ditemukan"));
        BigDecimal discount = discountFor(promo, subtotal, Instant.now());
        promo.setUsedCount(promo.getUsedCount() + 1);
        promoCodeRepository.save(promo);
        return new Redemption(promo, discount);
    }

    /** Gives one use back when a sale that redeemed the code is voided. */
    @Transactional
    public void release(UUID promoCodeId) {
        promoCodeRepository.findByIdForUpdate(promoCodeId).ifPresent(promo -> {
            promo.setUsedCount(Math.max(0, promo.getUsedCount() - 1));
            promoCodeRepository.save(promo);
        });
    }

    /** Discount this promo gives on {@code subtotal}, or a clear error when it cannot be used. */
    static BigDecimal discountFor(PromoCode promo, BigDecimal subtotal, Instant now) {
        if (!promo.isActive()) {
            throw new BadRequestException("Kode promo sudah tidak aktif");
        }
        if (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt())) {
            throw new BadRequestException("Kode promo belum berlaku");
        }
        if (promo.getEndsAt() != null && now.isAfter(promo.getEndsAt())) {
            throw new BadRequestException("Kode promo sudah kedaluwarsa");
        }
        if (promo.getUsageLimit() != null && promo.getUsedCount() >= promo.getUsageLimit()) {
            throw new BadRequestException("Kuota kode promo sudah habis");
        }
        if (promo.getMinPurchaseAmount() != null && subtotal.compareTo(promo.getMinPurchaseAmount()) < 0) {
            throw new BadRequestException("Minimal belanja Rp" + rupiah(promo.getMinPurchaseAmount()) + " untuk kode ini");
        }
        BigDecimal discount = "PERCENT".equals(promo.getDiscountType())
                ? subtotal.multiply(promo.getDiscountValue()).divide(HUNDRED, 0, RoundingMode.HALF_UP)
                : promo.getDiscountValue();
        if (promo.getMaxDiscountAmount() != null && discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
            discount = promo.getMaxDiscountAmount();
        }
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private void apply(PromoCode promo, PromoCodeRequest request, String code) {
        if ("PERCENT".equals(request.discountType()) && request.discountValue().compareTo(HUNDRED) > 0) {
            throw new BadRequestException("Diskon persen maksimal 100%");
        }
        if (request.startsAt() != null && request.endsAt() != null && !request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("Tanggal berakhir harus setelah tanggal mulai");
        }
        promo.setCode(code);
        promo.setName(request.name().trim());
        promo.setDiscountType(request.discountType());
        promo.setDiscountValue(request.discountValue());
        promo.setMaxDiscountAmount("PERCENT".equals(request.discountType()) ? positiveOrNull(request.maxDiscountAmount()) : null);
        promo.setMinPurchaseAmount(positiveOrNull(request.minPurchaseAmount()));
        promo.setStartsAt(request.startsAt());
        promo.setEndsAt(request.endsAt());
        promo.setUsageLimit(request.usageLimit());
        promo.setActive(request.active() == null || request.active());
    }

    /** Short human description, e.g. "Diskon 10% (maks. Rp20.000)". */
    private static String describe(PromoCode promo) {
        String base = "PERCENT".equals(promo.getDiscountType())
                ? "Diskon " + promo.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : "Potongan Rp" + rupiah(promo.getDiscountValue());
        return promo.getMaxDiscountAmount() != null ? base + " (maks. Rp" + rupiah(promo.getMaxDiscountAmount()) + ")" : base;
    }

    private static String rupiah(BigDecimal value) {
        return String.format(Locale.forLanguageTag("id-ID"), "%,d", value.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private PromoCode require(UUID storeId, UUID id) {
        return promoCodeRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Kode promo tidak ditemukan"));
    }

    private UUID requireStore(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Pilih toko terlebih dahulu");
        }
        return storeId;
    }

    /** A redeemed promo and the discount it gave. */
    public record Redemption(PromoCode promo, BigDecimal discount) {
    }
}
