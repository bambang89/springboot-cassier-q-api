package com.cassierq.api.catalog.bundle;

import com.cassierq.api.catalog.dto.BundleRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Paket bundling — every endpoint is for the store head (KEPALA_TOKO) or SUPERADMIN only. */
@RestController
@RequestMapping("/api/v1/bundles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
@Tag(name = "Bundles", description = "Paket bundling (khusus Kepala Toko)")
public class BundleController {

    private final BundleService bundleService;

    @GetMapping
    @Operation(summary = "Daftar paket beserta isi dan stok yang tersedia")
    public List<ProductResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return bundleService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buat paket bundling")
    public ProductResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody BundleRequest request) {
        return bundleService.create(request, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Ubah paket bundling (isi, harga, detail)")
    public ProductResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody BundleRequest request) {
        return bundleService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Hapus paket bundling")
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        bundleService.delete(id, principal);
    }
}
