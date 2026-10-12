package com.cassierq.api.catalog;

import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Product;
import com.cassierq.api.domain.entity.ProductCategory;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Supplier;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.repository.ProductCategoryRepository;
import com.cassierq.api.domain.repository.ProductRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.SupplierRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Katalog (produk, kategori, satuan, supplier) dimiliki oleh toko induk
 * ({@code owner_store_id}). Cabang ({@code stores.parent_store_id} terisi)
 * memakai katalog induknya; toko tanpa parent — termasuk toko hasil
 * {@code /auth/register} — punya katalog sendiri yang terisolasi.
 *
 * <p>Semua lookup katalog lewat sini supaya user toko A tidak pernah bisa
 * membaca atau mengubah katalog toko B hanya dengan menebak id.
 */
@Component
@RequiredArgsConstructor
public class CatalogScope {

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final SupplierRepository supplierRepository;

    /** Id toko induk pemilik katalog yang dipakai user ini. */
    public UUID ownerOf(AppUserPrincipal principal) {
        UUID storeId = principal.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Toko tidak ditemukan"));
        return store.getParentStoreId() != null ? store.getParentStoreId() : store.getId();
    }

    /**
     * SUPERADMIN dan PRODUCT boleh mengubah katalog; KEPALA_TOKO hanya kalau
     * tokonya sendiri pemilik katalog (toko induk) — kepala cabang tidak boleh
     * mengubah katalog yang dipakai bersama cabang lain.
     */
    public UUID requireWriter(AppUserPrincipal principal) {
        UUID owner = ownerOf(principal);
        if (principal.isSuperadminUser() || principal.hasRole("PRODUCT")) {
            return owner;
        }
        if (principal.hasRole("KEPALA_TOKO") && owner.equals(principal.getPrimaryStoreId())) {
            return owner;
        }
        throw new AccessDeniedException("Katalog hanya bisa diubah oleh toko induk");
    }

    public Product product(UUID id, UUID owner) {
        return productRepository.findByIdAndOwnerStoreIdAndDeletedAtIsNull(id, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Produk tidak ditemukan"));
    }

    public ProductCategory category(UUID id, UUID owner) {
        return categoryRepository.findByIdAndOwnerStoreId(id, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Kategori tidak ditemukan"));
    }

    public Unit unit(UUID id, UUID owner) {
        return unitRepository.findByIdAndOwnerStoreId(id, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));
    }

    public Supplier supplier(UUID id, UUID owner) {
        return supplierRepository.findByIdAndOwnerStoreId(id, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan"));
    }
}
