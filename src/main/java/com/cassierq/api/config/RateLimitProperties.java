package com.cassierq.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param authPerMinute maksimal request per menit per IP ke tiap endpoint auth publik (login, register, ...). */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int authPerMinute) {
}
