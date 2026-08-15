package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.UnitRequest;
import com.cassierq.api.catalog.dto.UnitResponse;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.repository.UnitRepository;
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

    @Transactional(readOnly = true)
    public List<UnitResponse> list() {
        return unitRepository.findAll().stream().map(UnitResponse::from).toList();
    }

    @Transactional
    public UnitResponse create(UnitRequest request) {
        if (unitRepository.existsByUnitCodeIgnoreCase(request.unitCode())) {
            throw new ConflictException("Kode satuan sudah dipakai");
        }
        Unit unit = unitRepository.save(Unit.builder()
                .unitCode(request.unitCode())
                .unitName(request.unitName())
                .createdAt(Instant.now())
                .build());
        return UnitResponse.from(unit);
    }

    @Transactional
    public UnitResponse update(UUID id, UnitRequest request) {
        Unit unit = unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Satuan tidak ditemukan"));

        if (!unit.getUnitCode().equalsIgnoreCase(request.unitCode())
                && unitRepository.existsByUnitCodeIgnoreCase(request.unitCode())) {
            throw new ConflictException("Kode satuan sudah dipakai");
        }

        unit.setUnitCode(request.unitCode());
        unit.setUnitName(request.unitName());
        unitRepository.save(unit);
        return UnitResponse.from(unit);
    }

    @Transactional
    public void delete(UUID id) {
        if (!unitRepository.existsById(id)) {
            throw new ResourceNotFoundException("Satuan tidak ditemukan");
        }
        // Products/conversions/order & PO items referencing this unit surface as a
        // 409 via GlobalExceptionHandler's DataIntegrityViolationException handler.
        unitRepository.deleteById(id);
    }
}
