package com.portiq.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        Duration window = Duration.ofMinutes(1);

        for (int i = 1; i <= 3; i++) {
            assertThat(rateLimiter.record("test", "1.2.3.4", 3, window).allowed())
                    .as("hit %d of 3 should be allowed", i)
                    .isTrue();
        }

        assertThat(rateLimiter.record("test", "1.2.3.4", 3, window).allowed()).isFalse();
    }

    @Test
    void reportsHowManyHitsRemain() {
        Duration window = Duration.ofMinutes(1);

        assertThat(rateLimiter.record("test", "1.2.3.4", 3, window).remaining()).isEqualTo(2);
        assertThat(rateLimiter.record("test", "1.2.3.4", 3, window).remaining()).isEqualTo(1);
        assertThat(rateLimiter.record("test", "1.2.3.4", 3, window).remaining()).isZero();
    }

    @Test
    void countsEachKeySeparately() {
        Duration window = Duration.ofMinutes(1);
        rateLimiter.record("test", "1.2.3.4", 1, window);

        assertThat(rateLimiter.record("test", "1.2.3.4", 1, window).allowed()).isFalse();
        assertThat(rateLimiter.record("test", "5.6.7.8", 1, window).allowed())
                .as("one caller exhausting their budget must not lock out everyone else")
                .isTrue();
    }

    @Test
    void countsEachBucketSeparately() {
        Duration window = Duration.ofMinutes(1);
        rateLimiter.record("auth", "1.2.3.4", 1, window);

        assertThat(rateLimiter.record("auth", "1.2.3.4", 1, window).allowed()).isFalse();
        assertThat(rateLimiter.record("general", "1.2.3.4", 1, window).allowed()).isTrue();
    }

    @Test
    void clearForgivesEarlierHits() {
        Duration window = Duration.ofMinutes(1);
        rateLimiter.record("test", "1.2.3.4", 2, window);
        rateLimiter.record("test", "1.2.3.4", 2, window);

        rateLimiter.clear("test", "1.2.3.4", window);

        assertThat(rateLimiter.usage("test", "1.2.3.4", window)).isZero();
        assertThat(rateLimiter.record("test", "1.2.3.4", 2, window).allowed()).isTrue();
    }

    @Test
    void retryAfterNeverAdvisesZeroSeconds() {
        // A Retry-After of 0 invites an immediate retry, which is exactly what the limit is for.
        RateLimiter.Decision decision = rateLimiter.record("test", "1.2.3.4", 1, Duration.ofSeconds(1));
        assertThat(decision.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void usageDoesNotItselfCountAsAHit() {
        Duration window = Duration.ofMinutes(1);
        rateLimiter.usage("test", "1.2.3.4", window);
        rateLimiter.usage("test", "1.2.3.4", window);

        assertThat(rateLimiter.record("test", "1.2.3.4", 1, window).allowed()).isTrue();
    }
}
