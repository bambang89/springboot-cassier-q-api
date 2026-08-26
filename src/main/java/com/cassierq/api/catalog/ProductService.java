package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.ProductImageResponse;
import com.cassierq.api.catalog.dto.ProductRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.catalog.dto.ProductUnitResponse;
import com.cassierq.api.catalog.dto.RegisterProductUnitRequest;
import com.cassierq.api.catalog.dto.UnitConversionResponse;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.common.storage.FileStorageService;
import com.cassierq.api.domain.entity.Inventory;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductCategory;
import com.cassierq.api.domain.entity.ProductImage;
import com.cassierq.api.domain.entity.ProductPrice;
import com.cassierq.api.domain.entity.ProductUnitConversion;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.repository.InventoryRepository;
import com.cassierq.api.domain.repository.ProductCategoryRepository;
import com.cassierq.api.domain.repository.ProductImageRepository;
import com.cassierq.api.domain.repository.ProductPriceRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.ProductUnitConversionRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductUnitConversionRepository conversionRepository;
    private final ProductPriceRepository priceRepository;
    private final InventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, UUID storeId, Pageable pageable) {
        Page<Product> products = productRepository.search(search, pageable);
        return PageResponse.of(products.map(p -> toResponse(p, storeId)));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(String barcode, UUID storeId) {
        Product product = productRepository.findByBarcodeAndDeletedAtIsNull(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Produk dengan barcode tersebut tidak ditemukan"));
        return toResponse(product, storeId);
    }

    @Transactional
    public ProductResponse create(ProductRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal.getPrimaryStoreId());

        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new ConflictException("SKU sudah dipakai");
        }

        ProductCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
        Unit baseUnit = unitRepository.findById(request.baseUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
        Store store = storeRepository.getReferenceById(storeId);
        User creator = userRepository.getReferenceById(principal.getUserId());

        Product product = productRepository.save(Product.builder()
                .sku(request.sku())
                .barcode(request.barcode())
                .productName(request.productName())
                .category(category)
                .brand(request.brand())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .baseUnit(baseUnit)
                .status("ACTIVE")
                .createdBy(creator)
                .build());

        conversionRepository.save(ProductUnitConversion.builder()
                .product(product)
                .unit(baseUnit)
                .conversionToBase(BigDecimal.ONE)
                .baseUnit(true)
                .purchaseUnit(true)
                .saleUnit(true)
                .build());

        priceRepository.save(ProductPrice.builder()
                .store(store)
                .product(product)
                .sellingPrice(request.sellingPrice())
                .costPrice(request.costPrice())
                .effectiveFrom(Instant.now())
                .active(true)
                .createdBy(creator)
                .build());

        inventoryRepository.save(Inventory.builder()
                .store(store)
                .product(product)
                .quantityBaseUnit(BigDecimal.ZERO)
                .minimumStock(BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build());

        return toResponse(product, storeId);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request, AppUserPrincipal principal) {
        UUID storeId = requireStore(principal.getPrimaryStoreId());

        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));

        if (!product.getSku().equalsIgnoreCase(request.sku()) && productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new ConflictException("SKU sudah dipakai");
        }

        ProductCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
        Unit baseUnit = unitRepository.findById(request.baseUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));

        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setProductName(request.productName());
        product.setCategory(category);
        product.setBrand(request.brand());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setBaseUnit(baseUnit);
        productRepository.save(product);

        updatePriceIfChanged(product, storeId, request.sellingPrice(), request.costPrice(), principal.getUserId());

        return toResponse(product, storeId);
    }

    @Transactional
    public ProductResponse uploadPhoto(UUID id, MultipartFile file, UUID storeId) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));

        String relativePath = fileStorageService.storeImage("products/" + id, file);
        String publicUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(relativePath)
                .toUriString();

        product.setImageUrl(publicUrl);
        productRepository.save(product);

        return toResponse(product, storeId);
    }

    /** Galeri foto tambahan produk, di luar `imageUrl` (foto utama) — lihat {@link #uploadPhoto}. */
    @Transactional(readOnly = true)
    public List<ProductImageResponse> listPhotos(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Produk tidak ditemukan");
        }
        return productImageRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(productId).stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    @Transactional
    public ProductImageResponse addPhoto(UUID productId, MultipartFile file, AppUserPrincipal principal) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));

        String relativePath = fileStorageService.storeImage("products/" + productId, file);
        String publicUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(relativePath)
                .toUriString();

        ProductImage image = productImageRepository.save(ProductImage.builder()
                .product(product)
                .imageUrl(publicUrl)
                .sortOrder((int) productImageRepository.countByProductId(productId))
                .createdBy(userRepository.getReferenceById(principal.getUserId()))
                .build());

        return ProductImageResponse.from(image);
    }

    @Transactional
    public void deletePhoto(UUID productId, UUID photoId) {
        ProductImage image = productImageRepository.findById(photoId)
                .filter(img -> img.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Foto tidak ditemukan"));
        productImageRepository.delete(image);
    }

    @Transactional(readOnly = true)
    public UnitConversionResponse convert(UUID productId, UUID unitId, BigDecimal quantity) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
        ProductUnitConversion conversion = conversionRepository.findByProductIdAndUnitId(productId, unitId)
                .orElseThrow(() -> new BadRequestException("Satuan tersebut tidak berlaku untuk produk " + product.getProductName()));

        BigDecimal quantityBaseUnit = quantity.multiply(conversion.getConversionToBase());

        return new UnitConversionResponse(
                product.getId(),
                unit.getId(),
                unit.getUnitName(),
                quantity,
                product.getBaseUnit().getId(),
                product.getBaseUnit().getUnitName(),
                conversion.getConversionToBase(),
                quantityBaseUnit);
    }

    /** Every unit registered for a product — the base unit (conversion 1) plus any alternates. */
    @Transactional(readOnly = true)
    public List<ProductUnitResponse> listUnits(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Produk tidak ditemukan");
        }
        return conversionRepository.findByProductId(productId).stream()
                .map(ProductUnitResponse::from)
                .toList();
    }

    @Transactional
    public ProductUnitResponse registerUnit(UUID productId, RegisterProductUnitRequest request) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
        Unit unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));

        if (product.getBaseUnit().getId().equals(unit.getId())) {
            throw new ConflictException("Satuan ini sudah jadi satuan dasar produk (konversi 1) sejak produk dibuat");
        }
        if (conversionRepository.findByProductIdAndUnitId(productId, unit.getId()).isPresent()) {
            throw new ConflictException("Satuan ini sudah terdaftar untuk produk ini");
        }

        ProductUnitConversion conversion = conversionRepository.save(ProductUnitConversion.builder()
                .product(product)
                .unit(unit)
                .conversionToBase(request.conversionToBase())
                .baseUnit(false)
                .purchaseUnit(request.purchaseUnit() == null || request.purchaseUnit())
                .saleUnit(request.saleUnit() == null || request.saleUnit())
                .build());

        return ProductUnitResponse.from(conversion);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
        // Soft delete — products may already be referenced by past sales_transaction_items.
        product.setStatus("INACTIVE");
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    private void updatePriceIfChanged(Product product, UUID storeId, BigDecimal newSellingPrice, BigDecimal newCostPrice, UUID userId) {
        var current = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, product.getId());
        boolean unchanged = current.isPresent()
                && current.get().getSellingPrice().compareTo(newSellingPrice) == 0
                && java.util.Objects.equals(current.get().getCostPrice(), newCostPrice);
        if (unchanged) {
            return;
        }

        Instant now = Instant.now();
        current.ifPresent(price -> {
            price.setEffectiveUntil(now);
            priceRepository.save(price);
        });

        priceRepository.save(ProductPrice.builder()
                .store(storeRepository.getReferenceById(storeId))
                .product(product)
                .sellingPrice(newSellingPrice)
                .costPrice(newCostPrice)
                .effectiveFrom(now)
                .active(true)
                .createdBy(userRepository.getReferenceById(userId))
                .build());
    }

    private ProductResponse toResponse(Product product, UUID storeId) {
        BigDecimal sellingPrice = null;
        BigDecimal costPrice = null;
        var price = priceRepository.findByStoreIdAndProductIdAndEffectiveUntilIsNull(storeId, product.getId());
        if (price.isPresent()) {
            sellingPrice = price.get().getSellingPrice();
            costPrice = price.get().getCostPrice();
        }
        BigDecimal stock = inventoryRepository.findByStoreIdAndProductId(storeId, product.getId())
                .map(Inventory::getQuantityBaseUnit)
                .orElse(null);
        return ProductResponse.from(product, sellingPrice, costPrice, stock);
    }

    private UUID requireStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
