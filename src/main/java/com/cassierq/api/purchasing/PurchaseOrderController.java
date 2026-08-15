package com.cassierq.api.purchasing;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.purchasing.dto.CreatePurchaseOrderRequest;
import com.cassierq.api.purchasing.dto.PurchaseOrderResponse;
import com.cassierq.api.purchasing.dto.ReceivePurchaseOrderRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO', 'GUDANG')")
@Tag(name = "Purchase Orders", description = "Pemesanan barang ke supplier + penerimaan barang (stok masuk resmi)")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    @Operation(summary = "Buat purchase order baru (status langsung ORDERED)")
    public ResponseEntity<PurchaseOrderResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.create(request, principal));
    }

    @GetMapping
    @Operation(summary = "Riwayat purchase order (paginated), terbaru dulu")
    public PageResponse<PurchaseOrderResponse> list(@AuthenticationPrincipal AppUserPrincipal principal, Pageable pageable) {
        return purchaseOrderService.list(principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail satu purchase order")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return purchaseOrderService.get(id, principal);
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Terima barang (boleh sebagian) — menambah stok & mencatat stock_movements PURCHASE")
    public PurchaseOrderResponse receive(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return purchaseOrderService.receive(id, request, principal);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Batalkan PO (hanya jika belum ada barang diterima)")
    public PurchaseOrderResponse cancel(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return purchaseOrderService.cancel(id, principal);
    }
}
