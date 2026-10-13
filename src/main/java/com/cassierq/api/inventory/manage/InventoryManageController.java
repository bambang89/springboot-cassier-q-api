package com.cassierq.api.inventory.manage;

import com.cassierq.api.common.PageResponse;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockItem;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockReport;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockRequest;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.BadStockSettings;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.MovementItem;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameRequest;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.OpnameResult;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.ResolveRequest;
import com.cassierq.api.inventory.manage.dto.InventoryDtos.StockSummary;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Inventori menu. Viewing is open to the store's staff; changes follow Hak Akses Menu → Inventori. */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory", description = "Daftar stok, riwayat mutasi, stok opname, bad stock")
public class InventoryManageController {

    private final InventoryManageService service;

    @GetMapping("/stock")
    @Operation(summary = "Daftar stok semua produk + ringkasan (habis, menipis, nilai stok)")
    public StockSummary stock(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.stock(principal);
    }

    @GetMapping("/movements")
    @Operation(summary = "Riwayat mutasi stok (filter produk, tipe, periode)")
    public PageResponse<MovementItem> movements(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(service.movements(principal, productId, type, from, to,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)))));
    }

    @PostMapping("/opname")
    @PreAuthorize("@actionAccess.allowed(principal, 'inventory.opname')")
    @Operation(summary = "Stok opname: set stok ke jumlah fisik, selisih tercatat di mutasi")
    public OpnameResult opname(
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody OpnameRequest request) {
        return service.opname(principal, request);
    }

    @GetMapping("/bad-stock")
    @Operation(summary = "Daftar laporan bad stock (opsional per status)")
    public List<BadStockItem> badStock(
            @AuthenticationPrincipal AppUserPrincipal principal, @RequestParam(required = false) String status) {
        return service.badStock(principal, status);
    }

    @PostMapping("/bad-stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@actionAccess.allowed(principal, 'inventory.bad_stock')")
    @Operation(summary = "Catat bad stock (rusak/kedaluwarsa/hilang/cacat/retur pembeli)")
    public BadStockItem report(
            @AuthenticationPrincipal AppUserPrincipal principal, @Valid @RequestBody BadStockRequest request) {
        return service.reportBadStock(principal, request);
    }

    @PostMapping(value = "/bad-stock/{id}/photo", consumes = "multipart/form-data")
    @PreAuthorize("@actionAccess.allowed(principal, 'inventory.bad_stock')")
    @Operation(summary = "Upload/ganti foto bukti bad stock")
    public BadStockItem photo(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return service.uploadBadStockPhoto(principal, id, file);
    }

    @PostMapping("/bad-stock/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Setujui laporan bad stock (stok dikurangi, barang dipisahkan)")
    public BadStockItem approve(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return service.approveBadStock(principal, id);
    }

    @PostMapping("/bad-stock/{id}/return")
    @PreAuthorize("@actionAccess.allowed(principal, 'inventory.bad_stock_resolve')")
    @Operation(summary = "Tandai bad stock sudah diretur ke supplier")
    public BadStockItem returnToSupplier(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request) {
        return service.resolveBadStock(principal, id, "RETURNED", request.supplierId(), request.note());
    }

    @PostMapping("/bad-stock/{id}/dispose")
    @PreAuthorize("@actionAccess.allowed(principal, 'inventory.bad_stock_resolve')")
    @Operation(summary = "Tandai bad stock sudah dimusnahkan")
    public BadStockItem dispose(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request) {
        return service.resolveBadStock(principal, id, "DISPOSED", null, request.note());
    }

    @PostMapping("/bad-stock/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Batalkan/tolak laporan bad stock (stok dikembalikan bila sudah dikurangi)")
    public BadStockItem cancel(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ResolveRequest request) {
        return service.cancelBadStock(principal, id, request.note());
    }

    @GetMapping("/bad-stock/report")
    @Operation(summary = "Laporan kerugian bad stock dalam periode")
    public BadStockReport report(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return service.report(principal, from, to);
    }

    @GetMapping("/bad-stock/settings")
    @Operation(summary = "Apakah laporan bad stock perlu persetujuan Kepala Toko")
    public BadStockSettings settings(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new BadStockSettings(service.requireApproval(principal));
    }

    @PutMapping("/bad-stock/settings")
    @PreAuthorize("hasRole('KEPALA_TOKO')")
    @Operation(summary = "Atur persetujuan bad stock (khusus Kepala Toko)")
    public BadStockSettings updateSettings(
            @AuthenticationPrincipal AppUserPrincipal principal, @RequestBody BadStockSettings request) {
        return new BadStockSettings(service.setRequireApproval(principal, request.requireApproval()));
    }
}
