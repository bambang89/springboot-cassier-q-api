package com.cassierq.api.catalog.bundle;

import com.cassierq.api.catalog.CatalogScope;
import com.cassierq.api.catalog.ProductService;
import com.cassierq.api.catalog.dto.BundleItemRequest;
import com.cassierq.api.catalog.dto.BundleRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.storage.PublicUrls;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductBundleItem;
import com.cassierq.api.domain.entity.ProductPrice;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductBundleItemRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paket bundling: a product of type BUNDLE that holds other products. Managed by the
 * store head only (enforced on {@link BundleController}). A bundle has no stock of its
 * own — selling it takes stock from its items (see OrderService).
 */
@Service
@RequiredArgsConstructor
public class BundleService {

    private static final String BUNDLE_UNIT_CODE = "PAKET";

    private final CatalogScope catalogScope;
    private final ProductService productService;
    private final BundleComposition composition;
    private final ProductRepository productRepository;
    private final ProductBundleItemRepository bundleItemRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final ProductPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final UnitRepository unitRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    /** All bundles in the store's catalog, with items and available stock. */
    @Transactional(readOnly = true)
    public List<ProductResponse> list(AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        return productRepository.findByOwnerStoreIdAndProductTypeAndDeletedAtIsNullOrderByProductNameAsc(owner, "BUNDLE")
                .stream()
                .map(p -> productService.toResponse(p, principal.getPrimaryStoreId()))
                .toList();
    }

    /** Creates a bundle product with its items, price and (unused) stock row. */
    @Transactional
    public ProductResponse create(BundleRequest request, AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        UUID storeId = principal.getPrimaryStoreId();
        checkUnique(owner, request, null);

        Store store = storeRepository.getReferenceById(storeId);
        User creator = userRepository.getReferenceById(principal.getUserId());
        Unit bundleUnit = bundleUnit(owner);

        Product bundle = productRepository.save(Product.builder()
                .sku(request.sku().trim())
                .barcode(blankToNull(request.barcode()))
                .productName(request.productName().trim())
                .ownerStoreId(owner)
                .category(catalogScope.category(request.categoryId(), owner))
                .description(request.description())
                .imageUrl(PublicUrls.toStored(request.imageUrl()))
                .baseUnit(bundleUnit)
                .status("ACTIVE")
                .productType("BUNDLE")
                .createdBy(creator)
                .build());

        conversionRepository.save(ProductUnitConversion.builder()
                .product(bundle)
                .unit(bundleUnit)
                .conversionToBase(BigDecimal.ONE)
                .baseUnit(true)
                .purchaseUnit(false)
                .saleUnit(true)
                .build());

        List<ProductBundleItem> items = replaceItems(bundle, request.items(), owner);

        priceRepository.save(ProductPrice.builder()
                .store(store)
                .product(bundle)
                .sellingPrice(request.sellingPrice())
                .costPrice(composition.costPrice(items, storeId))
                .effectiveFrom(Instant.now())
                .active(true)
                .createdBy(creator)
                .build());

        // Stock is derived from the items; the row only keeps existing stock queries uniform.
        inventoryRepository.save(Inventory.builder()
                .store(store)
                .product(bundle)
                .quantityBaseUnit(BigDecimal.ZERO)
                .minimumStock(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build());

        return productService.toResponse(bundle, storeId);
    }

    /** Updates a bundle's details, items and price (cost price is recomputed from the items). */
    @Transactional
    public ProductResponse update(UUID id, BundleRequest request, AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        UUID storeId = principal.getPrimaryStoreId();
        Product bundle = requireBundle(id, owner);
        checkUnique(owner, request, id);

        bundle.setSku(request.sku().trim());
        bundle.setBarcode(blankToNull(request.barcode()));
        bundle.setProductName(request.productName().trim());
        bundle.setCategory(catalogScope.category(request.categoryId(), owner));
        bundle.setDescription(request.description());
        bundle.setImageUrl(PublicUrls.toStored(request.imageUrl()));
        productRepository.save(bundle);

        List<ProductBundleItem> items = replaceItems(bundle, request.items(), owner);
        updatePrice(bundle, storeId, request.sellingPrice(), composition.costPrice(items, storeId), principal.getUserId());
        return productService.toResponse(bundle, storeId);
    }

    /** Removes a bundle from sale (soft delete; past sales keep referencing it). */
    @Transactional
    public void delete(UUID id, AppUserPrincipal principal) {
        Product bundle = requireBundle(id, catalogScope.ownerOf(principal));
        bundle.setStatus("INACTIVE");
        bundle.setDeletedAt(Instant.now());
        productRepository.save(bundle);
    }

    private List<ProductBundleItem> replaceItems(Product bundle, List<BundleItemRequest> requests, UUID owner) {
        Set<String> seen = new HashSet<>();
        List<ProductBundleItem> items = new ArrayList<>();
        for (BundleItemRequest request : requests) {
            if (request.productId().equals(bundle.getId())) {
                throw new BadRequestException("Paket tidak bisa berisi dirinya sendiri");
            }
            Product component = catalogScope.product(request.productId(), owner);
            if (component.isBundle()) {
                throw new BadRequestException("Paket tidak bisa berisi paket lain (" + component.getProductName() + ")");
            }
            if (!"ACTIVE".equals(component.getStatus())) {
                throw new BadRequestException("Produk " + component.getProductName() + " tidak aktif");
            }
            Unit unit = catalogScope.unit(request.unitId(), owner);
            if (conversionRepository.findByProductIdAndUnitId(component.getId(), unit.getId()).isEmpty()) {
                throw new BadRequestException("Satuan " + unit.getUnitName() + " tidak berlaku untuk " + component.getProductName());
            }
            if (!seen.add(component.getId() + ":" + unit.getId())) {
                throw new BadRequestException(component.getProductName() + " (" + unit.getUnitName() + ") tercantum dua kali");
            }
            items.add(ProductBundleItem.builder()
                    .bundle(bundle)
                    .component(component)
                    .unit(unit)
                    .quantity(request.quantity())
                    .build());
        }
        bundleItemRepository.deleteByBundleId(bundle.getId());
        bundleItemRepository.flush();
        return bundleItemRepository.saveAll(items);
    }

    private void updatePrice(Product bundle, UUID storeId, BigDecimal sellingPrice, BigDecimal costPrice, UUID userId) {
        var current = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, bundle.getId());
        boolean unchanged = current.isPresent()
                && current.get().getSellingPrice().compareTo(sellingPrice) == 0
                && (current.get().getCostPrice() == null ? costPrice == null
                        : costPrice != null && current.get().getCostPrice().compareTo(costPrice) == 0);
        if (unchanged) return;
        Instant now = Instant.now();
        current.ifPresent(price -> {
            price.setEffectiveUntil(now);
            priceRepository.saveAndFlush(price);
        });
        priceRepository.save(ProductPrice.builder()
                .store(storeRepository.getReferenceById(storeId))
                .product(bundle)
                .sellingPrice(sellingPrice)
                .costPrice(costPrice)
                .effectiveFrom(now)
                .active(true)
                .createdBy(userRepository.getReferenceById(userId))
                .build());
    }

