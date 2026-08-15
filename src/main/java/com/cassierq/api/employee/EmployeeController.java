package com.cassierq.api.employee;

import com.cassierq.api.employee.dto.CreateEmployeeRequest;
import com.cassierq.api.employee.dto.EmployeeResponse;
import com.cassierq.api.employee.dto.UpdateEmployeeRequest;
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
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
@Tag(name = "Employees", description = "Kelola staf toko sendiri (beda dari /auth/register yang selalu bikin toko baru)")
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @Operation(summary = "Tambah karyawan baru ke toko Anda (KASIR/PRODUCT/GUDANG/KEPALA_TOKO)")
    public ResponseEntity<EmployeeResponse> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(principal, request));
    }

    @GetMapping
    @Operation(summary = "Daftar karyawan di toko Anda")
    public List<EmployeeResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return employeeService.list(principal);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail satu karyawan")
    public EmployeeResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return employeeService.get(principal, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit data karyawan (nama/kontak/role) — username & password tidak diubah di sini")
    public EmployeeResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(principal, id, request);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Nonaktifkan karyawan (akun tidak bisa login lagi, sesi aktif langsung dicabut)")
    public EmployeeResponse deactivate(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return employeeService.setActive(principal, id, false);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Aktifkan kembali karyawan")
    public EmployeeResponse reactivate(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable UUID id) {
        return employeeService.setActive(principal, id, true);
    }
}
