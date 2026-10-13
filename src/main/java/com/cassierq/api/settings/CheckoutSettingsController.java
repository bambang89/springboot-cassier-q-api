package com.cassierq.api.settings;

import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.settings.dto.CheckoutSettingsResponse;
import com.cassierq.api.settings.dto.UpdateCheckoutSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/checkout-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Checkout Settings", description = "Metode pembayaran & PPN per toko")
public class CheckoutSettingsController {

    private final CheckoutSettingsService checkoutSettingsService;

    @GetMapping
    @Operation(summary = "Metode pembayaran yang diterima toko, metode default, dan status PPN")
    public CheckoutSettingsResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return checkoutSettingsService.get(principal);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Atur metode pembayaran & PPN (pemilik toko)")
    public CheckoutSettingsResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UpdateCheckoutSettingsRequest request) {
        return checkoutSettingsService.update(principal, request);
    }
}
