package com.cassierq.api.catalog.pricing;

import com.cassierq.api.catalog.CatalogScope;
import com.cassierq.api.catalog.dto.PriceTierDto;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductPriceTier;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductPriceTierRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wholesale ("grosir") tiered prices: the more base units of a product a sale holds,
 * the lower the price per base unit. Steps are per product per store.
 */
@Service
@RequiredArgsConstructor
public class PriceTierService {

    private final ProductPriceTierRepository tierRepository;
    private final ProductPriceRepository priceRepository;
    private final StoreRepository storeRepository;
    private final CatalogScope catalogScope;

    /** Price steps of a product in a store, lowest quantity first. */
    @Transactional(readOnly = true)
    public List<PriceTierDto> tiers(UUID storeId, UUID productId) {
        return tierRepository.findByStoreIdAndProductIdOrderByMinQuantityAsc(storeId, productId).stream()
                .map(t -> new PriceTierDto(t.getMinQuantity(), t.getUnitPrice()))
                .toList();
    }

    /** Replaces a product's price steps in the caller's store. */
    @Transactional
    public List<PriceTierDto> replace(UUID productId, List<PriceTierDto> requested, AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        Product product = catalogScope.product(productId, catalogScope.ownerOf(principal));
        if (product.isBundle()) {
            throw new BadRequestException("Harga grosir tidak berlaku untuk paket");
        }
        BigDecimal normalPrice = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, productId)
                .map(p -> p.getSellingPrice())
                .orElseThrow(() -> new BadRequestException("Produk belum punya harga di toko ini"));

        List<PriceTierDto> sorted = new ArrayList<>(requested);
        sorted.sort(Comparator.comparing(PriceTierDto::minQuantity));
        for (int i = 0; i < sorted.size(); i++) {
            PriceTierDto tier = sorted.get(i);
            if (tier.unitPrice().compareTo(normalPrice) >= 0) {
                throw new BadRequestException("Harga grosir untuk ≥" + plain(tier.minQuantity())
                        + " harus lebih murah dari harga normal");
            }
            if (i > 0) {
                PriceTierDto previous = sorted.get(i - 1);
                if (tier.minQuantity().compareTo(previous.minQuantity()) == 0) {
                    throw new BadRequestException("Minimal jumlah " + plain(tier.minQuantity()) + " tercantum dua kali");
                }
                if (tier.unitPrice().compareTo(previous.unitPrice()) > 0) {
                    throw new BadRequestException("Harga untuk ≥" + plain(tier.minQuantity())
                            + " tidak boleh lebih mahal dari tingkat sebelumnya");
                }
            }
        }

        tierRepository.deleteByStoreIdAndProductId(storeId, productId);
        tierRepository.flush();
        tierRepository.saveAll(sorted.stream()
                .map(t -> ProductPriceTier.builder()
                        .store(storeRepository.getReferenceById(storeId))
                        .product(product)
                        .minQuantity(t.minQuantity())
                        .unitPrice(t.unitPrice())
                        .build())
                .toList());
        return tiers(storeId, productId);
    }

    /**
     * Price per base unit for {@code totalBaseQuantity} of a product in a sale:
     * the step with the highest minimum the quantity reaches, else {@code normalPrice}.
     */
    @Transactional(readOnly = true)
    public BigDecimal basePriceFor(UUID storeId, UUID productId, BigDecimal totalBaseQuantity, BigDecimal normalPrice) {
        return pick(tierRepository.findByStoreIdAndProductIdOrderByMinQuantityAsc(storeId, productId).stream()
                .map(t -> new PriceTierDto(t.getMinQuantity(), t.getUnitPrice()))
                .toList(), totalBaseQuantity, normalPrice);
    }

    /** Picks the matching step from steps sorted by minimum quantity. */
    static BigDecimal pick(List<PriceTierDto> tiers, BigDecimal totalBaseQuantity, BigDecimal normalPrice) {
        BigDecimal price = normalPrice;
        for (PriceTierDto tier : tiers) {
            if (totalBaseQuantity.compareTo(tier.minQuantity()) >= 0) price = tier.unitPrice();
        }
        return price;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
