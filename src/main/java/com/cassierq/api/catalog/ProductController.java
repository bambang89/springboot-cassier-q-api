package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.ProductImageResponse;
import com.cassierq.api.catalog.dto.ProductRequest;
import com.cassierq.api.catalog.dto.ProductResponse;
import com.cassierq.api.catalog.dto.ProductUnitResponse;
import com.cassierq.api.catalog.dto.RegisterProductUnitRequest;
import com.cassierq.api.catalog.dto.UnitConversionResponse;
import com.cassierq.api.common.PageResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{id}/convert")
    @Operation(summary = "Konversi jumlah dari satuan lain ke satuan dasar produk (mis. 2 DUS = berapa PCS)")
    public UnitConversionResponse convert(
            @PathVariable UUID id,
            @RequestParam UUID unitId,
            @RequestParam BigDecimal quantity) {
        return productService.convert(id, unitId, quantity);
    }

    @GetMapping("/{id}/units")
    @Operation(summary = "Daftar satuan yang berlaku untuk produk ini (satuan dasar + alternatif)")
    public List<ProductUnitResponse> listUnits(@PathVariable UUID id) {
        return productService.listUnits(id);
    }

    @PostMapping("/{id}/units")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'PRODUCT')")
    @Operation(summary = "Daftarkan satuan alternatif + rasio konversinya untuk produk ini (pasangan tulis dari /convert)")
    public ResponseEntity<ProductUnitResponse> registerUnit(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterProductUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.registerUnit(id, request));
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

    @PostMapping(value = "/{id}/photo", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Upload/ganti foto produk (JPG/PNG/WEBP, maks 5MB)")
    public ProductResponse uploadPhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return productService.uploadPhoto(id, file, principal.getPrimaryStoreId());
    }

    @GetMapping("/{id}/photos")
    @Operation(summary = "Daftar foto galeri produk (di luar foto utama)")
    public List<ProductImageResponse> listPhotos(@PathVariable UUID id) {
        return productService.listPhotos(id);
    }

    @PostMapping(value = "/{id}/photos", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Tambah foto ke galeri produk (JPG/PNG/WEBP, maks 5MB)")
    public ResponseEntity<ProductImageResponse> addPhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addPhoto(id, file, principal));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Hapus salah satu foto galeri produk")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        productService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'PRODUCT')")
    @Operation(summary = "Nonaktifkan produk (soft delete)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
