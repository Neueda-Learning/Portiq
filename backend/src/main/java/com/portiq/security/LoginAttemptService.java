package com.portiq.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Temporary lockout after repeated failed logins.
 *
 * <p>This sits alongside the blanket per-IP throttle in {@link RateLimitFilter} because the two
 * catch different attacks. The throttle caps request volume from one address; this counts
 * *failures* against one account, so a slow distributed guessing run - well under any volume
 * limit, one attempt per address - still trips it.
 *
 * <p>Only failures are counted, and a success clears the account's tally, so someone who mistypes
 * their password twice and then gets it right is never left locked out.
 *
 * <p>The lockout is time-boxed rather than permanent on purpose: a permanent lock turns a
 * guessing attempt against a known username into a denial of service against its owner.
 */
@Component
public class LoginAttemptService {

    private static final String ACCOUNT_BUCKET = "login-fail-account";
    private static final String ADDRESS_BUCKET = "login-fail-address";

    private final RateLimiter rateLimiter;

    /** Failures against one username before that account is temporarily locked. */
    @Value("${app.security.login.max-failures-per-account:5}")
    private int maxFailuresPerAccount;

    /**
     * Failures from one address across any username. Higher than the per-account limit so a shared
     * office NAT is not locked out by one careless colleague, low enough to stop enumeration.
     */
    @Value("${app.security.login.max-failures-per-address:20}")
    private int maxFailuresPerAddress;

    @Value("${app.security.login.lockout-minutes:15}")
    private int lockoutMinutes;

    public LoginAttemptService(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** Seconds the caller must wait, or 0 if the attempt may proceed. */
    public long blockedForSeconds(String username, String clientIp) {
        Duration window = window();
        boolean blocked = rateLimiter.usage(ACCOUNT_BUCKET, key(username), window) >= maxFailuresPerAccount
                || rateLimiter.usage(ADDRESS_BUCKET, clientIp, window) >= maxFailuresPerAddress;
        return blocked ? secondsLeftInWindow(window) : 0;
    }

    public void recordFailure(String username, String clientIp) {
        Duration window = window();
        rateLimiter.record(ACCOUNT_BUCKET, key(username), maxFailuresPerAccount, window);
        rateLimiter.record(ADDRESS_BUCKET, clientIp, maxFailuresPerAddress, window);
    }

    public void recordSuccess(String username, String clientIp) {
        Duration window = window();
        rateLimiter.clear(ACCOUNT_BUCKET, key(username), window);
        rateLimiter.clear(ADDRESS_BUCKET, clientIp, window);
    }

    private Duration window() {
        return Duration.ofMinutes(Math.max(1, lockoutMinutes));
    }

    private long secondsLeftInWindow(Duration window) {
        long windowMillis = window.toMillis();
        long now = System.currentTimeMillis();
        long endOfWindow = (now / windowMillis + 1) * windowMillis;
        return Math.max(1, (endOfWindow - now + 999) / 1000);
    }

    /** Usernames are matched case-insensitively for counting so "Owner" and "owner" share a tally. */
    private String key(String username) {
        return username == null ? "-" : username.trim().toLowerCase();
    }
}
