package com.cassierq.api.purchasing;

import com.cassierq.api.purchasing.dto.SupplierRequest;
import com.cassierq.api.purchasing.dto.SupplierResponse;
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
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Suppliers", description = "Pemasok barang (katalog global, tidak per-toko)")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "Daftar semua supplier")
    public List<SupplierResponse> list() {
        return supplierService.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'GUDANG')")
    @Operation(summary = "Tambah supplier baru")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'GUDANG')")
    @Operation(summary = "Perbarui data supplier")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'GUDANG')")
    @Operation(summary = "Nonaktifkan supplier")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        supplierService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
