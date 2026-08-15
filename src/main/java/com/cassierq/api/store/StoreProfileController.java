package com.cassierq.api.store;

import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.store.dto.StoreProfileResponse;
import com.cassierq.api.store.dto.UpdateStoreProfileRequest;
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
@RequestMapping("/api/v1/store")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Store Profile", description = "Profil toko user yang sedang login — data dasar (stores) + pengaturan bebas (store_settings, mis. logo/deskripsi/footer struk)")
public class StoreProfileController {

    private final StoreProfileService storeProfileService;

    @GetMapping
    @Operation(summary = "Profil toko saya")
    public StoreProfileResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return storeProfileService.get(principal);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Perbarui profil toko (partial — hanya field yang dikirim yang berubah)")
    public StoreProfileResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UpdateStoreProfileRequest request) {
        return storeProfileService.update(principal, request);
    }
}
