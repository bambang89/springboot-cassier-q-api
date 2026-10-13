package com.cassierq.api.cashier;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes cashier sessions that are still open past the daily auto-close time
 * (app.business.cashier-session-auto-close, default 23:00 store time).
 * Runs every minute so a missed run (restart, deploy) is caught up quickly;
 * the close itself is idempotent, so overlapping blue/green instances are safe.
 */
@Component
@RequiredArgsConstructor
public class CashierSessionAutoCloseJob {

    private final CashierSessionService cashierSessionService;

    /** Closes every session opened before the most recent auto-close time. */
    @Scheduled(cron = "0 * * * * *")
    public void closeExpiredSessions() {
        cashierSessionService.autoCloseExpiredSessions();
    }
}
