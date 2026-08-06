package com.portiq.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.5";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(new RateLimiter());
        ReflectionTestUtils.setField(service, "maxFailuresPerAccount", 3);
        ReflectionTestUtils.setField(service, "maxFailuresPerAddress", 10);
        ReflectionTestUtils.setField(service, "lockoutMinutes", 15);
    }

    @Test
    void allowsAttemptsUpToTheThreshold() {
        service.recordFailure("owner", IP);
        service.recordFailure("owner", IP);

        assertThat(service.blockedForSeconds("owner", IP))
                .as("two failures is a person mistyping, not an attack")
                .isZero();
    }

    @Test
    void locksTheAccountOnceTheThresholdIsReached() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("owner", IP);
        }

        assertThat(service.blockedForSeconds("owner", IP)).isPositive();
    }

    @Test
    void successClearsEarlierFailures() {
        service.recordFailure("owner", IP);
        service.recordFailure("owner", IP);

        service.recordSuccess("owner", IP);

        service.recordFailure("owner", IP);
        assertThat(service.blockedForSeconds("owner", IP))
                .as("a correct password resets the tally, so old typos cannot accumulate into a lockout")
                .isZero();
    }

    @Test
    void locksTheAddressEvenWhenTheUsernameKeepsChanging() {
        ReflectionTestUtils.setField(service, "maxFailuresPerAddress", 4);

        for (int i = 0; i < 4; i++) {
            service.recordFailure("guess" + i, IP);
        }

        assertThat(service.blockedForSeconds("someoneelse", IP))
                .as("username enumeration from one address must trip the address limit")
                .isPositive();
    }

    @Test
    void oneAddressBeingLockedDoesNotLockAnother() {
        for (int i = 0; i < 3; i++) {
            service.recordFailure("owner", IP);
        }

        assertThat(service.blockedForSeconds("someoneelse", "198.51.100.9")).isZero();
    }

    @Test
    void usernameMatchingIsCaseInsensitive() {
        service.recordFailure("Owner", IP);
        service.recordFailure("OWNER", IP);
        service.recordFailure("owner", IP);

        assertThat(service.blockedForSeconds("oWnEr", IP))
                .as("varying the case must not hand an attacker a fresh budget")
                .isPositive();
    }
}
