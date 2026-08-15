package com.cassierq.api.sales;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderResponse;
import com.cassierq.api.sales.dto.VoidOrderRequest;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Transaksi penjualan (sales_transactions) — butuh sesi kasir yang sedang terbuka")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Catat transaksi penjualan baru (mendekremen stok dalam transaksi yang sama)")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, principal));
    }

    @GetMapping
    @Operation(summary = "Riwayat transaksi (paginated), terbaru dulu")
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal AppUserPrincipal principal, Pageable pageable) {
        return orderService.list(principal, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail satu transaksi")
    public OrderResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return orderService.get(id, principal);
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "Batalkan transaksi PAID (restock item, status jadi VOID)")
    public OrderResponse voidOrder(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VoidOrderRequest request) {
        return orderService.voidOrder(id, principal, request.reason());
    }
}
