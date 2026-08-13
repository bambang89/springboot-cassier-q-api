package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.ProductRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Products", description = "Katalog produk, termasuk pencarian berdasarkan barcode untuk kasir")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Daftar produk (paginated, opsional pencarian berdasarkan nama)")
    public PageResponse<ProductResponse> list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return productService.list(principal.getStoreId(), search, pageable);
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Cari produk berdasarkan barcode (dipakai oleh scanner di aplikasi kasir)")
    public ProductResponse getByBarcode(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String barcode) {
        return productService.getByBarcode(principal.getStoreId(), barcode);
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Tambah produk baru (khusus owner)")
    public ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(principal.getStoreId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Ubah produk (khusus owner)")
    public ProductResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return productService.update(principal.getStoreId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Hapus produk (khusus owner)")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id) {
        productService.delete(principal.getStoreId(), id);
        return ResponseEntity.noContent().build();
    }
}
