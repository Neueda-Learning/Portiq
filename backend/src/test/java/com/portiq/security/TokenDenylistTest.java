package com.portiq.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenDenylistTest {

    private final TokenDenylist denylist = new TokenDenylist();

    @Test
    void aRevokedTokenIsRecognised() {
        denylist.revoke("token-1", Instant.now().plusSeconds(3600));

        assertThat(denylist.isRevoked("token-1")).isTrue();
    }

    @Test
    void anUnknownTokenIsNotRevoked() {
        assertThat(denylist.isRevoked("token-2")).isFalse();
        assertThat(denylist.isRevoked(null)).isFalse();
    }

    @Test
    void revokingOneTokenLeavesOthersWorking() {
        denylist.revoke("token-1", Instant.now().plusSeconds(3600));

        assertThat(denylist.isRevoked("token-3"))
                .as("logging out of one session must not end every other session")
                .isFalse();
    }

    @Test
    void anEntryIsDroppedOnceTheTokenWouldHaveExpiredAnyway() {
        // The list only has to outlive the token; keeping it longer would grow without bound.
        denylist.revoke("already-expired", Instant.now().minusSeconds(1));

        assertThat(denylist.isRevoked("already-expired")).isFalse();
    }

    @Test
    void ignoresAMissingTokenId() {
        denylist.revoke(null, Instant.now().plusSeconds(60));
        denylist.revoke("  ", Instant.now().plusSeconds(60));

        assertThat(denylist.isRevoked("  ")).isFalse();
    }
}
