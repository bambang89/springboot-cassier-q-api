package com.cassierq.api.catalog;

import com.cassierq.api.catalog.dto.CategoryRequest;
import com.cassierq.api.catalog.dto.CategoryResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Categories", description = "Kategori produk milik toko")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Daftar kategori pada toko user yang login")
    public List<CategoryResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return categoryService.list(principal.getStoreId());
    }

    @PostMapping
    @Operation(summary = "Buat kategori baru")
    public ResponseEntity<CategoryResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(principal.getStoreId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hapus kategori")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id) {
        categoryService.delete(principal.getStoreId(), id);
        return ResponseEntity.noContent().build();
    }
}
