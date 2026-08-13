package com.cassierq.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Without this, Spring Security's default behavior for a request carrying no
 * (or an invalid/expired) bearer token is a bare 403 — indistinguishable
 * from "authenticated but not allowed". API clients need 401 to know
 * "log in again" vs 403's "you don't have permission".
 *
 * Writes the error body by hand instead of via the shared ErrorResponse/
 * ObjectMapper machinery: this runs inside Spring Security's filter chain,
 * before the DispatcherServlet/RestControllerAdvice machinery is involved,
 * so it keeps its own minimal, dependency-free serialization.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = """
                {"timestamp":"%s","status":401,"code":"UNAUTHENTICATED","message":"Token akses tidak ada atau tidak valid"}""".formatted(Instant.now());
        response.getWriter().write(body);
    }
}
