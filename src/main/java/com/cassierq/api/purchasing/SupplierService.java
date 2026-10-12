package com.cassierq.api.purchasing;

import com.cassierq.api.catalog.CatalogScope;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.domain.entity.Supplier;
import com.cassierq.api.domain.repository.SupplierRepository;
import com.cassierq.api.purchasing.dto.SupplierRequest;
import com.cassierq.api.purchasing.dto.SupplierResponse;
import com.cassierq.api.security.AppUserPrincipal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Supplier dimiliki toko induk, sama seperti katalog produk — lihat {@link CatalogScope}. */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final CatalogScope catalogScope;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        return supplierRepository.findByOwnerStoreId(owner).stream().map(SupplierResponse::from).toList();
    }

    @Transactional
    public SupplierResponse create(AppUserPrincipal principal, SupplierRequest request) {
        UUID owner = catalogScope.ownerOf(principal);
        if (supplierRepository.existsByOwnerStoreIdAndSupplierCodeIgnoreCase(owner, request.supplierCode())) {
            throw new ConflictException("Kode supplier sudah dipakai");
        }
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .supplierCode(request.supplierCode())
                .supplierName(request.supplierName())
                .ownerStoreId(owner)
                .contactPerson(request.contactPerson())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .active(true)
                .build());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse update(AppUserPrincipal principal, UUID id, SupplierRequest request) {
        UUID owner = catalogScope.ownerOf(principal);
        Supplier supplier = catalogScope.supplier(id, owner);

        if (!supplier.getSupplierCode().equalsIgnoreCase(request.supplierCode())
                && supplierRepository.existsByOwnerStoreIdAndSupplierCodeIgnoreCase(owner, request.supplierCode())) {
            throw new ConflictException("Kode supplier sudah dipakai");
        }

        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        supplier.setContactPerson(request.contactPerson());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());
        supplierRepository.save(supplier);
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void deactivate(AppUserPrincipal principal, UUID id) {
        Supplier supplier = catalogScope.supplier(id, catalogScope.ownerOf(principal));
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }
}
