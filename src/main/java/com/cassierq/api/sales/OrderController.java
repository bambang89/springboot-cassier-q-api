package com.cassierq.api.sales;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderResponse;
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
@Tag(name = "Orders", description = "Transaksi kasir: buat order, riwayat, void")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Buat transaksi baru dari keranjang kasir (mengurangi stok produk)")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(principal.getStoreId(), principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Riwayat transaksi (paginated, terbaru dulu)")
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return orderService.list(principal.getStoreId(), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail satu transaksi")
    public OrderResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return orderService.get(principal.getStoreId(), id);
    }

    @PostMapping("/{id}/void")
    @Operation(summary = "Batalkan transaksi PAID dan kembalikan stok produk")
    public OrderResponse voidOrder(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return orderService.voidOrder(principal.getStoreId(), id);
    }
}