    /** The catalog's "Paket" unit, created on first use. */
    private Unit bundleUnit(UUID owner) {
        return unitRepository.findFirstByOwnerStoreIdAndUnitCodeIgnoreCase(owner, BUNDLE_UNIT_CODE)
                .orElseGet(() -> unitRepository.save(Unit.builder()
                        .unitCode(BUNDLE_UNIT_CODE)
                        .unitName("Paket")
                        .ownerStoreId(owner)
                        .createdAt(Instant.now())
                        .build()));
    }

    private void checkUnique(UUID owner, BundleRequest request, UUID currentId) {
        Product current = currentId != null ? productRepository.findById(currentId).orElse(null) : null;
        boolean skuChanged = current == null || !current.getSku().equalsIgnoreCase(request.sku().trim());
        if (skuChanged && productRepository.existsByOwnerStoreIdAndSkuIgnoreCase(owner, request.sku().trim())) {
            throw new ConflictException("SKU sudah dipakai");
        }
        String barcode = blankToNull(request.barcode());
        if (barcode != null) {
            boolean taken = currentId == null
                    ? productRepository.existsByOwnerStoreIdAndBarcodeAndDeletedAtIsNull(owner, barcode)
                    : productRepository.existsByOwnerStoreIdAndBarcodeAndDeletedAtIsNullAndIdNot(owner, barcode, currentId);
            if (taken) throw new ConflictException("Barcode sudah dipakai produk lain");
        }
    }

    private Product requireBundle(UUID id, UUID owner) {
        Product product = catalogScope.product(id, owner);
        if (!product.isBundle()) {
            throw new BadRequestException("Produk ini bukan paket");
        }
        return product;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
