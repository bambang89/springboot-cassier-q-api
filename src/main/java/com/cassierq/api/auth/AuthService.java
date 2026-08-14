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
import com.cassierq.api.domain.entity.Employee;
import com.cassierq.api.domain.entity.PasswordResetToken;
import com.cassierq.api.domain.entity.RefreshToken;
import com.cassierq.api.domain.entity.Role;
import com.cassierq.api.domain.entity.Store;
import com.cassierq.api.domain.entity.User;
import com.cassierq.api.domain.entity.UserRole;
import com.cassierq.api.domain.repository.EmployeeRepository;
import com.cassierq.api.domain.repository.PasswordResetTokenRepository;
import com.cassierq.api.domain.repository.RefreshTokenRepository;
import com.cassierq.api.domain.repository.RoleRepository;
import com.cassierq.api.domain.repository.StoreRepository;
import com.cassierq.api.domain.repository.UserRepository;
import com.cassierq.api.domain.repository.UserRoleRepository;
import com.cassierq.api.security.AppUserPrincipal;
import com.cassierq.api.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    // The store-head role newly registered accounts are granted — the closest
    // equivalent, in this RBAC schema, of the old single "OWNER" role.
    private static final String DEFAULT_REGISTER_ROLE_CODE = "KEPALA_TOKO";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordResetProperties passwordResetProperties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }
        if (employeeRepository.existsByEmployeeCodeIgnoreCase(request.username())) {
            throw new ConflictException("Username sudah terdaftar");
        }

        Store store = storeRepository.save(Store.builder()
                .storeCode(request.storeCode())
                .storeName(request.storeName())
                .status("ACTIVE")
                .build());

        // Every login account maps 1:1 to an employee record in this schema;
        // the username doubles as the employee code since we don't collect one separately here.
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

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Refresh token tidak valid atau sudah kedaluwarsa"));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashToken(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
        List<UserRole> roles = userRoleRepository.findAllByUserIdFetchingRole(userId);
        return UserResponse.from(user, roles);
    }

    /** Authenticated user changes their own password, knowing the current one. */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Kata sandi saat ini salah");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // A password change should invalidate sessions issued under the old
        // credential, same reasoning as revoking a refresh token chain.
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    /**
     * Starts the "forgot password" flow: issues a one-time reset token and
     * hands it to {@link PasswordResetMailSender}. Always completes the same
     * way whether or not the username is registered, so the endpoint can't be
     * used to enumerate accounts.
     */
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

        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private AuthResponse issueTokens(User user) {
        List<UserRole> userRoles = userRoleRepository.findAllByUserIdFetchingRole(user.getId());
        AppUserPrincipal principal = AppUserPrincipal.of(user, userRoles);
        String accessToken = jwtService.issueAccessToken(principal);

        String rawRefreshToken = jwtService.generateRefreshTokenValue();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .expiresAt(jwtService.refreshTokenExpiry())
                .build());

        long expiresInSeconds = jwtProperties.accessTokenTtlMinutes() * 60;
        return new AuthResponse(accessToken, rawRefreshToken, expiresInSeconds, UserResponse.from(user, userRoles));
    }
}
