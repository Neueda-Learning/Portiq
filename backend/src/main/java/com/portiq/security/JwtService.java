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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
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

    /** Where the generated development key is kept so sessions survive a restart. Gitignored. */
    @Value("${app.jwt.dev-secret-file:.dev-secrets/jwt.key}")
    private String devSecretPath;

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
            bytes = developmentSecret();
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * The unconfigured-secret path, for local development only.
     *
     * <p>This used to generate a fresh random key on every boot, which meant every restart silently
     * logged the developer out - and, worse, behaved the same way in any environment where the
     * variable had simply been forgotten, where two replicas would each sign with a different key
     * and reject each other's tokens. The failure looked like a flaky login rather than a
     * configuration mistake, which is the most expensive kind of bug to chase.
     *
     * <p>So the generated key is now written once to a gitignored file and reused. Sessions survive
     * a restart, the behaviour is the same on the second run as the first, and nothing about it is
     * silent: it warns at every startup, and StartupSecurityValidator refuses to let a production
     * profile reach this path at all.
     *
     * <p>If the file cannot be read or written - a read-only container, say - it falls back to a
     * purely in-memory key rather than refusing to start, since that is still the correct outcome
     * for a throwaway environment.
     */
    private byte[] developmentSecret() {
        Path keyFile = Path.of(devSecretPath);
        try {
            if (Files.isReadable(keyFile)) {
                byte[] stored = Base64.getDecoder().decode(Files.readString(keyFile).trim());
                if (stored.length >= 32) {
                    log.warn("No app.jwt.secret configured - reusing the development key in {}. "
                            + "Set JWT_SECRET before deploying anywhere real.", keyFile.toAbsolutePath());
                    return stored;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not read the development JWT key at {} ({}); generating a new one",
                    keyFile.toAbsolutePath(), e.getMessage());
        }

        byte[] generated = new byte[48];
        new SecureRandom().nextBytes(generated);
        try {
            Path parent = keyFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(generated));
            log.warn("No app.jwt.secret configured - generated a development key and stored it in {}. "
                    + "Set JWT_SECRET before deploying anywhere real.", keyFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("No app.jwt.secret configured and the development key could not be persisted ({}). "
                    + "Signing with an in-memory key: every restart will end all sessions.", e.getMessage());
        }
        return generated;
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
