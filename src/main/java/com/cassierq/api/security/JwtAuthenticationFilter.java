package com.cassierq.api.security;

import com.cassierq.api.domain.repository.DeviceRepository;
import com.cassierq.api.domain.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            Optional<JwtService.AccessTokenClaims> claims = jwtService.parseAccessToken(token).filter(this::isSessionLive);

            if (claims.isPresent()) {
                JwtService.AccessTokenClaims c = claims.get();
                String deviceId = request.getHeader(DEVICE_ID_HEADER);
                boolean deviceIdSent = deviceId != null && !deviceId.isBlank();
                if (deviceIdSent && !isDeviceRecognized(c.userId(), deviceId)) {
                    writeDeviceError(response, deviceId);
                    return; // short-circuit — never reaches a controller
                }

                AppUserPrincipal principal = new AppUserPrincipal(
                        c.userId(), c.employeeId(), c.username(), c.email(), c.superadmin(), true, c.roleGrants(), c.jti());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSessionLive(JwtService.AccessTokenClaims claims) {
        return userSessionRepository.findByJti(claims.jti())
                .filter(session -> !session.isRevoked())
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    private boolean isDeviceRecognized(UUID userId, String deviceId) {
        return deviceRepository.findByUserIdAndDeviceId(userId, deviceId).isPresent();
    }

    private void writeDeviceError(HttpServletResponse response, String deviceId) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = """
                {"timestamp":"%s","status":401,"code":"DEVICE_MISMATCH","message":"Device ID tidak dikenali untuk akun ini"}"""
                .formatted(Instant.now());
        response.getWriter().write(body);
    }
}
