package com.cassierq.api.catalog.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductBundleItem;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductBundleItemRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BundleCompositionTest {

    private final ProductBundleItemRepository items = mock(ProductBundleItemRepository.class);
    private final ProductUnitConversionRepository conversions = mock(ProductUnitConversionRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final BundleComposition composition =
            new BundleComposition(items, conversions, inventory, mock(ProductPriceRepository.class));

    private final UUID store = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();

    private ProductBundleItem item(String qty, String conversion, String stock) {
        Product component = Product.builder().id(UUID.randomUUID()).productName("Isi").build();
        Unit unit = Unit.builder().id(UUID.randomUUID()).unitName("pcs").build();
        when(conversions.findByProductIdAndUnitId(component.getId(), unit.getId()))
                .thenReturn(Optional.of(ProductUnitConversion.builder().conversionToBase(new BigDecimal(conversion)).build()));
        when(inventory.findByStoreIdAndProductId(eq(store), eq(component.getId())))
                .thenReturn(Optional.of(Inventory.builder().quantityBaseUnit(new BigDecimal(stock)).build()));
        return ProductBundleItem.builder().component(component).unit(unit).quantity(new BigDecimal(qty)).build();
    }

    @Test
    void availableStockIsLimitedByTheScarcestItem() {
        // 1 roti (stok 10), 2 susu (stok 6) → 3 paket; 1 dus selai isi 12 (stok 30 pcs) → 2 paket
        List<ProductBundleItem> contents = List.of(item("1", "1", "10"), item("2", "1", "6"), item("1", "12", "30"));
        when(items.findByBundleIdOrderByCreatedAtAsc(bundleId)).thenReturn(contents);
        assertThat(composition.availableStock(bundleId, store)).isEqualByComparingTo("2");
    }

    @Test
    void emptyBundleHasNoStock() {
        when(items.findByBundleIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        assertThat(composition.availableStock(bundleId, store)).isEqualByComparingTo("0");
    }
}
