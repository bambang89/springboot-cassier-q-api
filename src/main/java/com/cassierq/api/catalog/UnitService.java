package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.UnitRequest;
import com.cassierq.api.catalog.dto.UnitResponse;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.security.AppUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final UnitRepository unitRepository;
    private final CatalogScope catalogScope;

    @Transactional(readOnly = true)
    public List<UnitResponse> list(AppUserPrincipal principal) {
        UUID owner = catalogScope.ownerOf(principal);
        return unitRepository.findByOwnerStoreId(owner).stream().map(UnitResponse::from).toList();
    }

    @Transactional
    public UnitResponse create(AppUserPrincipal principal, UnitRequest request) {
        UUID owner = catalogScope.requireWriter(principal);
        if (unitRepository.existsByOwnerStoreIdAndUnitCodeIgnoreCase(owner, request.unitCode())) {
            throw new ConflictException("Kode satuan sudah dipakai");
        }
        Unit unit = unitRepository.save(Unit.builder()
                .unitCode(request.unitCode())
                .unitName(request.unitName())
                .ownerStoreId(owner)
                .createdAt(Instant.now())
                .build());
        return UnitResponse.from(unit);
    }

    @Transactional
    public UnitResponse update(AppUserPrincipal principal, UUID id, UnitRequest request) {
        UUID owner = catalogScope.requireWriter(principal);
        Unit unit = catalogScope.unit(id, owner);

        if (!unit.getUnitCode().equalsIgnoreCase(request.unitCode())
                && unitRepository.existsByOwnerStoreIdAndUnitCodeIgnoreCase(owner, request.unitCode())) {
            throw new ConflictException("Kode satuan sudah dipakai");
        }

        unit.setUnitCode(request.unitCode());
        unit.setUnitName(request.unitName());
        unitRepository.save(unit);
        return UnitResponse.from(unit);
    }

    @Transactional
    public void delete(AppUserPrincipal principal, UUID id) {
        UUID owner = catalogScope.requireWriter(principal);
        // Products/conversions/order & PO items referencing this unit surface as a
        // 409 via GlobalExceptionHandler's DataIntegrityViolationException handler.
        unitRepository.delete(catalogScope.unit(id, owner));
    }
}
