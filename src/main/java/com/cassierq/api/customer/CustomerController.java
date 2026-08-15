package com.cassierq.api.customer;

import com.cassierq.api.customer.dto.CustomerRequest;
import com.cassierq.api.customer.dto.CustomerResponse;
import com.cassierq.api.customer.dto.LedgerEntryResponse;
import com.cassierq.api.customer.dto.RecordPaymentRequest;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers", description = "Pelanggan toko + hutang/piutang (bon) — konsep baru, tidak ada di skema asli")
public class CustomerController {

    private final CustomerService customerService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Daftarkan pelanggan baru")
    public ResponseEntity<CustomerResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(principal, request));
    }

    @GetMapping
    @Operation(summary = "Daftar pelanggan di toko Anda, dengan saldo hutang saat ini")
    public List<CustomerResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return customerService.list(principal);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail satu pelanggan")
    public CustomerResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return customerService.get(principal, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Perbarui data pelanggan (termasuk limit kredit)")
    public CustomerResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request) {
        return customerService.update(principal, id, request);
    }

    @GetMapping("/{id}/ledger")
    @Operation(summary = "Riwayat hutang & pembayaran pelanggan, terbaru dulu")
    public List<LedgerEntryResponse> ledger(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return customerService.ledger(principal, id);
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Catat pelanggan membayar sebagian/seluruh hutangnya")
    public CustomerResponse recordPayment(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest request) {
        var actor = userRepository.getReferenceById(principal.getUserId());
        return customerService.recordPayment(principal, id, request, actor);
    }
}
