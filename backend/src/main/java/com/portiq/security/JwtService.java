package com.portiq.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String ISSUER = "portiq";

    @Value("${app.jwt.secret:}")
    private String configuredSecret;

    @Value("${app.jwt.expiry-hours:12}")
    private int expiryHours;

    /**
     * Rejects a token whose {@code iat} is further in the future than this. Without it, clock skew
     * between the issuing and verifying host makes freshly issued tokens fail verification; with it
     * set this small, a forged future-dated token still buys nothing.
     */
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes;
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
            }
        } else {
            // An ephemeral key keeps a dev run working without setup, at the cost of invalidating
            // every session on restart - and of being unusable across replicas, since each would
            // sign with a different key. StartupSecurityValidator refuses to let this reach prod.
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            log.warn("No app.jwt.secret configured - signing with a random key that will not survive a restart");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofHours(Math.max(1, expiryHours)).toMillis());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, expiry and issuer in one pass and hands back the claims.
     *
     * <p>Callers get an {@link Optional} rather than a boolean plus a second parse: parsing twice
     * invites the classic mistake of validating one token and then reading the subject out of an
     * unverified copy.
     */
    public Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String extractSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clockSkewSeconds(ALLOWED_CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isValid(String token) {
        return parse(token).isPresent();
    }
}
