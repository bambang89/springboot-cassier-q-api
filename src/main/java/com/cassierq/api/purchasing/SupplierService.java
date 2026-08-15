package com.cassierq.api.purchasing;

import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Supplier;
import com.cassierq.api.domain.repository.SupplierRepository;
import com.cassierq.api.purchasing.dto.SupplierRequest;
import com.cassierq.api.purchasing.dto.SupplierResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return supplierRepository.findAll().stream().map(SupplierResponse::from).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        if (supplierRepository.existsBySupplierCodeIgnoreCase(request.supplierCode())) {
            throw new ConflictException("Kode supplier sudah dipakai");
        }
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .supplierCode(request.supplierCode())
                .supplierName(request.supplierName())
                .contactPerson(request.contactPerson())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .active(true)
                .build());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse update(UUID id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan"));

        if (!supplier.getSupplierCode().equalsIgnoreCase(request.supplierCode())
                && supplierRepository.existsBySupplierCodeIgnoreCase(request.supplierCode())) {
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
    public void deactivate(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan"));
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }
}
