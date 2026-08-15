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
@Tag(name = "Products", description = "Produk (katalog global) + harga & stok pada toko user yang sedang login")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Daftar produk (paginated), harga & stok mengikuti toko user")
    public PageResponse<ProductResponse> list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return productService.search(search, principal.getPrimaryStoreId(), pageable);
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Cari produk berdasarkan barcode (dipakai scanner)")
    public ProductResponse getByBarcode(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable String barcode) {
        return productService.getByBarcode(barcode, principal.getPrimaryStoreId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Buat produk baru (sekaligus harga & stok awal di toko user)")
    public ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request, principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Perbarui produk (harga baru otomatis menutup harga lama)")
    public ProductResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Nonaktifkan produk (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
