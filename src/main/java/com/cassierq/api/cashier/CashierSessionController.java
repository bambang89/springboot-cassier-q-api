package com.cassierq.api.cashier;

import com.cassierq.api.cashier.dto.CashierSessionResponse;
import com.cassierq.api.cashier.dto.CloseCashierSessionRequest;
import com.cassierq.api.cashier.dto.OpenCashierSessionRequest;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/cashier-sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cashier Sessions", description = "Sesi buka/tutup kasir — wajib dibuka sebelum mencatat penjualan")
public class CashierSessionController {

    private final CashierSessionService cashierSessionService;

    @PostMapping("/open")
    @Operation(summary = "Buka sesi kasir dengan modal awal")
    public ResponseEntity<CashierSessionResponse> open(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody OpenCashierSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashierSessionService.open(principal, request));
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Tutup sesi kasir, hitung selisih kas")
    public CashierSessionResponse close(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CloseCashierSessionRequest request) {
        return cashierSessionService.close(principal, id, request);
    }

    @GetMapping("/current")
    @Operation(summary = "Sesi kasir yang sedang terbuka milik user ini")
    public CashierSessionResponse current(@AuthenticationPrincipal AppUserPrincipal principal) {
        return cashierSessionService.current(principal);
    }
}
