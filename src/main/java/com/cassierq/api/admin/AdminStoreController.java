package com.cassierq.api.admin;

import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.repository.StoreRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Khusus SUPERADMIN (pemilik aplikasi): daftar semua toko. Toko yang dipilih
 * dikirim balik lewat header {@code X-Store-Id}, lalu semua endpoint per-toko
 * bekerja untuk toko itu (lihat JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin", description = "Akses pemilik aplikasi (SUPERADMIN) ke seluruh toko")
public class AdminStoreController {

    private final StoreRepository storeRepository;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Daftar semua toko untuk dipilih SUPERADMIN")
    public List<StoreSummary> list() {
        return storeRepository.findAll().stream()
                .sorted(Comparator.comparing(Store::getStoreName, String.CASE_INSENSITIVE_ORDER))
                .map(StoreSummary::from)
                .toList();
    }

    public record StoreSummary(
            UUID id,
            String storeCode,
            String storeName,
            String address,
            String city,
            String phone,
            String status,
            UUID parentStoreId) {

        static StoreSummary from(Store store) {
            return new StoreSummary(store.getId(), store.getStoreCode(), store.getStoreName(), store.getAddress(),
                    store.getCity(), store.getPhone(), store.getStatus(), store.getParentStoreId());
        }
    }
}
