package com.cassierq.api.employee;

import com.cassierq.api.domain.repository.RoleRepository;
import com.cassierq.api.employee.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles", description = "Katalog role yang bisa diberikan ke karyawan (tidak termasuk SUPERADMIN)")
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Daftar role yang bisa dipilih saat menambah karyawan")
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream()
                .filter(role -> !"SUPERADMIN".equals(role.getRoleCode()))
                .map(RoleResponse::from)
                .toList();
    }
}
