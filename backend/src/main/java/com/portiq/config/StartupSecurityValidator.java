package com.portiq.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Refuses to start a production instance that is still running on development defaults.
 *
 * <p>Every setting checked here has a convenient fallback so a developer can clone and run with no
 * setup - and every one of those fallbacks is a serious hole in production. A blank JWT secret
 * means sessions are signed with a key that dies with the process; a blank encryption key means the
 * encrypted columns are unreadable after a restart *and* unreadable by a second replica; the seeded
 * owner password is published in this repository.
 *
 * <p>Catching that at startup is the point. A misconfigured deployment that boots looks healthy and
 * is discovered by whoever finds it first; one that refuses to boot is discovered by the person
 * deploying it, while they are still watching.
 *
 * <p>Enforcement follows the {@code prod} profile by default and can be forced on for any other
 * environment worth treating as real, via {@code app.security.enforce-secrets}.
 */
@Component
public class StartupSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    /** The password committed to application.properties - anyone reading the repo knows it. */
    private static final String SEEDED_OWNER_PASSWORD = "ChangeMe123!";

    private static final int MIN_JWT_SECRET_BYTES = 32;
    private static final int MIN_OWNER_PASSWORD_LENGTH = 12;

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.encryption.key:}")
    private String encryptionKey;

    @Value("${app.owner.password:}")
    private String ownerPassword;

    @Value("${app.webauthn.origin:}")
    private String webauthnOrigin;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${app.security.enforce-secrets:}")
    private String enforceOverride;

    public StartupSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        List<String> problems = collectProblems();
        if (problems.isEmpty()) {
            return;
        }

        if (enforcing()) {
            throw new IllegalStateException("Refusing to start with an insecure configuration:"
                    + problems.stream().map(p -> "\n  - " + p).reduce("", String::concat)
                    + "\nSet these via environment variables (see .env.prod.example), or set "
                    + "app.security.enforce-secrets=false if this really is a throwaway environment.");
        }

        // Outside production these are expected, so they are a warning rather than a failure -
        // but still said out loud, so nobody is surprised by the hard failure at deploy time.
        problems.forEach(problem -> log.warn("Development-only security setting in use: {}", problem));
    }

    private boolean enforcing() {
        if (enforceOverride != null && !enforceOverride.isBlank()) {
            return Boolean.parseBoolean(enforceOverride.trim());
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private List<String> collectProblems() {
        List<String> problems = new ArrayList<>();

        if (isBlank(jwtSecret)) {
            problems.add("JWT_SECRET is not set - session tokens are signed with a key that is "
                    + "regenerated on every restart and differs between replicas");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET is shorter than " + MIN_JWT_SECRET_BYTES + " bytes");
        }

        if (isBlank(encryptionKey)) {
            problems.add("DB_ENCRYPTION_KEY is not set - encrypted columns are written with an "
                    + "ephemeral key and become unreadable after a restart");
        } else if (!isValidAesKey(encryptionKey)) {
            problems.add("DB_ENCRYPTION_KEY is not a base64-encoded 16, 24 or 32 byte AES key");
        }

        if (SEEDED_OWNER_PASSWORD.equals(ownerPassword)) {
            problems.add("OWNER_PASSWORD is still the default value committed to this repository");
        } else if (!isBlank(ownerPassword) && ownerPassword.length() < MIN_OWNER_PASSWORD_LENGTH) {
            problems.add("OWNER_PASSWORD is shorter than " + MIN_OWNER_PASSWORD_LENGTH + " characters");
        }

        if (webauthnOrigin != null && webauthnOrigin.startsWith("http://")
                && !webauthnOrigin.contains("localhost") && !webauthnOrigin.contains("127.0.0.1")) {
            problems.add("WEBAUTHN_ORIGIN is a plain-http origin (" + webauthnOrigin
                    + ") - browsers only allow WebAuthn over https outside localhost");
        }

        if (h2ConsoleEnabled) {
            problems.add("the H2 console is enabled - it exposes a full SQL shell over http");
        }

        return problems;
    }

    private boolean isValidAesKey(String base64Key) {
        try {
            int length = Base64.getDecoder().decode(base64Key.trim()).length;
            return length == 16 || length == 24 || length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
