package com.portiq.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request counter, held in memory.
 *
 * <p>The window index is folded into the cache key, so a counter is only ever created and
 * incremented, never reset - which is what makes this safe under concurrency without locking.
 * Entries for elapsed windows are simply evicted by Caffeine.
 *
 * <p>Two properties are worth knowing before relying on the numbers. Fixed windows allow a burst of
 * up to twice the limit across a boundary - the last moment of one window plus the first of the
 * next - so a limit of 20 really means "no more than 40 in any five-minute stretch". And the
 * counters are in memory, so limits are per instance: two replicas behind a load balancer each
 * allow the configured rate.
 *
 * <p>Both are accepted trade-offs for not requiring Redis, and neither undermines what these limits
 * are for, which is making bulk guessing and scraping expensive rather than metering precisely. If
 * this is ever run as more than one instance, either divide the limits or move the counters to a
 * shared store.
 */
@Component
public class RateLimiter {

    /** Comfortably longer than the longest window in use, so no live counter is evicted early. */
    private static final Duration ENTRY_TTL = Duration.ofHours(1);

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(ENTRY_TTL)
            .maximumSize(100_000)
            .build();

    /**
     * Records one hit against {@code bucket:key} and reports whether it is within {@code limit}
     * for the current window.
     */
    public Decision record(String bucket, String key, int limit, Duration window) {
        long windowMillis = Math.max(1L, window.toMillis());
        long now = System.currentTimeMillis();
        long windowIndex = now / windowMillis;

        String composite = bucket + '|' + key + '|' + windowIndex;
        int used = counters.get(composite, unused -> new AtomicInteger()).incrementAndGet();

        long millisLeft = (windowIndex + 1) * windowMillis - now;
        int retryAfter = (int) Math.max(1, (millisLeft + 999) / 1000);
        return new Decision(used <= limit, Math.max(0, limit - used), retryAfter);
    }

    /** Current usage for a key without recording a hit - used to report state, not to gate. */
    public int usage(String bucket, String key, Duration window) {
        long windowMillis = Math.max(1L, window.toMillis());
        long windowIndex = System.currentTimeMillis() / windowMillis;
        AtomicInteger counter = counters.getIfPresent(bucket + '|' + key + '|' + windowIndex);
        return counter == null ? 0 : counter.get();
    }

    /** Drops a key's counters. Called after a successful login so one typo is not punished. */
    public void clear(String bucket, String key, Duration window) {
        long windowMillis = Math.max(1L, window.toMillis());
        long windowIndex = System.currentTimeMillis() / windowMillis;
        counters.invalidate(bucket + '|' + key + '|' + windowIndex);
    }

    void reset() {
        counters.invalidateAll();
    }

    /**
     * @param allowed           whether this hit is within the limit
     * @param remaining         hits left in the current window
     * @param retryAfterSeconds seconds until the window rolls over
     */
    public record Decision(boolean allowed, int remaining, int retryAfterSeconds) {}
}
