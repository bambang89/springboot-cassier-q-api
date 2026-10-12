package com.cassierq.api.security;

import com.cassierq.api.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Batas request per menit per IP untuk endpoint auth publik — menahan brute
 * force password dan spam register/forgot-password. In-memory (fixed window
 * per menit): cukup karena hanya satu container (blue atau green) yang aktif
 * menerima traffic. IP klien diambil dari X-Forwarded-For nginx lewat
 * {@code server.forward-headers-strategy: native}.
 */
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password");

    private final RateLimitProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        long minute = Instant.now().getEpochSecond() / 60;
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.minute < minute);
        }

        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Window window = windows.compute(key, (k, w) -> w == null || w.minute != minute ? new Window(minute) : w);
        if (window.count.incrementAndGet() > properties.authPerMinute()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(60 - Instant.now().getEpochSecond() % 60));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"timestamp":"%s","status":429,"code":"TOO_MANY_REQUESTS","message":"Terlalu banyak percobaan, coba lagi sebentar lagi"}"""
                    .formatted(Instant.now()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
