package com.cassierq.api.auth;

import com.cassierq.api.auth.dto.AuthResponse;
import com.cassierq.api.auth.dto.ChangePasswordRequest;
import com.cassierq.api.auth.dto.ForgotPasswordRequest;
import com.cassierq.api.auth.dto.LoginRequest;
import com.cassierq.api.auth.dto.RefreshRequest;
import com.cassierq.api.auth.dto.RegisterRequest;
import com.cassierq.api.auth.dto.ResetPasswordRequest;
import com.cassierq.api.auth.dto.UserResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registrasi toko, login, dan pengelolaan token")
public class AuthController {

    private static final String HEADER_DEVICE_ID = "X-Device-Id";
    private static final String HEADER_DEVICE_OS = "X-Device-OS";
    private static final String HEADER_APP_VERSION = "X-App-Version";
    private static final String HEADER_DEVICE_TYPE = "X-Device-Type";

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Daftarkan toko baru beserta akun pemilik (owner)")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = HEADER_DEVICE_ID, required = false) String deviceId,
            @RequestHeader(value = HEADER_DEVICE_OS, required = false) String deviceOs,
            @RequestHeader(value = HEADER_APP_VERSION, required = false) String appVersion,
            @RequestHeader(value = HEADER_DEVICE_TYPE, required = false) String deviceType) {
        var device = new DeviceContext(deviceId, deviceOs, appVersion, deviceType);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, device));
    }

    @PostMapping("/login")
    @Operation(summary = "Masuk dengan username & kata sandi")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HEADER_DEVICE_ID, required = false) String deviceId,
            @RequestHeader(value = HEADER_DEVICE_OS, required = false) String deviceOs,
            @RequestHeader(value = HEADER_APP_VERSION, required = false) String appVersion,
            @RequestHeader(value = HEADER_DEVICE_TYPE, required = false) String deviceType) {
        var device = new DeviceContext(deviceId, deviceOs, appVersion, deviceType);
        return authService.login(request, device);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Tukar refresh token dengan access token baru (refresh token lama otomatis dicabut)")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader(value = HEADER_DEVICE_ID, required = false) String deviceId,
            @RequestHeader(value = HEADER_DEVICE_OS, required = false) String deviceOs,
            @RequestHeader(value = HEADER_APP_VERSION, required = false) String appVersion,
            @RequestHeader(value = HEADER_DEVICE_TYPE, required = false) String deviceType) {
        var device = new DeviceContext(deviceId, deviceOs, appVersion, deviceType);
        return authService.refresh(request, device);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cabut refresh token (logout)")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout dari semua device: cabut semua sesi access token & refresh token milik akun ini, berlaku seketika")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AppUserPrincipal principal) {
        authService.logoutAllDevices(principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/revoke/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'KEPALA_TOKO')")
    @Operation(summary = "Paksa logout user lain (SUPERADMIN: siapa saja; KEPALA_TOKO: hanya staf di toko sendiri)")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID userId) {
        authService.revokeUserSessions(principal, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Profil user yang sedang login")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.me(principal.getUserId());
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ganti kata sandi (mengetahui kata sandi saat ini)")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Minta token reset kata sandi (dikirim ke email jika terdaftar)")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Tukar token reset dengan kata sandi baru")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
