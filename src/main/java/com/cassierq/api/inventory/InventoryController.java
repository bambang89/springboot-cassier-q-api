package com.cassierq.api.inventory;

import com.cassierq.api.inventory.dto.RestockRequest;
import com.cassierq.api.inventory.dto.StockResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{id}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory", description = "Stok — hanya penambahan stok manual (belum ada alur Purchase Order)")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/restock")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'GUDANG')")
    @Operation(summary = "Tambah stok produk secara manual di toko user yang sedang login")
    public StockResponse restock(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RestockRequest request) {
        return inventoryService.restock(id, request, principal);
    }
}
