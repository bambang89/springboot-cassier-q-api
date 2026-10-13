package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.UnitRequest;
import com.cassierq.api.catalog.dto.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import com.cassierq.api.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Units", description = "Satuan produk (pcs, dus, kg, ...) — milik toko induk, dipakai bersama cabangnya")
public class UnitController {

    private final UnitService unitService;

    @GetMapping
    @Operation(summary = "Daftar semua satuan")
    public List<UnitResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return unitService.list(principal);
    }

    @PostMapping
    @PreAuthorize("@actionAccess.allowed(principal, 'units.create')")
    @Operation(summary = "Buat satuan baru")
    public ResponseEntity<UnitResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.create(principal, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@actionAccess.allowed(principal, 'units.update')")
    @Operation(summary = "Perbarui satuan")
    public UnitResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UnitRequest request) {
        return unitService.update(principal, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@actionAccess.allowed(principal, 'units.delete')")
    @Operation(summary = "Hapus satuan (gagal 409 jika masih dipakai produk)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        unitService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
