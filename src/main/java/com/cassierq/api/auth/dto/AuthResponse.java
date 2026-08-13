package com.cassierq.api.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
}
