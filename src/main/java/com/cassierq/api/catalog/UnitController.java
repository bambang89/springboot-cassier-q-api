package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.UnitRequest;
import com.cassierq.api.catalog.dto.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Units", description = "Satuan produk (pcs, dus, kg, ...) — katalog global, tidak per-toko")
public class UnitController {

    private final UnitService unitService;

    @GetMapping
    @Operation(summary = "Daftar semua satuan")
    public List<UnitResponse> list() {
        return unitService.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Buat satuan baru")
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Perbarui satuan")
    public UnitResponse update(@PathVariable UUID id, @Valid @RequestBody UnitRequest request) {
        return unitService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Hapus satuan (gagal 409 jika masih dipakai produk)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        unitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
