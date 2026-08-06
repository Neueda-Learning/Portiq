package com.portiq.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-comfortably-over-32-bytes";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "configuredSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiryHours", 12);
        jwtService.init();
    }

    @Test
    void issuesATokenThatVerifiesAndCarriesTheSubject() {
        String token = jwtService.generateToken("owner");

        assertThat(jwtService.parse(token)).isPresent();
        assertThat(jwtService.extractSubject(token)).isEqualTo("owner");
    }

    @Test
    void givesEveryTokenItsOwnId() {
        // Logout revokes by id, so two sessions sharing one id would log both out together.
        String first = jwtService.parse(jwtService.generateToken("owner")).orElseThrow().getId();
        String second = jwtService.parse(jwtService.generateToken("owner")).orElseThrow().getId();

        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        var otherKey = Keys.hmacShaKeyFor("a-completely-different-secret-of-sufficient-length"
                .getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .id("forged")
                .issuer("portiq")
                .subject("owner")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.parse(forged)).isEmpty();
    }

    @Test
    void rejectsATokenIssuedBySomethingElse() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String wrongIssuer = Jwts.builder()
                .id("x")
                .issuer("some-other-service")
                .subject("owner")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThat(jwtService.parse(wrongIssuer))
                .as("a token minted by another service that happens to share the secret is not ours")
                .isEmpty();
    }

    @Test
    void rejectsAnExpiredToken() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .id("x")
                .issuer("portiq")
                .subject("owner")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key)
                .compact();

        assertThat(jwtService.parse(expired)).isEmpty();
    }

    @Test
    void rejectsAnUnsignedToken() {
        // The "alg: none" family of attacks - a token with no signature at all must never verify.
        String unsigned = Jwts.builder().issuer("portiq").subject("owner").compact();

        assertThat(jwtService.parse(unsigned)).isEmpty();
    }

    @Test
    void rejectsGarbageWithoutThrowing() {
        assertThat(jwtService.parse("not.a.token")).isEmpty();
        assertThat(jwtService.parse("")).isEmpty();
        assertThat(jwtService.parse(null)).isEmpty();
    }

    @Test
    void refusesToStartWithATooShortSecret() {
        JwtService weak = new JwtService();
        ReflectionTestUtils.setField(weak, "configuredSecret", "too-short");

        assertThatThrownBy(weak::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
