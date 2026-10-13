package com.cassierq.api.catalog.bundle;

import com.cassierq.api.catalog.dto.BundleItemResponse;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductBundleItem;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductBundleItemRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Read-side helpers for bundles shared by the product list, the bundle screens and
 * checkout: the items of a bundle, how much of each item one bundle uses, how many
 * bundles current stock allows and the bundle's cost price.
 */
@Component
@RequiredArgsConstructor
public class BundleComposition {

    private final ProductBundleItemRepository bundleItemRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductPriceRepository priceRepository;

    /** Items of a bundle, in the order they were added. */
    public List<ProductBundleItem> items(UUID bundleId) {
        return bundleItemRepository.findByBundleIdOrderByCreatedAtAsc(bundleId);
    }

    /** Base-unit quantity of an item used by ONE bundle (quantity × unit conversion). */
    public BigDecimal baseQuantityPerBundle(ProductBundleItem item) {
        BigDecimal conversion = conversionRepository
                .findByProductIdAndUnitId(item.getComponent().getId(), item.getUnit().getId())
                .map(c -> c.getConversionToBase())
                .orElse(BigDecimal.ONE);
        return item.getQuantity().multiply(conversion);
    }

    /** Items as API rows, with each item's current stock in {@code storeId}. */
    public List<BundleItemResponse> describe(UUID bundleId, UUID storeId) {
        return items(bundleId).stream()
                .map(item -> new BundleItemResponse(
                        item.getComponent().getId(),
                        item.getComponent().getProductName(),
                        item.getUnit().getId(),
                        item.getUnit().getUnitName(),
                        item.getQuantity(),
                        baseQuantityPerBundle(item),
                        stockOf(storeId, item.getComponent())))
                .toList();
    }

    /** Whole bundles that can be sold with the items' current stock (0 when an item is out). */
    public BigDecimal availableStock(UUID bundleId, UUID storeId) {
        BigDecimal available = null;
        for (ProductBundleItem item : items(bundleId)) {
            BigDecimal perBundle = baseQuantityPerBundle(item);
            if (perBundle.signum() <= 0) continue;
            BigDecimal possible = stockOf(storeId, item.getComponent()).divide(perBundle, 0, RoundingMode.FLOOR);
            available = available == null ? possible : available.min(possible);
        }
        return available == null ? BigDecimal.ZERO : available.max(BigDecimal.ZERO);
    }

    /** Cost price of one bundle = Σ item cost × base quantity; null if any item has no cost price. */
    public BigDecimal costPrice(List<ProductBundleItem> items, UUID storeId) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductBundleItem item : items) {
            var price = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, item.getComponent().getId());
            if (price.isEmpty() || price.get().getCostPrice() == null) return null;
            total = total.add(price.get().getCostPrice().multiply(baseQuantityPerBundle(item)));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal stockOf(UUID storeId, Product product) {
        return inventoryRepository.findByStoreIdAndProductId(storeId, product.getId())
                .map(Inventory::getQuantityBaseUnit)
                .orElse(BigDecimal.ZERO);
    }
}
