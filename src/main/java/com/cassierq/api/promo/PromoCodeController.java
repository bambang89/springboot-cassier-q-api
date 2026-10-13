package com.cassierq.api.promo;

import com.cassierq.api.promo.dto.PromoCodeRequest;
import com.cassierq.api.promo.dto.PromoCodeResponse;
import com.cassierq.api.promo.dto.PromoQuoteResponse;
import com.cassierq.api.promo.dto.ValidatePromoRequest;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/promo-codes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Promo Codes", description = "Kode promo toko")
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Daftar kode promo toko")
    public List<PromoCodeResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return promoCodeService.list(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Buat kode promo")
    public PromoCodeResponse create(
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody PromoCodeRequest request) {
        return promoCodeService.create(principal, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Ubah kode promo")
    public PromoCodeResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody PromoCodeRequest request) {
        return promoCodeService.update(principal, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Hapus kode promo (yang sudah pernah dipakai hanya dinonaktifkan)")
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        promoCodeService.delete(principal, id);
    }

    @PostMapping("/validate")
    @PreAuthorize("@actionAccess.allowed(principal, 'pos.promo')")
    @Operation(summary = "Cek kode promo untuk subtotal keranjang (dipakai kasir)")
    public PromoQuoteResponse validate(
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody ValidatePromoRequest request) {
        return promoCodeService.quote(principal, request.code(), request.subtotal());
    }
}
