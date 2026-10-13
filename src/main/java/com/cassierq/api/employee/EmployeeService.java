package com.cassierq.api.employee;

import com.cassierq.api.auth.AuthService;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.domain.entity.Employee;
import com.cassierq.api.domain.entity.Role;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import com.cassierq.api.domain.repository.EmployeeRepository;
import com.cassierq.api.domain.repository.RoleRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.domain.repository.UserRoleRepository;
import com.cassierq.api.employee.dto.CreateEmployeeRequest;
import com.cassierq.api.employee.dto.EmployeeResponse;
import com.cassierq.api.employee.dto.UpdateEmployeeRequest;
import com.cassierq.api.security.AppUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cassierq.api.security.RolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final com.cassierq.api.common.storage.FileStorageService fileStorageService;

    @Transactional
    public EmployeeResponse create(AppUserPrincipal caller, CreateEmployeeRequest request) {
        UUID storeId = requireStore(caller);

        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }

        Store store = storeRepository.getReferenceById(storeId);
        Role role = assignableRole(caller, request.roleCode());
        String nationalId = blankToNull(request.nationalId());
        String employeeNumber = blankToNull(request.employeeNumber());
        checkIdentityUnique(storeId, nationalId, employeeNumber, null);

        Employee employee = employeeRepository.save(Employee.builder()
                .employeeCode(request.username())
                .store(store)
                .fullName(request.name())
                .phone(request.phone())
                .email(request.email())
                .nationalId(nationalId)
                .employeeNumber(employeeNumber)
                .active(true)
                .build());

        User user = userRepository.save(User.builder()
                .employee(employee)
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                // Owner picked this password, not the employee themselves —
                // make them set their own on first login.
                .mustChangePassword(true)
                .build());

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .role(role)
                .store(store)
                .createdAt(Instant.now())
                .build());

        return EmployeeResponse.from(employee, user, List.of(role.getRoleCode()));
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list(AppUserPrincipal caller) {
        UUID storeId = requireStore(caller);
        return employeeRepository.findByStoreId(storeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(AppUserPrincipal caller, UUID employeeId) {
        UUID storeId = requireStore(caller);
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getStore() != null && storeId.equals(e.getStore().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Karyawan tidak ditemukan"));
        return toResponse(employee);
    }

    @Transactional
    public EmployeeResponse update(AppUserPrincipal caller, UUID employeeId, UpdateEmployeeRequest request) {
        UUID storeId = requireStore(caller);
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getStore() != null && storeId.equals(e.getStore().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Karyawan tidak ditemukan"));
        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Akun user untuk karyawan ini tidak ditemukan"));
        requireCanManage(caller, user);

        String nationalId = blankToNull(request.nationalId());
        String employeeNumber = blankToNull(request.employeeNumber());
        checkIdentityUnique(storeId, nationalId, employeeNumber, employeeId);

        employee.setFullName(request.name());
        employee.setPhone(request.phone());
        employee.setEmail(request.email());
        employee.setNationalId(nationalId);
        employee.setEmployeeNumber(employeeNumber);
        employeeRepository.save(employee);

        user.setEmail(request.email());
        userRepository.save(user);

        List<String> currentRoles = roleCodesOf(user.getId());
        if (currentRoles.size() != 1 || !currentRoles.get(0).equalsIgnoreCase(request.roleCode())) {
            if (user.getId().equals(caller.getUserId())) {
                throw new BadRequestException("Anda tidak bisa mengubah role akun Anda sendiri");
            }
            Role newRole = assignableRole(caller, request.roleCode());
            userRoleRepository.deleteByUserIdAndStoreId(user.getId(), storeId);
            userRoleRepository.save(UserRole.builder()
                    .user(user)
                    .role(newRole)
                    .store(storeRepository.getReferenceById(storeId))
                    .createdAt(Instant.now())
                    .build());
        }

        return EmployeeResponse.from(employee, user, roleCodesOf(user.getId()));
    }

    @Transactional
    public EmployeeResponse setActive(AppUserPrincipal caller, UUID employeeId, boolean active) {
        UUID storeId = requireStore(caller);
        Employee employee = employeeRepository.findById(employeeId)
                .filter(e -> e.getStore() != null && storeId.equals(e.getStore().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Karyawan tidak ditemukan"));

        User user = userRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Akun user untuk karyawan ini tidak ditemukan"));

        if (user.getId().equals(caller.getUserId()) && !active) {
            throw new BadRequestException("Anda tidak bisa menonaktifkan akun Anda sendiri");
        }
        requireCanManage(caller, user);

        employee.setActive(active);
        employeeRepository.save(employee);
        user.setActive(active);
        userRepository.save(user);

        if (!active) {
            // Deactivating someone should end their sessions right away, not
            // whenever their (now up to 1-day-old) access token happens to expire.
            authService.revokeAllSessions(user.getId());
        }

        return EmployeeResponse.from(employee, user, roleCodesOf(user.getId()));
    }

    /** Role hanya boleh dari daftar yang diizinkan untuk caller — SUPERADMIN tidak pernah bisa diberikan lewat API. */
    private Role assignableRole(AppUserPrincipal caller, String roleCode) {
        String code = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (!RolePolicy.assignableBy(caller).contains(code)) {
            throw new AccessDeniedException("Anda tidak bisa memberikan role " + code);
        }
        return roleRepository.findByRoleCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Role tidak ditemukan"));
    }

    /**
     * KEPALA_TOKO mengelola staf (PRODUCT/GUDANG/KASIR), bukan sesama
     * KEPALA_TOKO atau SUPERADMIN. Mengubah data diri sendiri tetap boleh.
     */
    private void requireCanManage(AppUserPrincipal caller, User target) {
        if (caller.isSuperadminUser() || target.getId().equals(caller.getUserId())) {
            return;
        }
        boolean targetIsManager = target.isSuperadmin() || roleCodesOf(target.getId()).stream()
                .anyMatch(code -> RolePolicy.SUPERADMIN.equals(code) || RolePolicy.KEPALA_TOKO.equals(code));
        if (targetIsManager) {
            throw new AccessDeniedException("Hanya pemilik aplikasi yang bisa mengubah akun Kepala Toko");
        }
    }

    /** Uploads or replaces an employee's photo (store head); the old file is removed after commit. */
    @Transactional
    public EmployeeResponse updatePhoto(AppUserPrincipal caller, UUID employeeId, org.springframework.web.multipart.MultipartFile file) {
        Employee employee = requireInStore(caller, employeeId);
        String previous = employee.getPhotoUrl();
        employee.setPhotoUrl(fileStorageService.storeImage(
                "stores/" + com.cassierq.api.common.storage.FileStorageService.slug(employee.getStore().getStoreCode())
                        + "/employees/" + employeeId,
                employee.getFullName(), file));
        employeeRepository.save(employee);
        fileStorageService.deleteAfterCommit(previous);
        return toResponse(employee);
    }

    /** Removes an employee's photo. */
    @Transactional
    public EmployeeResponse removePhoto(AppUserPrincipal caller, UUID employeeId) {
        Employee employee = requireInStore(caller, employeeId);
        fileStorageService.deleteAfterCommit(employee.getPhotoUrl());
        employee.setPhotoUrl(null);
        employeeRepository.save(employee);
        return toResponse(employee);
    }

    private Employee requireInStore(AppUserPrincipal caller, UUID employeeId) {
        UUID storeId = requireStore(caller);
        return employeeRepository.findById(employeeId)
                .filter(e -> e.getStore() != null && storeId.equals(e.getStore().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Karyawan tidak ditemukan"));
    }

    /** NIK KTP and NIK Karyawan must be unique among the store's employees (when filled in). */
    private void checkIdentityUnique(UUID storeId, String nationalId, String employeeNumber, UUID currentId) {
        for (Employee other : employeeRepository.findByStoreId(storeId)) {
            if (other.getId().equals(currentId)) continue;
            if (nationalId != null && nationalId.equals(other.getNationalId())) {
                throw new ConflictException("NIK KTP sudah dipakai karyawan " + other.getFullName());
            }
            if (employeeNumber != null && employeeNumber.equalsIgnoreCase(other.getEmployeeNumber())) {
                throw new ConflictException("NIK Karyawan sudah dipakai " + other.getFullName());
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private EmployeeResponse toResponse(Employee employee) {
        User user = userRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Akun user untuk karyawan ini tidak ditemukan"));
        return EmployeeResponse.from(employee, user, roleCodesOf(user.getId()));
    }

    private List<String> roleCodesOf(UUID userId) {
        return userRoleRepository.findAllByUserIdFetchingRole(userId).stream()
                .map(ur -> ur.getRole().getRoleCode())
                .toList();
    }

    private UUID requireStore(AppUserPrincipal caller) {
        UUID storeId = caller.getPrimaryStoreId();
        if (storeId == null) {
            throw new BadRequestException("Akun ini tidak terikat ke toko manapun");
        }
        return storeId;
    }
}
