package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.ProductRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Category;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.repository.CategoryRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(UUID storeId, String search, Pageable pageable) {
        Page<Product> page = StringUtils.hasText(search)
                ? productRepository.findByStoreIdAndNameContainingIgnoreCase(storeId, search, pageable)
                : productRepository.findByStoreId(storeId, pageable);
        return PageResponse.of(page.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByBarcode(UUID storeId, String barcode) {
        Product product = productRepository.findByStoreIdAndBarcode(storeId, barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Produk dengan barcode " + barcode + " tidak ditemukan"));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(UUID storeId, ProductRequest request) {
        if (productRepository.existsByStoreIdAndSkuIgnoreCase(storeId, request.sku())) {
            throw new ConflictException("SKU sudah digunakan");
        }

        Product product = Product.builder()
                .store(storeRepository.getReferenceById(storeId))
                .category(resolveCategory(storeId, request.categoryId()))
                .sku(request.sku())
                .barcode(request.barcode())
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .imageUrl(request.imageUrl())
                .build();

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(UUID storeId, UUID productId, ProductRequest request) {
        Product product = findOwned(storeId, productId);

        if (!product.getSku().equalsIgnoreCase(request.sku())
                && productRepository.existsByStoreIdAndSkuIgnoreCase(storeId, request.sku())) {
            throw new ConflictException("SKU sudah digunakan");
        }

        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setImageUrl(request.imageUrl());
        product.setCategory(resolveCategory(storeId, request.categoryId()));

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID storeId, UUID productId) {
        productRepository.delete(findOwned(storeId, productId));
    }

    private Product findOwned(UUID storeId, UUID productId) {
        return productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
    }

    private Category resolveCategory(UUID storeId, UUID categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .filter(c -> c.getStore().getId().equals(storeId))
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
    }
}
