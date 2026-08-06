package com.portiq.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Revoked token ids, so logging out actually ends the session.
 *
 * <p>A signed JWT is otherwise valid until it expires - "logging out" by deleting it from the
 * browser leaves a working credential in anyone's hands who captured it. Keeping the revoked
 * {@code jti} until its own expiry closes that window without giving up stateless verification for
 * the other 99% of requests.
 *
 * <p>Entries expire exactly when the token would have, so the list can never grow beyond the
 * tokens issued in one token lifetime. Like the rate limiter, this is per instance; a multi-replica
 * deployment needs a shared store for logout to be global.
 */
@Component
public class TokenDenylist {

    private final Cache<String, Instant> revoked = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfter(new Expiry<String, Instant>() {
                @Override
                public long expireAfterCreate(String tokenId, Instant expiresAt, long currentTime) {
                    return nanosUntil(expiresAt);
                }

                @Override
                public long expireAfterUpdate(String tokenId, Instant expiresAt, long currentTime,
                                              long currentDuration) {
                    return nanosUntil(expiresAt);
                }

                @Override
                public long expireAfterRead(String tokenId, Instant expiresAt, long currentTime,
                                            long currentDuration) {
                    return currentDuration;
                }

                private long nanosUntil(Instant expiresAt) {
                    long millis = expiresAt.toEpochMilli() - System.currentTimeMillis();
                    return TimeUnit.MILLISECONDS.toNanos(Math.max(0, millis));
                }
            })
            .build();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || expiresAt == null) {
            return;
        }
        revoked.put(tokenId, expiresAt);
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && revoked.getIfPresent(tokenId) != null;
    }
}
