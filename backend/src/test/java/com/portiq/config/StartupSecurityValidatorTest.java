package com.portiq.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecurityValidatorTest {

    private static final String GOOD_JWT_SECRET = "a-production-secret-of-at-least-32-bytes-long";
    /** 32 zero bytes, base64 - a well-formed AES-256 key as far as the format check is concerned. */
    private static final String GOOD_ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String GOOD_OWNER_PASSWORD = "a-real-owner-password";

    @Test
    void startsWhenEverythingIsConfigured() {
        assertThatCode(() -> validator("prod", GOOD_JWT_SECRET, GOOD_ENCRYPTION_KEY,
                GOOD_OWNER_PASSWORD, "https://portiq.example", false).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void refusesProductionWithNoJwtSecret() {
        assertThatThrownBy(() -> validator("prod", "", GOOD_ENCRYPTION_KEY,
                GOOD_OWNER_PASSWORD, "https://portiq.example", false).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesProductionWithTheSeededOwnerPassword() {
        assertThatThrownBy(() -> validator("prod", GOOD_JWT_SECRET, GOOD_ENCRYPTION_KEY,
                "ChangeMe123!", "https://portiq.example", false).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OWNER_PASSWORD");
    }

    @Test
    void refusesProductionWithNoEncryptionKey() {
        assertThatThrownBy(() -> validator("prod", GOOD_JWT_SECRET, "",
                GOOD_OWNER_PASSWORD, "https://portiq.example", false).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_ENCRYPTION_KEY");
    }

    @Test
    void refusesProductionWithAMalformedEncryptionKey() {
        assertThatThrownBy(() -> validator("prod", GOOD_JWT_SECRET, "not-base64!!",
                GOOD_OWNER_PASSWORD, "https://portiq.example", false).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_ENCRYPTION_KEY");
    }

    @Test
    void refusesProductionWithTheH2ConsoleEnabled() {
        assertThatThrownBy(() -> validator("prod", GOOD_JWT_SECRET, GOOD_ENCRYPTION_KEY,
                GOOD_OWNER_PASSWORD, "https://portiq.example", true).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("H2 console");
    }

    @Test
    void refusesProductionWithAPlainHttpWebauthnOrigin() {
        assertThatThrownBy(() -> validator("prod", GOOD_JWT_SECRET, GOOD_ENCRYPTION_KEY,
                GOOD_OWNER_PASSWORD, "http://portiq.example", false).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBAUTHN_ORIGIN");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        // One restart per missing variable would be a miserable way to deploy.
        assertThatThrownBy(() -> validator("prod", "", "", "ChangeMe123!",
                "https://portiq.example", false).validate())
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("DB_ENCRYPTION_KEY")
                .hasMessageContaining("OWNER_PASSWORD");
    }

    @Test
    void warnsButStartsOutsideProduction() {
        // A developer must be able to clone and run with no setup at all.
        assertThatCode(() -> validator(null, "", "", "ChangeMe123!",
                "http://localhost:5173", true).validate())
                .doesNotThrowAnyException();
    }

    private StartupSecurityValidator validator(String profile, String jwtSecret, String encryptionKey,
                                               String ownerPassword, String webauthnOrigin,
                                               boolean h2ConsoleEnabled) {
        MockEnvironment environment = new MockEnvironment();
        if (profile != null) {
            environment.setActiveProfiles(profile);
        }

        StartupSecurityValidator validator = new StartupSecurityValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(validator, "encryptionKey", encryptionKey);
        ReflectionTestUtils.setField(validator, "ownerPassword", ownerPassword);
        ReflectionTestUtils.setField(validator, "webauthnOrigin", webauthnOrigin);
        ReflectionTestUtils.setField(validator, "h2ConsoleEnabled", h2ConsoleEnabled);
        ReflectionTestUtils.setField(validator, "enforceOverride", "");
        return validator;
    }
}
