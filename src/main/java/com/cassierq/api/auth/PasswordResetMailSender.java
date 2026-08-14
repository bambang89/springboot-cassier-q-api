package com.cassierq.api.auth;

/**
 * Delivers a password reset token to the user. The default implementation
 * ({@link LoggingPasswordResetMailSender}) only logs it — there's no SMTP/
 * email provider wired into this project yet. Swap in a real implementation
 * (SES, SendGrid, Postmark, ...) before relying on this in production.
 */
public interface PasswordResetMailSender {

    void sendResetToken(String email, String rawToken);
}
