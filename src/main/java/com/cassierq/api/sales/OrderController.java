package com.cassierq.api.sales;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.sales.dto.CreateOrderRequest;
import com.cassierq.api.sales.dto.OrderResponse;
import com.cassierq.api.sales.dto.ReceiptResponse;
import com.cassierq.api.sales.dto.VoidOrderRequest;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Transaksi penjualan (sales_transactions) — butuh sesi kasir yang sedang terbuka")
public class OrderController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Catat transaksi penjualan baru (mendekremen stok dalam transaksi yang sama). "
            + "Kirim header Idempotency-Key yang sama saat retry supaya tidak tercatat dua kali.")
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key maksimal " + MAX_IDEMPOTENCY_KEY_LENGTH + " karakter");
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, principal, key));
        } catch (DataIntegrityViolationException ex) {
            // Dua retry yang sama datang bersamaan: yang kalah kena unique index
            // idempotency — kembalikan transaksi milik pemenangnya.
            if (key == null) {
                throw ex;
            }
            return orderService.findByIdempotencyKey(principal.getPrimaryStoreId(), key)
                    .map(order -> ResponseEntity.status(HttpStatus.CREATED).body(order))
                    .orElseThrow(() -> ex);
        }
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

    @GetMapping("/{id}/receipt")
    @Operation(summary = "Data struk/nota untuk dicetak — nama toko, item, total, metode bayar, sisa utang jika transaksi kredit")
    public ReceiptResponse receipt(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return orderService.receipt(id, principal);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Batalkan transaksi PAID (restock item, refund payment, batalkan utang pelanggan, status jadi VOID)")
    public OrderResponse voidOrder(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VoidOrderRequest request) {
        return orderService.voidOrder(id, principal, request.reason());
    }
}
