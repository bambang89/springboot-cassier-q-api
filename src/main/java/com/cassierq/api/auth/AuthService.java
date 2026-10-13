package com.cassierq.api.auth;

import com.cassierq.api.auth.dto.AuthResponse;
import com.cassierq.api.auth.dto.ChangePasswordRequest;
import com.cassierq.api.auth.dto.ForgotPasswordRequest;
import com.cassierq.api.auth.dto.LoginRequest;
import com.cassierq.api.auth.dto.RefreshRequest;
import com.cassierq.api.auth.dto.RegisterRequest;
import com.cassierq.api.auth.dto.ResetPasswordRequest;
import com.cassierq.api.auth.dto.UserResponse;
import com.cassierq.api.common.exception.BadRequestException;
import com.cassierq.api.common.exception.ConflictException;
import com.cassierq.api.common.exception.ResourceNotFoundException;
import com.cassierq.api.config.JwtProperties;
import com.cassierq.api.config.PasswordResetProperties;
import com.cassierq.api.domain.entity.Device;
import com.cassierq.api.domain.entity.Employee;
import com.cassierq.api.domain.entity.NumberSequence;
import com.cassierq.api.domain.entity.PasswordResetToken;
import com.cassierq.api.domain.entity.ProductCategory;
import com.cassierq.api.domain.entity.RefreshToken;
import com.cassierq.api.domain.entity.Role;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.Unit;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import com.cassierq.api.domain.entity.UserSession;
import com.cassierq.api.domain.repository.DeviceRepository;
import com.cassierq.api.domain.repository.EmployeeRepository;
import com.cassierq.api.domain.repository.NumberSequenceRepository;
import com.cassierq.api.domain.repository.PasswordResetTokenRepository;
import com.cassierq.api.domain.repository.ProductCategoryRepository;
import com.cassierq.api.domain.repository.RefreshTokenRepository;
import com.cassierq.api.domain.repository.RoleRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UnitRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.domain.repository.UserRoleRepository;
import com.cassierq.api.domain.repository.UserSessionRepository;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_REGISTER_ROLE_CODE = "KEPALA_TOKO";

    private static final java.util.Map<String, String> DEFAULT_SEQUENCES = java.util.Map.of(
            "SALES_TRANSACTION", "TRX",
            "STOCK_OPNAME", "SO",
            "PURCHASE_ORDER", "PO",
            "STOCK_TRANSFER", "ST");

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final NumberSequenceRepository numberSequenceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UnitRepository unitRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordResetProperties passwordResetProperties;
    private final com.cassierq.api.common.storage.FileStorageService fileStorageService;

    @Transactional
    public AuthResponse register(RegisterRequest request, DeviceContext device) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }
        if (storeRepository.existsByStoreCodeIgnoreCase(request.storeCode())) {
            throw new ConflictException("Kode toko sudah dipakai");
        }

        Store store = storeRepository.save(Store.builder()
                .storeCode(request.storeCode())
                .storeName(request.storeName())
                .address(blankToNull(request.address()))
                .city(blankToNull(request.city()))
                .phone(blankToNull(request.phone()))
                .status("ACTIVE")
                .build());
        provisionNumberSequences(store);
        provisionStarterCatalog(store);

        Employee employee = employeeRepository.save(Employee.builder()
                .employeeCode(request.username())
                .store(store)
                .fullName(request.name())
                .email(request.email())
                .active(true)
                .build());

        User user = userRepository.save(User.builder()
                .employee(employee)
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .build());

        Role role = roleRepository.findByRoleCodeIgnoreCase(DEFAULT_REGISTER_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Role default '" + DEFAULT_REGISTER_ROLE_CODE + "' tidak ditemukan di tabel roles"));

        userRoleRepository.save(UserRole.builder()
                .user(user)
                .role(role)
                .store(store)
                .createdAt(Instant.now())
                .build());

        return issueTokens(user, device);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, DeviceContext device) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(request.username(), request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user, device);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, DeviceContext device) {
        String hash = jwtService.hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Refresh token tidak valid atau sudah kedaluwarsa"));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser(), device);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void logoutAllDevices(UUID userId) {
        revokeAllSessions(userId);
    }

    @Transactional
    public void revokeUserSessions(AppUserPrincipal caller, UUID targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        if (!caller.hasRole("SUPERADMIN")) {
            UUID targetStoreId = target.getEmployee().getStore() != null ? target.getEmployee().getStore().getId() : null;
            if (targetStoreId == null || !targetStoreId.equals(caller.getPrimaryStoreId())) {
                throw new BadRequestException("Anda hanya bisa me-revoke user di toko Anda sendiri");
            }
        }

        revokeAllSessions(targetUserId);
    }

    /**
     * Unconditionally kills every live session/refresh token/device record
     * for a user — no authorization check, callers must have already done
     * their own (see {@link #revokeUserSessions}, and employee deactivation
     * in {@code EmployeeService}).
     */
    @Transactional
    public void revokeAllSessions(UUID userId) {
        userSessionRepository.revokeAllByUserId(userId);
        refreshTokenRepository.revokeAllByUserId(userId);
        deviceRepository.deleteAllByUserId(userId);
    }

    /** Uploads or replaces the caller's profile photo; the old file is removed after commit. */
    @Transactional
    public UserResponse updateAvatar(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        String previous = user.getAvatarUrl();
        user.setAvatarUrl(fileStorageService.storeImage("users/" + userId + "/avatar", user.getUsername(), file));
        userRepository.save(user);
        fileStorageService.deleteAfterCommit(previous);
        return me(userId);
    }

    /** Removes the caller's profile photo. */
    @Transactional
    public UserResponse removeAvatar(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        fileStorageService.deleteAfterCommit(user.getAvatarUrl());
        user.setAvatarUrl(null);
        userRepository.save(user);
        return me(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        List<UserRole> roles = userRoleRepository.findAllByUserIdFetchingRole(userId);
        return UserResponse.from(user, roles);
    }

    /**
     * Authenticated user changes their own password, knowing the current one.
     * Every other session is logged out immediately (not just their refresh
     * tokens) — only the session making this request stays alive.
     */
    @Transactional
    public void changePassword(UUID userId, String currentJti, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Kata sandi saat ini salah");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId());
        userSessionRepository.revokeAllByUserIdExcept(user.getId(), currentJti);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByUsernameIgnoreCase(request.username()).ifPresent(user -> {
            String rawToken = jwtService.generateOpaqueToken();
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(jwtService.hashToken(rawToken))
                    .expiresAt(Instant.now().plus(passwordResetProperties.ttlMinutes(), ChronoUnit.MINUTES))
                    .build());
            passwordResetMailSender.sendResetToken(user.getEmail(), rawToken);
        });
    }

    /** Completes the "forgot password" flow: redeems a reset token for a new password. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = jwtService.hashToken(request.token());
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Token reset password tidak valid atau sudah kedaluwarsa"));

        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        stored.setUsed(true);
        passwordResetTokenRepository.save(stored);

        // Password direset karena mungkin bocor: semua sesi (access token
        // 24 jam juga, bukan cuma refresh token) langsung mati.
        revokeAllSessions(user.getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Toko hasil register adalah toko induk dengan katalog sendiri yang masih
     * kosong — tanpa satuan & kategori, produk pertama tidak bisa dibuat.
     */
    private void provisionStarterCatalog(Store store) {
        unitRepository.save(Unit.builder()
                .unitCode("PCS")
                .unitName("Pcs")
                .ownerStoreId(store.getId())
                .createdAt(Instant.now())
                .build());
        categoryRepository.save(ProductCategory.builder()
                .categoryCode("UMUM")
                .categoryName("Umum")
                .ownerStoreId(store.getId())
                .active(true)
                .build());
    }

    private void provisionNumberSequences(Store store) {
        Instant now = Instant.now();
        DEFAULT_SEQUENCES.forEach((type, prefix) -> numberSequenceRepository.save(NumberSequence.builder()
                .store(store)
                .sequenceType(type)
                .prefix(prefix)
                .currentValue(0)
                .updatedAt(now)
                .build()));
    }

    private AuthResponse issueTokens(User user, DeviceContext device) {
        if (!device.hasValidDeviceType()) {
            throw new BadRequestException("Tipe device harus ANDROID, IOS, atau WEB");
        }

        List<UserRole> userRoles = userRoleRepository.findAllByUserIdFetchingRole(user.getId());
        AppUserPrincipal principal = AppUserPrincipal.of(user, userRoles);

        String jti = jwtService.generateSessionId();
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .jti(jti)
                .issuedAt(now)
                .expiresAt(expiry)
                .build());
        String accessToken = jwtService.issueAccessToken(principal, jti,
                device.deviceId() == null || device.deviceId().isBlank() ? null : device.deviceId());

        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(jwtService.refreshTokenExpiry())
                .build());

        if (!device.isEmpty()) {
            recordDevice(user, device, accessToken);
        }

        return new AuthResponse(accessToken, rawRefreshToken, expiry.toEpochMilli(), UserResponse.from(user, userRoles));
    }

    /**
     * Upserts a `devices` row with the payload segment of the freshly issued
     * access token (header and signature are discarded) — one row per
     * physical device, not one per login. Keyed by {@code deviceId} when
     * the client sends one (the precise identifier); falls back to
     * {@code deviceType} otherwise (coarser — a second device of the same
     * platform without an id overwrites the first).
     */
    private void recordDevice(User user, DeviceContext ctx, String accessToken) {
        Optional<Device> existing = ctx.deviceId() != null
                ? deviceRepository.findByUserIdAndDeviceId(user.getId(), ctx.deviceId())
                : deviceRepository.findByUserIdAndDeviceType(user.getId(), ctx.deviceType());

        Device device = existing.orElseGet(() -> Device.builder()
                .user(user)
                .employee(user.getEmployee())
                .build());
        // Partial update: a header the client didn't send this time keeps
        // whatever was recorded before, instead of being wiped to null.
        if (ctx.deviceId() != null) {
            device.setDeviceId(ctx.deviceId());
        }
        if (ctx.deviceOs() != null) {
            device.setDeviceOs(ctx.deviceOs());
        }
        if (ctx.appVersion() != null) {
            device.setAppVersion(ctx.appVersion());
        }
        if (ctx.deviceType() != null) {
            device.setDeviceType(ctx.deviceType());
        }
        device.setToken(jwtService.extractPayload(accessToken));
        deviceRepository.save(device);
    }
}
