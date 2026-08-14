package com.cassierq.api.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dev-only stand-in: logs the raw reset token instead of emailing it, so the
 * forgot-password flow is testable end-to-end (e.g. via Swagger UI + the log
 * output) without a mail provider configured. Replace with a real
 * {@link PasswordResetMailSender} bean before deploying anywhere real —
 * logging a live reset token is not safe outside local dev.
 */
@Slf4j
@Component
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    @Override
    public void sendResetToken(String email, String rawToken) {
        log.info("Password reset requested for {} — token: {}", email, rawToken);
    }
}
