package com.cassierq.api.access;

import com.cassierq.api.access.dto.MenuAccessResponse;
import com.cassierq.api.access.dto.MyMenuAccessResponse;
import com.cassierq.api.access.dto.UpdateMenuAccessRequest;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/menu-access")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Menu Access", description = "Hak tampilan menu per role (diatur Kepala Toko)")
public class MenuAccessController {

    private final MenuAccessService menuAccessService;

    @GetMapping("/me")
    @Operation(summary = "Menu yang boleh tampil untuk user yang sedang login")
    public MyMenuAccessResponse mine(@AuthenticationPrincipal AppUserPrincipal principal) {
        return menuAccessService.mine(principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Matriks role → menu untuk toko ini")
    public MenuAccessResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return menuAccessService.get(principal);
    }

    @PutMapping("/{roleCode}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Atur menu yang boleh tampil untuk satu role staf (PRODUCT, GUDANG, KASIR)")
    public MenuAccessResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateMenuAccessRequest request) {
        return menuAccessService.update(principal, roleCode, request.menus());
    }
}
