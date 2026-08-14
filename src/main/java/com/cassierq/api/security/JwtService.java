package com.cassierq.api.security;

import com.cassierq.api.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(principal.getUserId().toString())
                .claim("username", principal.getUsername())
                .claim("email", principal.getEmail())
                .claim("superadmin", principal.isSuperadmin())
                .claim("roles", encodeRoleGrants(principal.getRoleGrants()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("email", String.class),
                    Boolean.TRUE.equals(claims.get("superadmin", Boolean.class)),
                    decodeRoleGrants(claims.get("roles", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // "ROLE_CODE:storeUuid,ROLE_CODE2:" (empty storeUuid = not store-scoped) —
    // plain delimited string rather than a nested JSON claim, so it round-trips
    // the same way regardless of which JSON provider jjwt is configured with.
    private String encodeRoleGrants(List<RoleGrant> grants) {
        return grants.stream()
                .map(g -> g.roleCode() + ":" + (g.storeId() == null ? "" : g.storeId()))
                .collect(Collectors.joining(","));
    }

    private List<RoleGrant> decodeRoleGrants(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        return Arrays.stream(encoded.split(","))
                .filter(s -> !s.isBlank())
                .map(s -> {
                    String[] parts = s.split(":", 2);
                    UUID storeId = (parts.length > 1 && !parts[1].isEmpty()) ? UUID.fromString(parts[1]) : null;
                    return new RoleGrant(parts[0], storeId);
                })
                .toList();
    }

    /** Opaque, high-entropy refresh token — never a JWT, so it can't be inspected/forged client-side. */
    public String generateRefreshTokenValue() {
        return generateOpaqueToken();
    }

    /**
     * High-entropy random token, base64url-encoded. Shared by refresh tokens
     * and password reset tokens — both are bearer secrets handed to the
     * client and only ever stored server-side as a hash (see {@link #hashToken}).
     */
    public String generateOpaqueToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS);
    }

    /** Only this hash is ever persisted — a leaked DB row can't be replayed as a live refresh token. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record AccessTokenClaims(UUID userId, String username, String email, boolean superadmin, List<RoleGrant> roleGrants) {
    }
}
